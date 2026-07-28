package com.eduardosalsan.tiktokstickersaver.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.eduardosalsan.tiktokstickersaver.R
import com.eduardosalsan.tiktokstickersaver.databinding.ActivityStickerPackBinding
import com.eduardosalsan.tiktokstickersaver.sticker.StickerFile
import com.eduardosalsan.tiktokstickersaver.sticker.StickerPackManager
import com.eduardosalsan.tiktokstickersaver.whatsapp.WhatsAppIntegration

/** Galería del paquete de stickers capturados: ver, borrar, renombrar y enviar a WhatsApp. */
class StickerPackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStickerPackBinding
    private lateinit var packManager: StickerPackManager
    private lateinit var adapter: StickerGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStickerPackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packManager = StickerPackManager(applicationContext)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = StickerGridAdapter(packManager) { sticker -> confirmDelete(sticker) }
        binding.stickerGrid.layoutManager = GridLayoutManager(this, 3)
        binding.stickerGrid.adapter = adapter

        binding.btnRenamePack.setOnClickListener { renamePack() }
        binding.btnAddToWhatsApp.setOnClickListener { addToWhatsApp() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val pack = packManager.getOrCreateDefaultPack()
        binding.packNameInput.setText(pack.name)
        adapter.submit(pack.stickers)
        binding.emptyView.visibility = if (pack.stickers.isEmpty()) View.VISIBLE else View.GONE
        binding.packCountLabel.text = getString(
            R.string.pack_count_label,
            pack.stickers.size,
            StickerPackManager.MAX_STICKERS_PER_PACK,
            StickerPackManager.MIN_STICKERS_PER_PACK
        )
    }

    private fun renamePack() {
        val newName = binding.packNameInput.text?.toString()?.trim().orEmpty()
        if (newName.isEmpty()) return
        packManager.renamePack(newName)
        Toast.makeText(this, R.string.toast_pack_renamed, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(sticker: StickerFile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                packManager.removeSticker(sticker.fileName)
                refresh()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun addToWhatsApp() {
        val pack = packManager.getOrCreateDefaultPack()
        if (pack.stickers.size < StickerPackManager.MIN_STICKERS_PER_PACK) {
            Toast.makeText(
                this,
                getString(R.string.error_need_more_stickers, StickerPackManager.MIN_STICKERS_PER_PACK),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val opened = WhatsAppIntegration.addPackToWhatsApp(this, pack)
        if (!opened) {
            Toast.makeText(this, R.string.error_whatsapp_not_installed, Toast.LENGTH_LONG).show()
        }
    }
}
