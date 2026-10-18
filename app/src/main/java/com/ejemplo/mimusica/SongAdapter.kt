package com.ejemplo.mimusica

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val songs: List<Song>,
    private val onClick: (Song) -> Unit,
    private val onLongClick: (Song) -> Unit // <-- NUEVO
) : RecyclerView.Adapter<SongAdapter.SongVH>() {

    class SongVH(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtArtist: TextView = view.findViewById(R.id.txtArtist)
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
        
        // Clic normal (reproducir)
        holder.itemView.setOnClickListener { onClick(song) }
        
        // Clic largo (abrir menú de listas)
        holder.itemView.setOnLongClickListener { 
            onLongClick(song)
            true 
        }
    }

    override fun getItemCount(): Int = songs.size
}
