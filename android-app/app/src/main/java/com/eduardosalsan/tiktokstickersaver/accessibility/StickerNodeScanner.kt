package com.eduardosalsan.tiktokstickersaver.accessibility

import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Heurística para encontrar, dentro del árbol de accesibilidad de TikTok,
 * qué nodos parecen ser el sticker de un comentario (y no un avatar, un
 * ícono de la barra inferior, o la miniatura del video).
 *
 * TikTok no expone ninguna API para esto: solo podemos inspeccionar lo que
 * el propio sistema de Accesibilidad de Android ya reporta sobre la
 * pantalla (clase del View, si es una imagen, su tamaño y posición). Por
 * eso es una heurística basada en tamaño/forma, NO una lectura exacta.
 *
 * Si notas que detecta de más o de menos, este es el archivo a ajustar:
 * juega con MIN_SIZE_DP / MAX_SIZE_DP / los rangos de aspect ratio.
 */
object StickerNodeScanner {

    data class Candidate(val bounds: Rect)

    // --- Umbrales ajustables -------------------------------------------------
    // Los stickers de comentarios de TikTok suelen verse más grandes que un
    // avatar (~32-40dp) y más pequeños que una miniatura de video. Ajusta
    // estos valores si tu versión de TikTok usa otro tamaño.
    private const val MIN_SIZE_DP = 40f
    private const val MAX_SIZE_DP = 150f

    // Los stickers son aproximadamente cuadrados; esto descarta barras de
    // texto (muy anchas) y separadores (muy delgados).
    private const val MIN_ASPECT_RATIO = 0.75f
    private const val MAX_ASPECT_RATIO = 1.35f

    // Límite defensivo para no recorrer árboles gigantes ni saturar la UI
    // del selector de capturas con demasiadas miniaturas.
    private const val MAX_CANDIDATES = 12
    // --------------------------------------------------------------------

    /**
     * Recorre el árbol de nodos a partir de [root] (normalmente
     * `rootInActiveWindow` del servicio) y devuelve los rectángulos, en
     * coordenadas de pantalla, de los nodos candidatos a ser stickers.
     */
    fun findStickerCandidates(
        root: AccessibilityNodeInfo?,
        displayMetrics: DisplayMetrics
    ): List<Candidate> {
        if (root == null) return emptyList()

        val minPx = dpToPx(MIN_SIZE_DP, displayMetrics)
        val maxPx = dpToPx(MAX_SIZE_DP, displayMetrics)

        val results = mutableListOf<Candidate>()
        val seenBounds = HashSet<String>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.addLast(root)

        while (pending.isNotEmpty() && results.size < MAX_CANDIDATES) {
            val node = pending.removeLast()

            val childCount = node.childCount
            for (i in 0 until childCount) {
                node.getChild(i)?.let { pending.addLast(it) }
            }

            if (looksLikeSticker(node, minPx, maxPx)) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val key = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
                if (seenBounds.add(key)) {
                    results.add(Candidate(bounds))
                }
            }
            // Nota: no llamamos a node.recycle(). Desde la API 33 ese método
            // no hace nada (el sistema gestiona el ciclo de vida solo); en
            // versiones anteriores, al ser un escaneo puntual disparado por
            // un toque del usuario (no continuo), el costo es despreciable.
        }
        return results
    }

    private fun looksLikeSticker(node: AccessibilityNodeInfo, minPx: Int, maxPx: Int): Boolean {
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName?.toString()?.lowercase().orEmpty()

        // Si TikTok no minimiza sus ids de recurso, esto descarta avatares e
        // íconos por nombre antes de mirar el tamaño.
        if (viewId.contains("avatar") || viewId.contains("icon") || viewId.contains("cover")) {
            return false
        }

        // Aceptamos cualquier clase cuyo nombre contenga "Image" (ImageView
        // y subclases personalizadas, que es lo habitual en apps grandes).
        if (!className.contains("Image", ignoreCase = true)) return false
        if (!node.isVisibleToUser) return false

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val width = bounds.width()
        val height = bounds.height()
        if (width < minPx || height < minPx) return false
        if (width > maxPx || height > maxPx) return false

        val aspectRatio = width.toFloat() / height.toFloat()
        return aspectRatio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO
    }

    private fun dpToPx(dp: Float, metrics: DisplayMetrics): Int = (dp * metrics.density).toInt()
}
