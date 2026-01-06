package com.example.todo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.todo.data.local.dao.TaskDao
import com.example.todo.data.local.dao.TodoListDao
import com.example.todo.data.local.dao.UserDao
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        TodoListEntity::class,
        TaskEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class TodoDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun todoListDao(): TodoListDao
    abstract fun taskDao(): TaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: TodoDatabase? = null
        
        fun getInstance(context: Context): TodoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TodoDatabase::class.java,
                    "todo_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
