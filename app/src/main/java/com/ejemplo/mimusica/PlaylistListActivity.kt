package com.ejemplo.mimusica

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.launch

class PlaylistListActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var listView: ListView
    private var playlists: List<Playlist> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_list)
        title = "Mis Listas"

        listView = findViewById(R.id.listPlaylists)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "music-db").build()

        listView.setOnItemClickListener { _, _, position, _ ->
            val playlist = playlists[position]
            val intent = Intent(this, PlaylistDetailActivity::class.java)
            intent.putExtra("PLAYLIST_ID", playlist.id)
            intent.putExtra("PLAYLIST_NAME", playlist.name)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadPlaylists()
    }

    private fun loadPlaylists() {
        lifecycleScope.launch {
            playlists = db.playlistDao().getAllPlaylists()
            val names = playlists.map { it.name }
            val adapter = ArrayAdapter(this@PlaylistListActivity, android.R.layout.simple_list_item_1, names)
            listView.adapter = adapter
        }
    }
}
