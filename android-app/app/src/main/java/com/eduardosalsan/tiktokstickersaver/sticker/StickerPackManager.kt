package com.eduardosalsan.tiktokstickersaver.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.eduardosalsan.tiktokstickersaver.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Convierte los recortes de pantalla capturados en stickers válidos para
 * WhatsApp y los guarda en el almacenamiento privado de la app
 * (`filesDir/stickerpacks/<packId>/...`). También lee/escribe el
 * `pack.json` con los metadatos del paquete, que luego lee
 * [com.eduardosalsan.tiktokstickersaver.provider.StickerContentProvider]
 * para responder las consultas de WhatsApp.
 *
 * Todo lo que probablemente quieras ajustar (tamaño, límites, nombre del
 * paquete por defecto) está arriba, en las constantes del companion object.
 */
class StickerPackManager(private val context: Context) {

    /** Recorta/rellena y guarda [bitmap] como un nuevo sticker del paquete por defecto. */
    @Synchronized
    fun addSticker(bitmap: Bitmap): Result<StickerFile> {
        val pack = getOrCreateDefaultPack()
        if (pack.stickers.size >= MAX_STICKERS_PER_PACK) {
            return Result.failure(IllegalStateException(context.getString(R.string.error_pack_full)))
        }

        val squared = resizeAndPad(bitmap, STICKER_SIZE_PX)
        val bytes = compressUnderLimit(squared, MAX_STICKER_BYTES)
        val fileName = "sticker_${System.currentTimeMillis()}.webp"
        stickerFile(fileName).writeBytes(bytes)

        if (!trayFile().exists()) {
            generateTrayIcon(bitmap)
        }

        val entry = StickerFile(fileName)
        pack.stickers.add(entry)
        writePackJson(pack)
        return Result.success(entry)
    }

    @Synchronized
    fun removeSticker(fileName: String) {
        val pack = getOrCreateDefaultPack()
        pack.stickers.removeAll { it.fileName == fileName }
        writePackJson(pack)
        stickerFile(fileName).delete()
    }

    @Synchronized
    fun renamePack(newName: String) {
        val pack = getOrCreateDefaultPack()
        pack.name = newName
        writePackJson(pack)
    }

    fun listStickers(): List<StickerFile> = getOrCreateDefaultPack().stickers

    /** Carga (o crea, si es la primera vez) el único paquete que maneja esta app. */
    @Synchronized
    fun getOrCreateDefaultPack(): StickerPack {
        readPackJson()?.let { return it }
        val pack = StickerPack(
            identifier = DEFAULT_PACK_ID,
            name = context.getString(R.string.default_pack_name),
            publisher = context.getString(R.string.default_pack_publisher),
            trayImageFile = TRAY_FILE_NAME,
            stickers = mutableListOf()
        )
        writePackJson(pack)
        return pack
    }

    fun stickerFile(fileName: String): File = File(packDir(), fileName)

    fun trayFile(): File = File(packDir(), TRAY_FILE_NAME)

    // --- Almacenamiento en disco ---------------------------------------------

    private fun rootDir(): File = File(context.filesDir, "stickerpacks").apply { mkdirs() }

    private fun packDir(): File = File(rootDir(), DEFAULT_PACK_ID).apply { mkdirs() }

    private fun packJsonFile(): File = File(packDir(), "pack.json")

    private fun writePackJson(pack: StickerPack) {
        val json = JSONObject().apply {
            put("identifier", pack.identifier)
            put("name", pack.name)
            put("publisher", pack.publisher)
            put("tray_image_file", pack.trayImageFile)
            put("animated", pack.animatedStickerPack)
            put("stickers", JSONArray().apply {
                pack.stickers.forEach { sticker ->
                    put(JSONObject().apply {
                        put("file_name", sticker.fileName)
                        put("emojis", JSONArray(sticker.emojis))
                    })
                }
            })
        }
        packJsonFile().writeText(json.toString())
    }

    private fun readPackJson(): StickerPack? {
        val file = packJsonFile()
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val stickersJson = json.optJSONArray("stickers") ?: JSONArray()
            val stickers = (0 until stickersJson.length()).map { i ->
                val entry = stickersJson.getJSONObject(i)
                val emojisJson = entry.optJSONArray("emojis") ?: JSONArray()
                val emojis = (0 until emojisJson.length()).map { emojisJson.getString(it) }
                StickerFile(entry.getString("file_name"), emojis)
            }.toMutableList()

            StickerPack(
                identifier = json.getString("identifier"),
                name = json.getString("name"),
                publisher = json.getString("publisher"),
                trayImageFile = json.optString("tray_image_file", TRAY_FILE_NAME),
                stickers = stickers,
                animatedStickerPack = json.optBoolean("animated", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    // --- Procesamiento de imagen ----------------------------------------------

    /**
     * Escala [source] para que quepa dentro de un cuadrado de [size]x[size]
     * manteniendo su proporción, y lo centra sobre fondo transparente
     * (letterbox). Así el sticker final siempre mide exactamente lo que
     * exige WhatsApp, sin deformar el recorte original.
     */
    private fun resizeAndPad(source: Bitmap, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output) // el bitmap ARGB_8888 nace transparente

        val scale = minOf(size.toFloat() / source.width, size.toFloat() / source.height)
        val scaledWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

        val left = (size - scaledWidth) / 2f
        val top = (size - scaledHeight) / 2f
        canvas.drawBitmap(scaled, left, top, null)
        return output
    }

    /** Comprime a WEBP bajando la calidad hasta quedar por debajo de [maxBytes]. */
    private fun compressUnderLimit(bitmap: Bitmap, maxBytes: Int): ByteArray {
        var quality = 100
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, stream)
            bytes = stream.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality > 10)
        return bytes
    }

    private fun generateTrayIcon(source: Bitmap) {
        // WhatsApp exige el ícono de bandeja en PNG (formato sin pérdida), así
        // que no podemos "bajar calidad" como con el WEBP de los stickers. En
        // la práctica, un recorte de sticker a 96x96 rara vez supera los 50KB
        // exigidos por WhatsApp; si tu contenido es muy detallado y se pasa
        // del límite, este es el lugar para agregar una reducción de colores.
        val trayBitmap = resizeAndPad(source, TRAY_SIZE_PX)
        val stream = ByteArrayOutputStream()
        trayBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        trayFile().writeBytes(stream.toByteArray())
    }

    companion object {
        /** Identificador único del paquete. Cámbialo si quieres soportar varios paquetes. */
        const val DEFAULT_PACK_ID = "tiktok_stickers"
        const val TRAY_FILE_NAME = "tray.png"

        // Límites que exige WhatsApp para paquetes de stickers de terceros.
        const val MIN_STICKERS_PER_PACK = 3
        const val MAX_STICKERS_PER_PACK = 30
        const val STICKER_SIZE_PX = 512
        const val TRAY_SIZE_PX = 96
        const val MAX_STICKER_BYTES = 100 * 1024
        const val MAX_TRAY_BYTES = 50 * 1024

        /** Autoridad del ContentProvider que WhatsApp usa para pedir estos stickers. */
        fun authority(context: Context): String = "${context.packageName}.stickercontentprovider"
    }
}
