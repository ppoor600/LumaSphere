package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HdrProjectDao {
    @Query("SELECT * FROM hdr_projects ORDER BY createdTimestamp DESC")
    fun getAllProjects(): Flow<List<HdrProject>>

    @Query("SELECT * FROM hdr_projects WHERE id = :id")
    suspend fun getProjectById(id: Int): HdrProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: HdrProject): Long

    @Update
    suspend fun updateProject(project: HdrProject)

    @Delete
    suspend fun deleteProject(project: HdrProject)
}
