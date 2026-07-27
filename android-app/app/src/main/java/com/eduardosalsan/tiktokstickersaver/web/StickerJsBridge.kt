package com.eduardosalsan.tiktokstickersaver.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Puente JS -> Kotlin. El script inyectado en la página llama a
 * AndroidStickers.onStickerFound(url) por cada imagen candidata a sticker
 * que encuentra dentro de los comentarios. Los callbacks de WebView llegan
 * en un hilo de JS, así que se despachan al hilo principal.
 */
class StickerJsBridge(private val onStickerFound: (String) -> Unit) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onStickerFound(imageUrl: String) {
        if (imageUrl.isBlank()) return
        mainHandler.post { onStickerFound.invoke(imageUrl) }
    }

    companion object {
        const val INTERFACE_NAME = "AndroidStickers"
    }
}
