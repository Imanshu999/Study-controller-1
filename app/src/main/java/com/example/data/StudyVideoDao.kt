package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyVideoDao {
    @Query("SELECT * FROM study_videos ORDER BY createdAt DESC")
    fun getAllVideos(): Flow<List<StudyVideo>>

    @Query("SELECT * FROM study_videos WHERE id = :id")
    suspend fun getVideoById(id: Int): StudyVideo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: StudyVideo): Long

    @Update
    suspend fun updateVideo(video: StudyVideo)

    @Delete
    suspend fun deleteVideo(video: StudyVideo)

    @Query("DELETE FROM study_videos WHERE id = :id")
    suspend fun deleteVideoById(id: Int)
}
