package com.eduardosalsan.tiktokstickersaver.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.eduardosalsan.tiktokstickersaver.R
import com.eduardosalsan.tiktokstickersaver.sticker.StickerFile
import com.eduardosalsan.tiktokstickersaver.sticker.StickerPackManager

class StickerGridAdapter(
    private val packManager: StickerPackManager,
    private val onDeleteClick: (StickerFile) -> Unit
) : RecyclerView.Adapter<StickerGridAdapter.ViewHolder>() {

    private val items = mutableListOf<StickerFile>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.stickerImage)
        val deleteButton: ImageButton = view.findViewById(R.id.btnDeleteSticker)
    }

    fun submit(newItems: List<StickerFile>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val file = packManager.stickerFile(item.fileName)
        holder.image.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        holder.deleteButton.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
