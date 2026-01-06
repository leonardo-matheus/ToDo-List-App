package com.example.todo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    
    val username: String,
    
    val email: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String
)
