package com.eduardosalsan.tiktokstickersaver.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.eduardosalsan.tiktokstickersaver.R
import com.eduardosalsan.tiktokstickersaver.data.StickerItem

class GalleryAdapter(
    private val items: MutableList<StickerItem> = mutableListOf(),
    private val onShare: (StickerItem) -> Unit,
    private val onDelete: (StickerItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.stickerImage)
        val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    fun submit(newItems: List<StickerItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItem(item: StickerItem) {
        val index = items.indexOfFirst { it.uri == item.uri }
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_sticker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.image.load(item.uri) { crossfade(true) }
        holder.btnShare.setOnClickListener { onShare(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}
