package com.example.data.repository

import com.example.data.local.dao.TaskDao
import com.example.data.local.entity.StudentTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class TaskRepository(private val taskDao: TaskDao) {

    fun getTasksForGroup(groupName: String): Flow<List<StudentTaskEntity>> {
        return taskDao.getTasksForGroup(groupName).flowOn(Dispatchers.IO)
    }

    fun getTasksForGroupAndSubject(groupName: String, subjectName: String): Flow<List<StudentTaskEntity>> {
        return taskDao.getTasksForGroupAndSubject(groupName, subjectName).flowOn(Dispatchers.IO)
    }

    fun getPendingTasksForGroup(groupName: String): Flow<List<StudentTaskEntity>> {
        return taskDao.getPendingTasksForGroup(groupName).flowOn(Dispatchers.IO)
    }

    suspend fun getTaskById(id: Long): StudentTaskEntity? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(id)
    }

    suspend fun addTask(task: StudentTaskEntity): Long = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: StudentTaskEntity) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task)
    }

    suspend fun toggleTaskCompleted(id: Long, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        taskDao.setTaskCompleted(id, isCompleted)
    }

    suspend fun deleteTask(task: StudentTaskEntity) = withContext(Dispatchers.IO) {
        taskDao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: Long) = withContext(Dispatchers.IO) {
        taskDao.deleteTaskById(id)
    }
}
