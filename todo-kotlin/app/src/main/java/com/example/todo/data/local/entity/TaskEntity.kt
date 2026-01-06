package com.example.todo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TodoListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("list_id")]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "list_id")
    val listId: String,
    
    val title: String,
    
    val description: String? = null,
    
    val completed: Boolean = false,
    
    val reminder: String? = null,
    
    @ColumnInfo(name = "position", defaultValue = "0")
    val position: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
