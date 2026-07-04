package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "study_videos")
data class StudyVideo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val youtubeUrl: String,
    val videoName: String,
    val description: String,
    val thumbnailUrl: String,
    val schoolClass: String, // e.g., Class 10, Class 12
    val subject: String,     // e.g., Mathematics, Physics
    val language: String,    // e.g., English, Hindi, Spanish
    val chapter: String,     // e.g., Chapter 1: Introduction
    val createdAt: Long = System.currentTimeMillis()
) : Serializable
