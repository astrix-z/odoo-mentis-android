package ua.pp.odoohost.mentis.data.task

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import ua.pp.odoohost.mentis.data.odoo.Html
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.Ref
import ua.pp.odoohost.mentis.data.odoo.booleanOrFalse
import ua.pp.odoohost.mentis.data.odoo.intOrNull
import ua.pp.odoohost.mentis.data.odoo.ints
import ua.pp.odoohost.mentis.data.odoo.stringOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.encoding.Base64

data class Attachment(val id: Int, val name: String, val mimetype: String) {
    val isImage get() = mimetype.startsWith("image/")
}

/** A field change recorded on a message, e.g. Stage: New → In Progress. */
data class Tracking(val field: String, val oldValue: String, val newValue: String)

data class ChatMessage(
    val id: Int,
    val author: Ref?,
    val date: Instant,
    /** Sanitized HTML from Odoo; empty for pure tracking messages. */
    val body: String,
    val isNote: Boolean,
    /** Odoo's description of an automatic message, e.g. "Task blocked". */
    val subtypeDescription: String?,
    val trackings: List<Tracking>,
    val attachments: List<Attachment>,
)

/** The chatter of a task: messages, notes, field-change history and attachments. */
class ChatterRepository(private val client: OdooClient, private val tasks: TaskRepository) {

    /** Newest first; pass the oldest id already shown as [beforeId] to page back. */
    suspend fun messages(taskId: Int, beforeId: Int? = null, limit: Int = PAGE_SIZE): List<ChatMessage> {
        val rows = client.rpc(
            "/mail/thread/messages",
            buildJsonObject {
                put("thread_model", MODEL)
                put("thread_id", taskId)
                beforeId?.let { put("max_id", it) }
                put("limit", limit)
            },
        ) as JsonArray
        return rows.map { parseMessage(it.jsonObject) }
    }

    /**
     * Posts [text] to the task: a message notifies its followers, a note ([isNote]) does not.
     * [attachmentIds] come from [upload].
     */
    suspend fun post(taskId: Int, text: String, isNote: Boolean, attachmentIds: List<Int>): ChatMessage {
        val result = client.rpc(
            "/mail/message/post",
            buildJsonObject {
                put("thread_model", MODEL)
                put("thread_id", taskId)
                put(
                    "post_data",
                    buildJsonObject {
                        put("body", Html.escape(text.trim()).replace("\n", "<br>"))
                        put("message_type", "comment")
                        put("subtype_xmlid", if (isNote) "mail.mt_note" else "mail.mt_comment")
                        put("attachment_ids", ints(attachmentIds))
                    },
                )
            },
        ).jsonObject
        tasks.notifyChanged(taskId)
        return parseMessage(result)
    }

    /**
     * Uploads a file for the next message. Like the web client's composer, it stays pending
     * (attached to no record) until [post] links it to the message and the task.
     */
    suspend fun upload(name: String, mimetype: String, bytes: ByteArray): Attachment {
        val id = client.callKw(
            "ir.attachment",
            "create",
            args = JsonArray(
                listOf(
                    buildJsonObject {
                        put("name", name)
                        put("mimetype", mimetype)
                        put("datas", Base64.encode(bytes))
                        put("res_model", "mail.compose.message")
                        put("res_id", 0)
                    },
                ),
            ),
        ).intOrNull()!!
        return Attachment(id, name, mimetype)
    }

    /** Removes an uploaded file the user dropped from the composer before sending. */
    suspend fun discard(attachmentId: Int) {
        client.rpc("/mail/attachment/delete", buildJsonObject { put("attachment_id", attachmentId) })
    }

    fun downloadUrl(attachment: Attachment): String =
        client.url("/web/content/${attachment.id}?download=true").toString()

    private fun parseMessage(row: JsonObject): ChatMessage {
        val author = row["author"] as? JsonObject
        return ChatMessage(
            id = row.getValue("id").intOrNull()!!,
            author = author?.get("id")?.intOrNull()?.let { Ref(it, author["name"]?.stringOrNull().orEmpty()) },
            date = parseUtc(row["date"]?.stringOrNull()),
            body = row["body"]?.stringOrNull().orEmpty(),
            isNote = row["is_note"]?.booleanOrFalse() ?: false,
            subtypeDescription = row["subtype_description"]?.stringOrNull(),
            trackings = (row["trackingValues"] as? JsonArray).orEmpty().map {
                val t = it.jsonObject
                Tracking(
                    field = t["changedField"]?.stringOrNull().orEmpty(),
                    oldValue = trackingValue(t["oldValue"]),
                    newValue = trackingValue(t["newValue"]),
                )
            },
            attachments = (row["attachment_ids"] as? JsonArray).orEmpty().map {
                val a = it.jsonObject
                Attachment(
                    id = a.getValue("id").intOrNull()!!,
                    name = a["name"]?.stringOrNull().orEmpty(),
                    mimetype = a["mimetype"]?.stringOrNull().orEmpty(),
                )
            },
        )
    }

    private fun trackingValue(value: kotlinx.serialization.json.JsonElement?): String {
        val v = (value as? JsonObject)?.get("value") ?: return ""
        return v.stringOrNull() ?: (v as? JsonPrimitive)?.takeUnless { it.content == "false" }?.content.orEmpty()
    }

    private companion object {
        const val MODEL = "project.task"
        const val PAGE_SIZE = 30
        val ODOO_DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** Odoo datetimes are UTC without a zone. */
        fun parseUtc(text: String?): Instant =
            text?.let { LocalDateTime.parse(it, ODOO_DATETIME).toInstant(ZoneOffset.UTC) } ?: Instant.EPOCH
    }
}
