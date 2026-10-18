package com.ejemplo.mimusica

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylists(): List<Playlist>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Insert
    suspend fun insertPlaylistSong(playlistSong: PlaylistSong)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongIdsForPlaylist(playlistId: Int): List<Long>
}

@Database(entities = [Playlist::class, PlaylistSong::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
}
