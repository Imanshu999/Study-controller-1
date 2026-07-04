package com.example.data

import com.example.security.JwtTokenService
import kotlinx.coroutines.flow.Flow

class StudyVideoRepository(private val studyVideoDao: StudyVideoDao) {

    val allVideos: Flow<List<StudyVideo>> = studyVideoDao.getAllVideos()

    suspend fun getVideoById(id: Int): StudyVideo? {
        return studyVideoDao.getVideoById(id)
    }

    suspend fun insertVideoSecured(token: String, video: StudyVideo): Result<Long> {
        val payload = JwtTokenService.validateToken(token)
            ?: return Result.failure(SecurityException("Unauthorized: Invalid or expired security token"))
        
        if (payload.role != "Admin") {
            return Result.failure(SecurityException("Unauthorized: User does not have Admin privileges"))
        }

        return try {
            val id = studyVideoDao.insertVideo(video)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateVideoSecured(token: String, video: StudyVideo): Result<Unit> {
        val payload = JwtTokenService.validateToken(token)
            ?: return Result.failure(SecurityException("Unauthorized: Invalid or expired security token"))
        
        if (payload.role != "Admin") {
            return Result.failure(SecurityException("Unauthorized: User does not have Admin privileges"))
        }

        return try {
            studyVideoDao.updateVideo(video)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVideoSecured(token: String, videoId: Int): Result<Unit> {
        val payload = JwtTokenService.validateToken(token)
            ?: return Result.failure(SecurityException("Unauthorized: Invalid or expired security token"))
        
        if (payload.role != "Admin") {
            return Result.failure(SecurityException("Unauthorized: User does not have Admin privileges"))
        }

        return try {
            studyVideoDao.deleteVideoById(videoId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
