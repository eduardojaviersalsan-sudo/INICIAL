package com.eduardosalsan.tiktokstickersaver.sticker

/**
 * Un sticker individual dentro del paquete. [emojis] es opcional (WhatsApp
 * permite asociar hasta 3 emojis por sticker para que aparezca en las
 * búsquedas de su teclado); esta app no los pide todavía, pero el modelo ya
 * los soporta por si quieres agregar esa función más adelante.
 */
data class StickerFile(
    val fileName: String,
    val emojis: List<String> = emptyList()
)

/**
 * Metadatos de un paquete de stickers, con el mismo significado que exige
 * el contrato de WhatsApp para "Third Party Sticker Apps" (ver
 * StickerContentProvider y el README).
 */
data class StickerPack(
    val identifier: String,
    var name: String,
    val publisher: String,
    val trayImageFile: String,
    val stickers: MutableList<StickerFile>,
    val animatedStickerPack: Boolean = false
)
