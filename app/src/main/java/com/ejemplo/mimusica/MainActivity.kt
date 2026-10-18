package com.ejemplo.mimusica

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : AppCompatActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private lateinit var recycler: RecyclerView
    private lateinit var txtNowPlaying: TextView
    private lateinit var btnPlayPause: Button
    private var songs: List<Song> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadSongs()
            connectController()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recyclerSongs)
        txtNowPlaying = findViewById(R.id.txtNowPlaying)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        recycler.layoutManager = LinearLayoutManager(this)

        // Lógica de los botones
        findViewById<Button>(R.id.btnPlayPause).setOnClickListener {
            if (controller?.isPlaying == true) {
                controller?.pause()
            } else {
                controller?.play()
            }
        }

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            controller?.seekToNext()
        }

        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            controller?.seekToPrevious()
        }

        val permiso = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permiso)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadSongs()
            connectController()
        } else {
            permissionLauncher.launch(permiso)
        }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            val items = songs.map { MediaItem.fromUri(it.uri) }
            controller?.setMediaItems(items)
            controller?.prepare()
            
            // Actualizar texto y botón cuando cambie la canción o el estado
            controller?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateUI()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateUI()
                }
            })

            songs.firstOrNull()?.let {
                txtNowPlaying.text = "▶ ${it.title} — ${it.artist}"
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateUI() {
        val index = controller?.currentMediaItemIndex ?: 0
        if (index in songs.indices) {
            val song = songs[index]
            val estado = if (controller?.isPlaying == true) "▶" else "⏸"
            txtNowPlaying.text = "$estado ${song.title} — ${song.artist}"
            btnPlayPause.text = if (controller?.isPlaying == true) "⏸" else "▶️"
        }
    }

    private fun loadSongs() {
        val list = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "Sin título"
                val artist = c.getString(artistCol) ?: "Desconocido"
                val uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                list.add(Song(id, title, artist, uri))
            }
        }
        songs = list

        recycler.adapter = SongAdapter(songs) { song ->
            val index = songs.indexOf(song)
            controller?.seekTo(index, 0)
            controller?.play()
        }
    }

    override fun onDestroy() {
        controllerFuture.addListener({
            MediaController.releaseFuture(controllerFuture)
        }, MoreExecutors.directExecutor())
        super.onDestroy()
    }
}
