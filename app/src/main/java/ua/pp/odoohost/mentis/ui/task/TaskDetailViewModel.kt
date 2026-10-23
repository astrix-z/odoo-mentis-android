package ua.pp.odoohost.mentis.ui.task

import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.pp.odoohost.mentis.data.auth.AuthRepository
import ua.pp.odoohost.mentis.data.files.AttachmentFiles
import ua.pp.odoohost.mentis.data.odoo.Ref
import ua.pp.odoohost.mentis.data.task.Attachment
import ua.pp.odoohost.mentis.data.task.ChatMessage
import ua.pp.odoohost.mentis.data.task.ChatterRepository
import ua.pp.odoohost.mentis.data.task.KanbanState
import ua.pp.odoohost.mentis.data.task.Task
import ua.pp.odoohost.mentis.data.task.TaskRepository
import ua.pp.odoohost.mentis.data.task.TaskValues
import ua.pp.odoohost.mentis.ui.common.OdooViewModel
import java.util.UUID

/** A file in the composer: uploading, uploaded ([attachment] set), or failed. */
data class PendingFile(
    val key: String,
    val name: String,
    val attachment: Attachment? = null,
    val error: Throwable? = null,
)

data class Composer(
    val text: String = "",
    /** Log note (followers not notified) vs. send message. Notes are the safer default. */
    val isNote: Boolean = true,
    val files: List<PendingFile> = emptyList(),
    val sending: Boolean = false,
) {
    val canSend get() = !sending && files.none { it.attachment == null && it.error == null } &&
        (text.isNotBlank() || files.any { it.attachment != null })
}

data class TaskDetailState(
    val task: Task? = null,
    val stages: List<Ref> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val messagesLoading: Boolean = false,
    val messagesEndReached: Boolean = false,
    val composer: Composer = Composer(),
    val loading: Boolean = true,
    /** Blocks the screen: the task could not be loaded at all. */
    val loadError: Throwable? = null,
    /** Shown as a snackbar: an action failed but the task is still on screen. */
    val actionError: Throwable? = null,
    val openingAttachmentId: Int? = null,
)

class TaskDetailViewModel(
    private val taskId: Int,
    private val tasks: TaskRepository,
    private val chatter: ChatterRepository,
    private val files: AttachmentFiles,
    auth: AuthRepository,
) : OdooViewModel(auth) {

    private val _state = MutableStateFlow(TaskDetailState())
    val state: StateFlow<TaskDetailState> = _state.asStateFlow()

    private val _openFile = Channel<Pair<Uri, String>>(Channel.BUFFERED)

    /** Downloaded attachments (content Uri, mimetype) for the screen to hand to a viewer app. */
    val openFile: Flow<Pair<Uri, String>> = _openFile.receiveAsFlow()

    init {
        load()
        viewModelScope.launch { tasks.changes.filter { it == taskId }.collect { load() } }
    }

    fun retry() {
        _state.update { it.copy(loading = true, loadError = null) }
        load()
    }

    fun dismissError() = _state.update { it.copy(actionError = null) }

    fun setStage(stageId: Int) = update(TaskValues(stageId = stageId))
    fun togglePriority() = _state.value.task?.let { update(TaskValues(highPriority = !it.highPriority)) }
    fun setKanbanState(state: KanbanState) = update(TaskValues(kanbanState = state))

    fun loadOlderMessages() {
        val s = _state.value
        if (s.messagesLoading || s.messagesEndReached) return
        _state.update { it.copy(messagesLoading = true) }
        launchSafe(onError = ::actionFailed, onFinally = { _state.update { it.copy(messagesLoading = false) } }) {
            val older = chatter.messages(taskId, beforeId = s.messages.lastOrNull()?.id)
            _state.update { it.copy(messages = it.messages + older, messagesEndReached = older.size < MESSAGE_PAGE) }
        }
    }

    fun onComposerText(text: String) = updateComposer { it.copy(text = text) }
    fun onComposerNote(isNote: Boolean) = updateComposer { it.copy(isNote = isNote) }

    /** Where the camera app should save the next photo. */
    fun newPhotoUri(): Uri = files.newPhotoUri()

    fun addFiles(uris: List<Uri>) = uris.forEach(::addFile)

    fun removeFile(key: String) {
        val file = _state.value.composer.files.firstOrNull { it.key == key } ?: return
        updateComposer { c -> c.copy(files = c.files.filterNot { it.key == key }) }
        file.attachment?.let { launchSafe(onError = {}) { chatter.discard(it.id) } }
    }

    fun send() {
        val composer = _state.value.composer
        if (!composer.canSend) return
        updateComposer { it.copy(sending = true) }
        launchSafe(onError = ::actionFailed, onFinally = { updateComposer { it.copy(sending = false) } }) {
            chatter.post(taskId, composer.text, composer.isNote, composer.files.mapNotNull { it.attachment?.id })
            updateComposer { Composer(isNote = it.isNote) }
        }
    }

    fun openAttachment(attachment: Attachment) {
        if (_state.value.openingAttachmentId != null) return
        _state.update { it.copy(openingAttachmentId = attachment.id) }
        launchSafe(onError = ::actionFailed, onFinally = { _state.update { it.copy(openingAttachmentId = null) } }) {
            val uri = files.download(chatter.downloadUrl(attachment), attachment.id, attachment.name)
            _openFile.send(uri to attachment.mimetype)
        }
    }

    private fun addFile(uri: Uri) {
        val key = UUID.randomUUID().toString()
        updateComposer { it.copy(files = it.files + PendingFile(key, uri.lastPathSegment.orEmpty())) }
        launchSafe(onError = { e -> updateFile(key) { it.copy(error = e) } }) {
            val local = files.read(uri)
            updateFile(key) { it.copy(name = local.name) }
            val attachment = chatter.upload(local.name, local.mimetype, local.bytes)
            updateFile(key) { it.copy(attachment = attachment) }
        }
    }

    private fun update(values: TaskValues) {
        launchSafe(onError = ::actionFailed) { tasks.update(taskId, values) }
    }

    private fun load() {
        launchSafe(
            onError = { e ->
                if (_state.value.task == null) _state.update { it.copy(loadError = e) } else actionFailed(e)
            },
            onFinally = { _state.update { it.copy(loading = false) } },
        ) {
            val task = tasks.task(taskId)
            val stages = task.project?.let { tasks.projectStages(it.id) }.orEmpty()
            val messages = chatter.messages(taskId)
            _state.update {
                it.copy(
                    task = task,
                    stages = stages,
                    messages = messages,
                    messagesEndReached = messages.size < MESSAGE_PAGE,
                    loadError = null,
                )
            }
        }
    }

    private fun actionFailed(e: Throwable) = _state.update { it.copy(actionError = e) }

    private fun updateComposer(change: (Composer) -> Composer) = _state.update { it.copy(composer = change(it.composer)) }

    private fun updateFile(key: String, change: (PendingFile) -> PendingFile) =
        updateComposer { c -> c.copy(files = c.files.map { if (it.key == key) change(it) else it }) }

    private companion object {
        const val MESSAGE_PAGE = 30
    }
}
