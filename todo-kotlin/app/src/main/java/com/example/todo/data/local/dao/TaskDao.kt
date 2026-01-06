package com.example.todo.data.local.dao

import androidx.room.*
import com.example.todo.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    
    @Query("SELECT * FROM tasks WHERE is_deleted = 0 ORDER BY completed ASC, position ASC, created_at DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>
    
    @Query("SELECT * FROM tasks WHERE list_id = :listId AND is_deleted = 0 ORDER BY completed ASC, position ASC, created_at DESC")
    fun getTasksByListId(listId: String): Flow<List<TaskEntity>>
    
    @Query("SELECT * FROM tasks WHERE list_id = :listId AND is_deleted = 0 ORDER BY completed ASC, position ASC, created_at DESC")
    fun getTasksByListIdSync(listId: String): List<TaskEntity>
    
    @Query("SELECT * FROM tasks WHERE id = :id AND is_deleted = 0")
    suspend fun getTaskById(id: String): TaskEntity?
    
    @Query("SELECT * FROM tasks WHERE is_synced = 0 AND is_deleted = 0")
    suspend fun getUnsyncedTasks(): List<TaskEntity>
    
    @Query("SELECT * FROM tasks WHERE is_deleted = 1")
    suspend fun getDeletedTasks(): List<TaskEntity>
    
    @Query("SELECT * FROM tasks WHERE reminder IS NOT NULL AND completed = 0 AND is_deleted = 0")
    suspend fun getTasksWithReminders(): List<TaskEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)
    
    @Update
    suspend fun update(task: TaskEntity)
    
    @Query("UPDATE tasks SET completed = :completed, is_synced = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateCompleted(id: String, completed: Boolean, updatedAt: String)
    
    @Query("UPDATE tasks SET position = :position, is_synced = 0 WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)
    
    @Query("UPDATE tasks SET is_deleted = 1, is_synced = 0 WHERE id = :id")
    suspend fun markAsDeleted(id: String)
    
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM tasks WHERE is_deleted = 1 AND is_synced = 1")
    suspend fun deleteAllSyncedDeleted()
    
    @Query("UPDATE tasks SET is_synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
    
    @Query("DELETE FROM tasks WHERE list_id = :listId")
    suspend fun deleteByListId(listId: String)
    
    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM tasks WHERE list_id = :listId AND is_deleted = 0")
    suspend fun getTaskCountByList(listId: String): Int
    
    @Query("SELECT COUNT(*) FROM tasks WHERE list_id = :listId AND completed = 1 AND is_deleted = 0")
    suspend fun getCompletedTaskCountByList(listId: String): Int
}
