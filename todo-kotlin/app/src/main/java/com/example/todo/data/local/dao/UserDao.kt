package com.example.todo.data.local.dao

import androidx.room.*
import com.example.todo.data.local.entity.UserEntity

@Dao
interface UserDao {
    
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUser(): UserEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)
    
    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
