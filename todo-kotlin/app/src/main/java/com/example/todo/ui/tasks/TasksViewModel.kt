package com.example.todo.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.data.repository.TodoRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TasksViewModel(
    private val repository: TodoRepository
) : ViewModel() {
    
    private val _tasks = MutableLiveData<List<TaskEntity>>()
    val tasks: LiveData<List<TaskEntity>> = _tasks
    
    private val _list = MutableLiveData<TodoListEntity?>()
    val list: LiveData<TodoListEntity?> = _list
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private var currentListId: String = ""
    
    fun loadTasks(listId: String) {
        currentListId = listId
        viewModelScope.launch {
            repository.getListById(listId)?.let {
                _list.value = it
            }
        }
        viewModelScope.launch {
            repository.getTasksByList(listId).collectLatest { taskList ->
                _tasks.value = taskList
            }
        }
    }
    
    fun createTask(listId: String, title: String, description: String?, reminderTime: Long?) {
        viewModelScope.launch {
            _isLoading.value = true
            val reminder = reminderTime?.toString()
            val result = repository.createTask(listId, title, description, reminder)
            _isLoading.value = false
            
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }
    
    fun updateTask(id: String, title: String?, description: String?, reminder: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.updateTask(id, title, description, null, reminder)
            _isLoading.value = false
            
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }
    
    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            val result = repository.toggleTaskCompleted(task.id)
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }
    
    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            val result = repository.deleteTask(task.id)
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }
    
    fun updateTasksOrder(tasks: List<TaskEntity>) {
        viewModelScope.launch {
            repository.updateTasksOrder(tasks)
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}
