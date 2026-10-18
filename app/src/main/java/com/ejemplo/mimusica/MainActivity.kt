package com.ejemplo.mimusica

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
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
    private lateinit var editSearch: EditText
    private lateinit var btnAddSelected: Button
    
    private var isSelectionMode = false
    private val selectedSongs = mutableListOf<Song>()
    
    private var allSongs: List<Song> = emptyList()
    private var filteredSongs: List<Song> = emptyList()
    
    private lateinit var db: AppDatabase

    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private val updateProgressTask = object : Runnable {
        override fun run() {
            if (!isUserSeeking) updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    // RECIBE LA ORDEN DE REPRODUCIR DESDE LA LISTA
    private val playSongReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val songId = intent?.getLongExtra("SONG_ID", -1L) ?: -1L
            if (songId != -1L) {
                val songIndex = allSongs.indexOfFirst { it.id == songId }
                if (songIndex != -1) {
                    controller?.seekTo(songIndex, 0)
                    controller?.play()
                }
            }
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

    private val playPlaylistLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Ya no hacemos nada aquí porque usamos el BroadcastReceiver
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "music-db").build()

        recycler = findViewById(R.id.recyclerSongs)
        txtNowPlaying = findViewById(R.id.txtNowPlaying)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        seekBar = findViewById(R.id.seekBar)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)
        editSearch = findViewById(R.id.editSearch)
        btnAddSelected = findViewById(R.id.btnAddSelected)
        recycler.layoutManager = LinearLayoutManager(this)

        // Registrar el receptor de la lista
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(playSongReceiver, IntentFilter("PLAY_SONG_FROM_PLAYLIST"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playSongReceiver, IntentFilter("PLAY_SONG_FROM_PLAYLIST"))
        }

        btnPlayPause.setOnClickListener {
            if (controller?.isPlaying == true) controller?.pause() else controller?.play()
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener { controller?.seekToNext() }
        findViewById<Button>(R.id.btnPrev).setOnClickListener { controller?.seekToPrevious() }

        findViewById<Button>(R.id.btnCreatePlaylist).setOnClickListener { showCreatePlaylistDialog() }
        
        findViewById<Button>(R.id.btnViewPlaylists).setOnClickListener {
            val intent = Intent(this, PlaylistListActivity::class.java)
            playPlaylistLauncher.launch(intent)
        }

        btnAddSelected.setOnClickListener {
            if (selectedSongs.isNotEmpty()) {
                showAddMultipleToPlaylistDialog(selectedSongs)
            }
        }

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) txtCurrentTime.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                controller?.seekTo(seekBar?.progress?.toLong() ?: 0)
                isUserSeeking = false
            }
        })

        val permiso = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
            connectController()
        } else {
            permissionLauncher.launch(permiso)
        }
    }

    private fun filter(query: String) {
        filteredSongs = if (query.isEmpty()) {
            allSongs
        } else {
            allSongs.filter { 
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) 
            }
        }
        updateAdapter()
    }

    private fun updateAdapter() {
        recycler.adapter = SongAdapter(filteredSongs, isSelectionMode,
            onClick = { song ->
                if (isSelectionMode) {
                    toggleSelection(song)
                } else {
                    val index = allSongs.indexOf(song)
                    controller?.seekTo(index, 0)
                    controller?.play()
                }
            },
            onLongClick = { song ->
                if (!isSelectionMode) {
                    enterSelectionMode(song)
                }
            }
        )
    }

    private fun enterSelectionMode(song: Song) {
        isSelectionMode = true
        selectedSongs.clear()
        song.isSelected = true
        selectedSongs.add(song)
        updateAddSelectedButton()
        
        // Solo actualizamos la fila seleccionada
        val position = filteredSongs.indexOf(song)
        if (position != -1 && recycler.adapter is SongAdapter) {
            (recycler.adapter as SongAdapter).updateSingleItem(position)
        }
    }

    private fun toggleSelection(song: Song) {
        if (song.isSelected) {
            song.isSelected = false
            selectedSongs.remove(song)
        } else {
            song.isSelected = true
            selectedSongs.add(song)
        }

        if (selectedSongs.isEmpty()) {
            isSelectionMode = false
        }
        updateAddSelectedButton()
        
        // Solo actualizamos la fila tocada
        val position = filteredSongs.indexOf(song)
        if (position != -1 && recycler.adapter is SongAdapter) {
            (recycler.adapter as SongAdapter).updateSingleItem(position)
        }
    }

    private fun updateAddSelectedButton() {
        if (isSelectionMode && selectedSongs.isNotEmpty()) {
            btnAddSelected.visibility = View.VISIBLE
            btnAddSelected.text = "📋 Añadir ${selectedSongs.size} canciones a lista"
        } else {
            btnAddSelected.visibility = View.GONE
        }
    }

    private fun showAddMultipleToPlaylistDialog(songsToAdd: List<Song>) {
        lifecycleScope.launch {
            val playlists = db.playlistDao().getAllPlaylists()
            if (playlists.isEmpty()) {
                Toast.makeText(this@MainActivity, "Primero crea una lista", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = playlists.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Añadir ${songsToAdd.size} canciones a:")
                .setItems(names) { _, which ->
                    val selectedPlaylist = playlists[which]
                    lifecycleScope.launch {
                        for (song in songsToAdd) {
                            db.playlistDao().insertPlaylistSong(
                                PlaylistSong(playlistId = selectedPlaylist.id, songId = song.id)
                            )
                        }
                        Toast.makeText(this@MainActivity, "${songsToAdd.size} canciones añadidas a '${selectedPlaylist.name}'", Toast.LENGTH_SHORT).show()
                        exitSelectionMode()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun exitSelectionMode() {
        for (song in allSongs) { song.isSelected = false }
        selectedSongs.clear()
        isSelectionMode = false
        updateAddSelectedButton()
        updateAdapter() // Aquí sí recargamos todo para limpiar los colores
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Nueva Lista de Reproducción")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        db.playlistDao().insertPlaylist(Playlist(name = name))
                        Toast.makeText(this@MainActivity, "Lista '$name' creada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            val items = allSongs.map { MediaItem.fromUri(it.uri) }
            controller?.setMediaItems(items)
            controller?.prepare()

            controller?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateUI() }
                override fun onIsPlayingChanged(isPlaying: Boolean) { updateUI() }
                override fun onPlaybackStateChanged(playbackState: Int) { updateUI() }
            })

            allSongs.firstOrNull()?.let { txtNowPlaying.text = "▶ ${it.title} — ${it.artist}" }
            handler.post(updateProgressTask)
        }, MoreExecutors.directExecutor())
    }

    private fun updateUI() {
        val index = controller?.currentMediaItemIndex ?: 0
        if (index in allSongs.indices) {
            val song = allSongs[index]
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
            seekBar.max = 100; seekBar.progress = 0
            txtTotalTime.text = "00:00"; txtCurrentTime.text = "00:00"
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun loadSongs() {
        val list = mutableListOf<Song>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "Sin título"
                val artist = c.getString(artistCol) ?: "Desconocido"
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                list.add(Song(id, title, artist, uri))
            }
        }
        allSongs = list
        filteredSongs = list
        updateAdapter()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(playSongReceiver)
        } catch (e: Exception) {}
        handler.removeCallbacks(updateProgressTask)
        controllerFuture.addListener({ MediaController.releaseFuture(controllerFuture) }, MoreExecutors.directExecutor())
        super.onDestroy()
    }
}
