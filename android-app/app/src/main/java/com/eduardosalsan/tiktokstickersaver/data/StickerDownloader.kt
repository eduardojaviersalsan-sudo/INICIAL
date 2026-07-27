package com.eduardosalsan.tiktokstickersaver.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Descarga una imagen (sticker) mostrada en el WebView y la guarda en
 * Pictures/TikTokStickers, usando MediaStore en Android 10+ (sin necesitar
 * permisos de almacenamiento) y escritura directa + escaneo de medios en
 * versiones anteriores.
 */
class StickerDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder().build()

    private val albumRelativePath = "Pictures/${ALBUM_NAME}"

    sealed class Result {
        data class Success(val fileName: String) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun download(imageUrl: String, referer: String? = null): Result = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(imageUrl)
                .header("User-Agent", DOWNLOAD_USER_AGENT)
            if (!referer.isNullOrBlank()) {
                requestBuilder.header("Referer", referer)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Failure("HTTP ${response.code}")
                }
                val bytes = response.body?.bytes()
                    ?: return@withContext Result.Failure("Respuesta vacía")

                val extension = guessExtension(imageUrl, response.header("Content-Type"))
                val fileName = "sticker_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"
                val mimeType = mimeTypeFor(extension)

                val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(fileName, mimeType, bytes)
                } else {
                    saveLegacy(fileName, bytes)
                }

                if (saved) Result.Success(fileName) else Result.Failure("No se pudo escribir el archivo")
            }
        } catch (e: IOException) {
            Result.Failure(e.message ?: "Error de red")
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Error desconocido")
        }
    }

    private fun saveWithMediaStore(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, albumRelativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values) ?: return false

        return try {
            resolver.openOutputStream(itemUri)?.use { it.write(bytes) } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            true
        } catch (e: IOException) {
            resolver.delete(itemUri, null, null)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(fileName: String, bytes: ByteArray): Boolean {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val albumDir = File(picturesDir, ALBUM_NAME)
        if (!albumDir.exists() && !albumDir.mkdirs()) return false

        val outFile = File(albumDir, fileName)
        return try {
            FileOutputStream(outFile).use { it.write(bytes) }
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(outFile.absolutePath), null, null
            )
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun guessExtension(url: String, contentType: String?): String {
        contentType?.let {
            when {
                it.contains("png") -> return "png"
                it.contains("webp") -> return "webp"
                it.contains("gif") -> return "gif"
                it.contains("jpeg") || it.contains("jpg") -> return "jpg"
            }
        }
        val clean = url.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase()
        return if (ext in listOf("png", "jpg", "jpeg", "webp", "gif")) ext else "jpg"
    }

    private fun mimeTypeFor(extension: String): String = when (extension) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    companion object {
        const val ALBUM_NAME = "TikTokStickers"
        const val DOWNLOAD_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}
