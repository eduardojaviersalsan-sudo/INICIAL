package com.eduardosalsan.tiktokstickersaver.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.eduardosalsan.tiktokstickersaver.sticker.StickerPack
import com.eduardosalsan.tiktokstickersaver.sticker.StickerPackManager

/**
 * WhatsApp no tiene un "pegar sticker" genérico desde el portapapeles: la
 * única vía oficial es pedirle que agregue nuestro paquete de stickers (una
 * sola vez, con un diálogo nativo de WhatsApp). Desde ahí en adelante, esos
 * stickers aparecen en su propio teclado de stickers y se pueden enviar en
 * cualquier chat, que es el resultado que buscas.
 *
 * Referencia del contrato (acción, extras y ContentProvider): la
 * documentación/​repositorio oficial "WhatsApp/stickers" en GitHub. Si
 * WhatsApp actualiza estos nombres en el futuro y el botón deja de
 * funcionar, ese es el sitio donde confirmar los valores vigentes.
 */
object WhatsAppIntegration {

    private const val TAG = "WhatsAppIntegration"

    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    private const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

    fun isWhatsAppInstalled(context: Context): Boolean = isPackageInstalled(context, WHATSAPP_PACKAGE)

    fun isWhatsAppBusinessInstalled(context: Context): Boolean =
        isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE)

    /**
     * Abre el diálogo nativo de WhatsApp "¿Agregar paquete de stickers?".
     * Si el paquete ya estaba agregado, WhatsApp simplemente lo indica ahí
     * mismo; no hace falta que nosotros lo verifiquemos de antemano.
     *
     * @return false si ni WhatsApp ni WhatsApp Business están instalados.
     */
    fun addPackToWhatsApp(context: Context, pack: StickerPack): Boolean {
        val targetPackage = when {
            isWhatsAppInstalled(context) -> WHATSAPP_PACKAGE
            isWhatsAppBusinessInstalled(context) -> WHATSAPP_BUSINESS_PACKAGE
            else -> return false
        }

        val intent = Intent(ACTION_ENABLE_STICKER_PACK).apply {
            putExtra(EXTRA_STICKER_PACK_ID, pack.identifier)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, StickerPackManager.authority(context))
            putExtra(EXTRA_STICKER_PACK_NAME, pack.name)
            setPackage(targetPackage)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir el diálogo de WhatsApp para agregar el paquete", e)
            false
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
