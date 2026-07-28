package com.eduardosalsan.tiktokstickersaver.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.eduardosalsan.tiktokstickersaver.R
import com.eduardosalsan.tiktokstickersaver.accessibility.TikTokAccessibilityService
import com.eduardosalsan.tiktokstickersaver.databinding.ActivityMainBinding

/**
 * Pantalla principal: solo muestra el estado del Servicio de Accesibilidad
 * (que es lo único que el usuario debe activar manualmente) y accesos
 * directos a la galería de stickers y a la ayuda. Toda la lógica de
 * captura vive en TikTokAccessibilityService, que corre en segundo plano
 * incluso con esta Activity cerrada.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenAccessibilitySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOpenStickerPack.setOnClickListener {
            startActivity(Intent(this, StickerPackActivity::class.java))
        }
        binding.btnHelp.setOnClickListener { showHelp() }
    }

    override fun onResume() {
        super.onResume()
        // El usuario puede volver de Ajustes tras activar/desactivar el
        // servicio, así que refrescamos el estado cada vez que esta
        // pantalla vuelve a primer plano.
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        binding.accessibilityStatusLabel.text =
            getString(if (enabled) R.string.accessibility_status_on else R.string.accessibility_status_off)
        binding.btnOpenAccessibilitySettings.text = getString(
            if (enabled) R.string.action_open_accessibility_settings_again
            else R.string.action_open_accessibility_settings
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, TikTokAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_help)
            .setMessage(R.string.help_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
