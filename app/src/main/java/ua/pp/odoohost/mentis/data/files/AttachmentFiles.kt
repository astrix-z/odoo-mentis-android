package ua.pp.odoohost.mentis.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.pp.odoohost.mentis.data.odoo.OdooException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max

/** A file picked or shot on the phone, ready to upload. */
class LocalFile(val name: String, val mimetype: String, val bytes: ByteArray)

class FileTooLargeException(val limitMb: Int) : Exception("File is larger than $limitMb MB")

/** Moves files between the phone and Odoo attachments. */
class AttachmentFiles(private val context: Context, private val http: OkHttpClient) {

    private val authority = "${context.packageName}.files"

    /** A fresh file for the camera app to write a photo into. */
    fun newPhotoUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("photo_", ".jpg", dir)
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Reads a picked file. Photos are scaled down to [MAX_IMAGE_SIDE] px and turned upright,
     * since a phone camera shot is often 5–10 MB and stored sideways with an EXIF flag.
     */
    suspend fun read(uri: Uri): LocalFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimetype = resolver.getType(uri) ?: "application/octet-stream"
        val name = displayName(uri) ?: "file"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Cannot read $uri")
        if (mimetype == "image/jpeg" || mimetype == "image/png" || mimetype == "image/heic" || mimetype == "image/webp") {
            shrinkImage(bytes)?.let { return@withContext LocalFile(name.substringBeforeLast('.') + ".jpg", "image/jpeg", it) }
        }
        if (bytes.size > MAX_UPLOAD_MB * 1024 * 1024) throw FileTooLargeException(MAX_UPLOAD_MB)
        LocalFile(name, mimetype, bytes)
    }

    /** Downloads an attachment into the cache and returns a content:// Uri other apps can open. */
    suspend fun download(url: String, id: Int, name: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "attachments/$id").apply { mkdirs() }
        val file = File(dir, name.replace('/', '_').ifBlank { "file" })
        if (!file.exists()) {
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw OdooException.Http(response.code)
                    val part = File(dir, file.name + ".part")
                    part.outputStream().use { response.body.byteStream().copyTo(it) }
                    part.renameTo(file)
                }
            } catch (e: IOException) {
                throw OdooException.Network(e)
            }
        }
        FileProvider.getUriForFile(context, authority, file)
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment

    private fun shrinkImage(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = MAX_IMAGE_SIDE.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            postRotate(exifRotation(bytes).toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        return ByteArrayOutputStream().use {
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            it.toByteArray()
        }
    }

    private fun exifRotation(bytes: ByteArray): Int =
        when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private companion object {
        const val MAX_IMAGE_SIDE = 2048
        const val JPEG_QUALITY = 85
        const val MAX_UPLOAD_MB = 25
    }
}
