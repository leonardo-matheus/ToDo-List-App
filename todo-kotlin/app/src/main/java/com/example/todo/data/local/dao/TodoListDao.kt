package com.example.todo.data.local.dao

import androidx.room.*
import com.example.todo.data.local.entity.TodoListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoListDao {
    
    @Query("SELECT * FROM todo_lists WHERE is_deleted = 0 ORDER BY created_at DESC")
    fun getAllLists(): Flow<List<TodoListEntity>>
    
    @Query("SELECT * FROM todo_lists WHERE id = :id AND is_deleted = 0")
    suspend fun getListById(id: String): TodoListEntity?
    
    @Query("SELECT * FROM todo_lists WHERE is_synced = 0 AND is_deleted = 0")
    suspend fun getUnsyncedLists(): List<TodoListEntity>
    
    @Query("SELECT * FROM todo_lists WHERE is_deleted = 1")
    suspend fun getDeletedLists(): List<TodoListEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: TodoListEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lists: List<TodoListEntity>)
    
    @Update
    suspend fun update(list: TodoListEntity)
    
    @Query("UPDATE todo_lists SET is_deleted = 1, is_synced = 0 WHERE id = :id")
    suspend fun markAsDeleted(id: String)
    
    @Query("DELETE FROM todo_lists WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM todo_lists WHERE is_deleted = 1 AND is_synced = 1")
    suspend fun deleteAllSyncedDeleted()
    
    @Query("UPDATE todo_lists SET is_synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
    
    @Query("DELETE FROM todo_lists")
    suspend fun deleteAll()
}
