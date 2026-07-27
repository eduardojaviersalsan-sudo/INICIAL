package com.eduardosalsan.tiktokstickersaver.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.eduardosalsan.tiktokstickersaver.R

class StickerStripAdapter(
    private val items: MutableList<String> = mutableListOf(),
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<StickerStripAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.stickerThumbnail)
    }

    fun submit(urls: List<String>) {
        items.clear()
        items.addAll(urls)
        notifyDataSetChanged()
    }

    fun addIfNew(url: String): Boolean {
        if (items.contains(url)) return false
        items.add(url)
        notifyItemInserted(items.size - 1)
        return true
    }

    fun currentUrls(): List<String> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sticker_strip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = items[position]
        holder.thumbnail.load(url) {
            crossfade(true)
        }
        holder.itemView.setOnClickListener { onClick(url) }
    }

    override fun getItemCount(): Int = items.size
}
