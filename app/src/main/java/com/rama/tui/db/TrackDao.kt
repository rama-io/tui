package com.rama.tui.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TrackEntity>)

    // SQLite has a limit (~999) on bound parameters in a single IN clause, so callers with
    // large libraries should chunk the path list before calling this (see MusicManager).
    @Query("DELETE FROM tracks WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)
}
