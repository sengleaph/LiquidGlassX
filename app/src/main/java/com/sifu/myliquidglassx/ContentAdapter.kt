package com.sifu.myliquidglassx

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Sample row model — a title, a subtitle, and a background color for the card. */
data class ContentItem(val title: String, val subtitle: String, val color: Int)

class ContentAdapter(private val items: List<ContentItem>) :
    RecyclerView.Adapter<ContentAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.itemRoot)
        val title: TextView = itemView.findViewById(R.id.itemTitle)
        val subtitle: TextView = itemView.findViewById(R.id.itemSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_content, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle
        holder.root.backgroundTintList = ColorStateList.valueOf(item.color)
    }

    override fun getItemCount(): Int = items.size

    companion object {
        /** A palette that contrasts nicely with the default green backdrop, so the glass bar has vivid stuff to blur. */
        private val palette = intArrayOf(
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#F7A445"),
            Color.parseColor("#F9D66A"),
            Color.parseColor("#66CDAA"),
            Color.parseColor("#4A90E2"),
            Color.parseColor("#A78BFA"),
            Color.parseColor("#FF7EB9"),
            Color.parseColor("#4CD0C0"),
        )

        fun sampleData(): List<ContentItem> {
            val titles = listOf(
                "Aurora" to "Nocturne Quartet",
                "River Song" to "Kiri Wu",
                "Midnight Drive" to "Sable & Nine",
                "Ember" to "Coastal Choir",
                "Paper Wings" to "Yuma Ito",
                "Cinder Rain" to "The Aviary",
                "Halcyon" to "Marina Vale",
                "Blue Hour" to "Anais Roux",
                "Small Fires" to "Otis Grove",
                "Cascade" to "Loom",
                "Signal Fade" to "Argent Bay",
                "Quiet Hours" to "Sara Kilma",
                "Verdant" to "Foxglove",
                "Longitude" to "Ilse Marner",
                "Late September" to "Third Coast",
                "Northline" to "Palindrome",
                "The Gallery" to "Mira Volt",
                "Stormlight" to "Ossian",
                "Cormorant" to "Field Report",
                "After Rain" to "Dune & Salt",
            )
            return titles.mapIndexed { i, (title, artist) ->
                ContentItem(title, artist, palette[i % palette.size])
            }
        }
    }
}
