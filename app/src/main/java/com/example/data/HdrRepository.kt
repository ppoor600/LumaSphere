package com.example.data

import kotlinx.coroutines.flow.Flow

class HdrRepository(private val dao: HdrProjectDao) {
    val allProjects: Flow<List<HdrProject>> = dao.getAllProjects()

    suspend fun getProjectById(id: Int): HdrProject? = dao.getProjectById(id)

    suspend fun insertProject(project: HdrProject): Long = dao.insertProject(project)

    suspend fun updateProject(project: HdrProject) = dao.updateProject(project)

    suspend fun deleteProject(project: HdrProject) = dao.deleteProject(project)
}
