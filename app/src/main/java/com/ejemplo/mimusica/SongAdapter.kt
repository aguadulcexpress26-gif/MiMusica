package com.ejemplo.mimusica

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val songs: List<Song>,
    private val onClick: (Song) -> Unit,
    private val onCheckChange: (Song, Boolean) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongVH>() {

    class SongVH(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtArtist: TextView = view.findViewById(R.id.txtArtist)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongVH(view)
    }

    override fun onBindViewHolder(holder: SongVH, position: Int) {
        val song = songs[position]
        holder.txtTitle.text = song.title
        holder.txtArtist.text = song.artist
        
        // Evitar que el checkbox se recicle mal
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = song.isSelected

        // Al tocar el checkbox
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            onCheckChange(song, isChecked)
        }

        // Al tocar el resto de la canción (reproducir)
        holder.itemView.setOnClickListener { onClick(song) }
    }

    override fun getItemCount(): Int = songs.size

    fun updateSingleItem(position: Int) {
        notifyItemChanged(position)
    }
}
