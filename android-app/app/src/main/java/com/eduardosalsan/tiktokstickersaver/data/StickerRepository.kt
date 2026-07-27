package com.eduardosalsan.tiktokstickersaver.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lee de MediaStore los stickers previamente guardados en Pictures/TikTokStickers. */
class StickerRepository(private val context: Context) {

    suspend fun listSavedStickers(): List<StickerItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<StickerItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )

        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            selectionArgs = arrayOf("Pictures/${StickerDownloader.ALBUM_NAME}%")
        } else {
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/${StickerDownloader.ALBUM_NAME}/%")
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    items.add(
                        StickerItem(
                            uri = uri,
                            displayName = cursor.getString(nameCol) ?: "",
                            dateAddedSeconds = cursor.getLong(dateCol)
                        )
                    )
                }
            }

        items
    }

    fun delete(item: StickerItem): Boolean = try {
        context.contentResolver.delete(item.uri, null, null) > 0
    } catch (e: Exception) {
        false
    }
}
