package com.eduardosalsan.tiktokstickersaver.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Envuelve AccessibilityService.takeScreenshot() (disponible desde Android 11
 * / API 30) en una función `suspend` que entrega directamente un Bitmap de
 * software listo para recortar.
 *
 * takeScreenshot() no pide ningún permiso adicional al usuario: basta con
 * que el Servicio de Accesibilidad ya esté activo. Es la razón por la que
 * este proyecto fija minSdk = 30 (ver app/build.gradle.kts): en versiones
 * anteriores de Android no existe esta API y habría que recurrir a
 * MediaProjection, que exige repetir un diálogo de permiso del sistema.
 */
@RequiresApi(Build.VERSION_CODES.R)
object ScreenshotCapture {

    private const val TAG = "ScreenshotCapture"

    suspend fun capture(service: AccessibilityService): Bitmap? = suspendCoroutine { continuation ->
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val hardwareBuffer = result.hardwareBuffer
                    try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                        // Un Bitmap "hardware" no permite leer/recortar píxeles
                        // directamente (getPixels falla). Lo copiamos a un
                        // formato de software (ARGB_8888) antes de usarlo.
                        val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        continuation.resume(softwareBitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "No se pudo procesar la captura de pantalla", e)
                        continuation.resume(null)
                    } finally {
                        hardwareBuffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot() falló con código $errorCode")
                    continuation.resume(null)
                }
            }
        )
    }
}
