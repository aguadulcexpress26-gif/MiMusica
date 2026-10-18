package com.ejemplo.mimusica

import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.launch

class PlaylistDetailActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var listView: ListView
    private var playlistId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)
        
        playlistId = intent.getIntExtra("PLAYLIST_ID", 0)
        title = intent.getStringExtra("PLAYLIST_NAME") ?: "Lista"

        listView = findViewById(R.id.listPlaylistSongs)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "music-db").build()
        
        loadSongsFromPlaylist()
    }

    private fun loadSongsFromPlaylist() {
        lifecycleScope.launch {
            val songIds = db.playlistDao().getSongIdsForPlaylist(playlistId)
            val songTitles = mutableListOf<String>()
            
            if (songIds.isNotEmpty()) {
                val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
                val selection = "${MediaStore.Audio.Media._ID} IN (${songIds.joinToString(",")})"
                
                contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null)?.use { c ->
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    while (c.moveToNext()) {
                        songTitles.add(c.getString(titleCol) ?: "Sin título")
                    }
                }
            }
            
            val adapter = ArrayAdapter(this@PlaylistDetailActivity, android.R.layout.simple_list_item_1, songTitles)
            listView.adapter = adapter
        }
    }
}
