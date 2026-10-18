package com.ejemplo.mimusica

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.SeekBar
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
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private lateinit var recycler: RecyclerView
    private lateinit var txtNowPlaying: TextView
    private lateinit var btnPlayPause: Button
    private lateinit var seekBar: SeekBar
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private var songs: List<Song> = emptyList()

    // Variables para la barra de progreso
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private val updateProgressTask = object : Runnable {
        override fun run() {
            if (!isUserSeeking) {
                updateProgress()
            }
            handler.postDelayed(this, 500) // Actualizar cada medio segundo
        }
    }

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
        seekBar = findViewById(R.id.seekBar)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)
        recycler.layoutManager = LinearLayoutManager(this)

        // Lógica de los botones
        btnPlayPause.setOnClickListener {
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

        // Lógica de arrastrar la barra de progreso
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    txtCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                controller?.seekTo(seekBar?.progress?.toLong() ?: 0)
                isUserSeeking = false
            }
        })

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

            controller?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateUI()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateUI()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateUI()
                }
            })

            songs.firstOrNull()?.let {
                txtNowPlaying.text = "▶ ${it.title} — ${it.artist}"
            }

            // Iniciar el bucle que actualiza la barra
            handler.post(updateProgressTask)

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

    private fun updateProgress() {
        val c = controller ?: return
        val duration = c.duration
        val current = c.currentPosition

        if (duration > 0) {
            seekBar.max = duration.toInt()
            seekBar.progress = current.toInt()
            txtTotalTime.text = formatTime(duration)
            txtCurrentTime.text = formatTime(current)
        } else {
            seekBar.max = 100
            seekBar.progress = 0
            txtTotalTime.text = "00:00"
            txtCurrentTime.text = "00:00"
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
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
        handler.removeCallbacks(updateProgressTask)
        controllerFuture.addListener({
            MediaController.releaseFuture(controllerFuture)
        }, MoreExecutors.directExecutor())
        super.onDestroy()
    }
}
