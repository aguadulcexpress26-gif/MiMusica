package com.ejemplo.mimusica

import android.app.Activity
import android.content.Intent
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
    private var songIdsList: List<Long> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)
        
        playlistId = intent.getIntExtra("PLAYLIST_ID", 0)
        title = intent.getStringExtra("PLAYLIST_NAME") ?: "Lista"

        listView = findViewById(R.id.listPlaylistSongs)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "music-db").build()

        // Al tocar una canción, devolvemos el ID a MainActivity y cerramos esta pantalla
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedSongId = songIdsList[position]
            val returnIntent = Intent()
            returnIntent.putExtra("SELECTED_SONG_ID", selectedSongId)
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
        
        loadSongsFromPlaylist()
    }

    private fun loadSongsFromPlaylist() {
        lifecycleScope.launch {
            songIdsList = db.playlistDao().getSongIdsForPlaylist(playlistId)
            val songTitles = mutableListOf<String>()
            
            if (songIdsList.isNotEmpty()) {
                val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
                val selection = "${MediaStore.Audio.Media._ID} IN (${songIdsList.joinToString(",")})"
                
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
