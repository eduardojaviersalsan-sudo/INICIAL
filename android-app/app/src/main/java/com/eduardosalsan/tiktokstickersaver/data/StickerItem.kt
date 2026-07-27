package com.eduardosalsan.tiktokstickersaver.data

import android.net.Uri

data class StickerItem(
    val uri: Uri,
    val displayName: String,
    val dateAddedSeconds: Long
)
