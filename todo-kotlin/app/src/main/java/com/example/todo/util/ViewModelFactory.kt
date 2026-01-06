package com.example.todo.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.todo.data.repository.TodoRepository
import com.example.todo.ui.auth.AuthViewModel
import com.example.todo.ui.lists.ListsViewModel
import com.example.todo.ui.tasks.TasksViewModel

class ViewModelFactory(
    private val repository: TodoRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(repository) as T
            }
            modelClass.isAssignableFrom(ListsViewModel::class.java) -> {
                ListsViewModel(repository) as T
            }
            modelClass.isAssignableFrom(TasksViewModel::class.java) -> {
                TasksViewModel(repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
