package com.eduardosalsan.tiktokstickersaver.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.eduardosalsan.tiktokstickersaver.sticker.StickerPackManager

/**
 * ContentProvider que expone nuestro paquete de stickers a WhatsApp,
 * siguiendo el contrato público que Meta documenta para "Third Party
 * Sticker Apps" (el mismo que implementa su repositorio de ejemplo
 * github.com/WhatsApp/stickers). WhatsApp consulta estas URIs desde su
 * propio proceso después de que el usuario acepta agregar el paquete
 * (ver WhatsAppIntegration.kt):
 *
 *  content://<authority>/metadata            -> datos del/los paquete(s)
 *  content://<authority>/metadata/<packId>   -> datos de un paquete puntual
 *  content://<authority>/stickers/<packId>   -> lista de stickers del paquete
 *  content://<authority>/stickers_asset/<packId>/<archivo>  -> bytes del webp/png
 *
 * Si en el futuro WhatsApp ajusta ligeramente los nombres de columnas, este
 * es el único archivo que debería necesitar cambios: compáralo contra la
 * versión vigente del repositorio oficial antes de depurar nada más.
 */
class StickerContentProvider : ContentProvider() {

    private lateinit var authority: String
    private lateinit var manager: StickerPackManager
    private lateinit var matcher: UriMatcher

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        authority = StickerPackManager.authority(ctx)
        manager = StickerPackManager(ctx)
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "metadata", CODE_METADATA)
            addURI(authority, "metadata/*", CODE_METADATA_SPECIFIC)
            addURI(authority, "stickers/*", CODE_STICKERS)
            addURI(authority, "stickers_asset/*/*", CODE_STICKERS_ASSET)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        return when (matcher.match(uri)) {
            CODE_METADATA, CODE_METADATA_SPECIFIC -> packMetadataCursor()
            CODE_STICKERS -> stickersCursor()
            else -> throw IllegalArgumentException("URI no soportada: $uri")
        }
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        CODE_METADATA, CODE_METADATA_SPECIFIC -> "vnd.android.cursor.dir/vnd.$authority.metadata"
        CODE_STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
        CODE_STICKERS_ASSET -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
        else -> null
    }

    /** WhatsApp pide los bytes de cada sticker (y el ícono de bandeja) por aquí. */
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != CODE_STICKERS_ASSET) return super.openAssetFile(uri, mode)

        // La ruta es /stickers_asset/<packId>/<archivo>. Como esta app solo
        // maneja un paquete (DEFAULT_PACK_ID), ignoramos <packId>; si algún
        // día agregas varios paquetes, úsalo para elegir el StickerPackManager
        // correcto en lugar de asumir siempre el mismo.
        val fileName = uri.lastPathSegment ?: return null
        val pack = manager.getOrCreateDefaultPack()
        val file = if (fileName == pack.trayImageFile) manager.trayFile() else manager.stickerFile(fileName)
        if (!file.exists()) return null

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    private fun packMetadataCursor(): Cursor {
        val pack = manager.getOrCreateDefaultPack()
        return MatrixCursor(METADATA_COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    pack.identifier,
                    pack.name,
                    pack.publisher,
                    pack.trayImageFile,
                    "", // android_play_store_link (no publicamos en Play Store)
                    "", // ios_app_download_link
                    "", // sticker_pack_publisher_email
                    "", // sticker_pack_publisher_website
                    "", // sticker_pack_privacy_policy_website
                    "", // sticker_pack_license_agreement_website
                    1L, // image_data_version: súbelo si reemplazas archivos con el mismo nombre
                    0,  // avoid_cache: 0 = permitir que WhatsApp cachee los stickers
                    if (pack.animatedStickerPack) 1 else 0
                )
            )
        }
    }

    private fun stickersCursor(): Cursor {
        val stickers = manager.listStickers()
        return MatrixCursor(STICKERS_COLUMNS).apply {
            stickers.forEach { sticker ->
                addRow(arrayOf<Any?>(sticker.fileName, sticker.emojis.joinToString(",")))
            }
        }
    }

    // WhatsApp solo lee: no necesitamos soportar escritura por esta vía.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val CODE_METADATA = 1
        private const val CODE_METADATA_SPECIFIC = 2
        private const val CODE_STICKERS = 3
        private const val CODE_STICKERS_ASSET = 4

        private val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack"
        )

        private val STICKERS_COLUMNS = arrayOf("sticker_file_name", "sticker_emoji")
    }
}
