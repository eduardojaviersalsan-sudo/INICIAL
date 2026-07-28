package com.eduardosalsan.tiktokstickersaver.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.eduardosalsan.tiktokstickersaver.R
import com.eduardosalsan.tiktokstickersaver.capture.ScreenshotCapture
import com.eduardosalsan.tiktokstickersaver.sticker.StickerPackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio de Accesibilidad: el corazón de la app.
 *
 * Mientras esté activo (el usuario lo enciende una vez en Ajustes >
 * Accesibilidad) y detecta que TikTok está en primer plano, dibuja un
 * botoncito flotante ("overlay de accesibilidad", que no necesita el
 * permiso "Mostrar sobre otras apps"). Al tocarlo:
 *
 *   1) Toma una captura de pantalla (ScreenshotCapture).
 *   2) Escanea el árbol de accesibilidad para adivinar qué recuadros
 *      parecen stickers (StickerNodeScanner).
 *   3) Recorta esas zonas de la captura y las muestra en un segundo
 *      overlay para que el usuario toque el sticker que quiere guardar.
 *   4) Al tocar uno, se convierte al formato que exige WhatsApp y se
 *      agrega al paquete (StickerPackManager).
 *
 * Este archivo es "pegamento" de Android (Service, WindowManager, vistas);
 * si quieres ajustar CÓMO se detectan los stickers, edita
 * StickerNodeScanner.kt; si quieres ajustar el procesamiento de imagen,
 * edita StickerPackManager.kt.
 */
class TikTokAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var packManager: StickerPackManager

    private var captureButtonView: View? = null
    private var pickerView: View? = null

    private val tiktokPackages = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically.go"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        packManager = StickerPackManager(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val foregroundPackage = event.packageName?.toString()
        if (foregroundPackage != null && foregroundPackage in tiktokPackages) {
            showCaptureButton()
        } else {
            hideCaptureButton()
            hidePicker()
        }
    }

    override fun onInterrupt() {
        hideCaptureButton()
        hidePicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        hideCaptureButton()
        hidePicker()
    }

    // --- Botón flotante --------------------------------------------------

    private fun showCaptureButton() {
        if (captureButtonView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_capture_button, null)
        view.findViewById<ImageButton>(R.id.captureButton).setOnClickListener { onCaptureClicked() }

        val params = overlayLayoutParams(Gravity.END or Gravity.CENTER_VERTICAL, dpToPx(12), 0)
        try {
            windowManager.addView(view, params)
            captureButtonView = view
        } catch (e: Exception) {
            // Si el sistema todavía no terminó de habilitar el servicio o la
            // ventana de TikTok cambió justo en este instante, simplemente
            // no mostramos el botón esta vez; el próximo cambio de ventana
            // lo vuelve a intentar.
        }
    }

    private fun hideCaptureButton() {
        captureButtonView?.let { safeRemoveView(it) }
        captureButtonView = null
    }

    // --- Captura y selección de sticker -----------------------------------

    private fun onCaptureClicked() {
        serviceScope.launch {
            val screenshot = ScreenshotCapture.capture(this@TikTokAccessibilityService)
            if (screenshot == null) {
                toast(getString(R.string.error_screenshot_failed))
                return@launch
            }

            val candidates = StickerNodeScanner.findStickerCandidates(rootInActiveWindow, resources.displayMetrics)
            if (candidates.isEmpty()) {
                toast(getString(R.string.info_no_stickers_found))
                return@launch
            }

            showPicker(screenshot, candidates)
        }
    }

    private fun showPicker(screenshot: Bitmap, candidates: List<StickerNodeScanner.Candidate>) {
        hidePicker()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_sticker_picker, null)
        val container = view.findViewById<LinearLayout>(R.id.pickerCandidatesContainer)
        val closeButton = view.findViewById<ImageButton>(R.id.pickerCloseButton)

        candidates.forEach { candidate ->
            val crop = cropSafely(screenshot, candidate.bounds) ?: return@forEach
            val thumbnailSize = dpToPx(64)
            val thumbnail = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(thumbnailSize, thumbnailSize).apply {
                    marginEnd = dpToPx(8)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(crop)
                setOnClickListener { saveSticker(crop) }
            }
            container.addView(thumbnail)
        }

        closeButton.setOnClickListener { hidePicker() }

        val params = overlayLayoutParams(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dpToPx(32))
        try {
            windowManager.addView(view, params)
            pickerView = view
        } catch (e: Exception) {
            // Ver comentario equivalente en showCaptureButton().
        }
    }

    private fun hidePicker() {
        pickerView?.let { safeRemoveView(it) }
        pickerView = null
    }

    private fun saveSticker(bitmap: Bitmap) {
        serviceScope.launch {
            val result = withContext(Dispatchers.Default) { packManager.addSticker(bitmap) }
            result.onSuccess {
                toast(getString(R.string.toast_sticker_saved))
            }.onFailure { error ->
                toast(error.message ?: getString(R.string.toast_save_error))
            }
            hidePicker()
        }
    }

    // --- Utilidades ---------------------------------------------------------

    /** Recorta [bounds] de [source], recortando los límites al tamaño real del bitmap. */
    private fun cropSafely(source: Bitmap, bounds: Rect): Bitmap? {
        val left = bounds.left.coerceIn(0, source.width)
        val top = bounds.top.coerceIn(0, source.height)
        val right = bounds.right.coerceIn(0, source.width)
        val bottom = bounds.bottom.coerceIn(0, source.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null
        return try {
            Bitmap.createBitmap(source, left, top, width, height)
        } catch (e: Exception) {
            null
        }
    }

    private fun overlayLayoutParams(gravity: Int, marginX: Int, marginY: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = marginX
            this.y = marginY
        }
    }

    private fun safeRemoveView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            // La vista ya no estaba adjunta; no hay nada que hacer.
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
