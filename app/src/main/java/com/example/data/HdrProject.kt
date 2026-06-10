package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hdr_projects")
data class HdrProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val bracketLevel: String = "Medium", // "Low" (3x), "Medium" (5x), "High" (7-9x)
    val capturedCount: Int = 0,
    val totalPointsCount: Int = 18,
    val expFilepath: String? = null,
    val isCompleted: Boolean = false,
    val dynamicRangeDb: Float = 12.5f // Estimated dynamic range of the final map in decibels / stops
)
