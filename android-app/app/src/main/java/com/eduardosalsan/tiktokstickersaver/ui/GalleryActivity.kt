package com.eduardosalsan.tiktokstickersaver.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.eduardosalsan.tiktokstickersaver.R
import com.eduardosalsan.tiktokstickersaver.data.StickerItem
import com.eduardosalsan.tiktokstickersaver.data.StickerRepository
import com.eduardosalsan.tiktokstickersaver.databinding.ActivityGalleryBinding
import kotlinx.coroutines.launch

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var repository: StickerRepository
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = StickerRepository(applicationContext)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = GalleryAdapter(
            onShare = { shareSticker(it) },
            onDelete = { deleteSticker(it) }
        )

        binding.galleryList.layoutManager = GridLayoutManager(this, 3)
        binding.galleryList.adapter = adapter

        loadStickers()
    }

    override fun onResume() {
        super.onResume()
        loadStickers()
    }

    private fun loadStickers() {
        lifecycleScope.launch {
            val stickers = repository.listSavedStickers()
            adapter.submit(stickers)
            binding.emptyView.visibility = if (stickers.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun shareSticker(item: StickerItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_gallery)))
    }

    private fun deleteSticker(item: StickerItem) {
        val removed = repository.delete(item)
        if (removed) {
            adapter.removeItem(item)
            binding.emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        } else {
            Toast.makeText(this, R.string.toast_save_error, Toast.LENGTH_SHORT).show()
        }
    }
}
