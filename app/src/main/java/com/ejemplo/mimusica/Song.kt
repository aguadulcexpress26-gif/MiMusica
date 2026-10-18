package com.ejemplo.mimusica

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    var isSelected: Boolean = false // <-- NUEVO
)
