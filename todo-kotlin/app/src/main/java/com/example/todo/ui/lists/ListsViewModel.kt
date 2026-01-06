package com.example.todo.ui.lists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.data.repository.TodoRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ListsViewModel(private val repository: TodoRepository) : ViewModel() {
    
    private val _lists = MutableLiveData<List<ListsAdapter.ListWithCount>>()
    val lists: LiveData<List<ListsAdapter.ListWithCount>> = _lists
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _syncStatus = MutableLiveData<SyncStatus>()
    val syncStatus: LiveData<SyncStatus> = _syncStatus
    
    init {
        loadLists()
    }
    
    private fun loadLists() {
        viewModelScope.launch {
            repository.getAllLists().collectLatest { listEntities ->
                val listsWithCount = listEntities.map { list ->
                    ListsAdapter.ListWithCount(
                        list = list,
                        taskCount = repository.getTaskCountByList(list.id),
                        completedCount = repository.getCompletedTaskCountByList(list.id)
                    )
                }
                _lists.value = listsWithCount
            }
        }
    }
    
    fun createList(name: String, color: String, reminder: String? = null, isPersistent: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.createList(name, color, reminder, isPersistent)
            _isLoading.value = false
            
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }
    
    fun updateList(id: String, name: String?, color: String?, reminder: String? = null, isPersistent: Boolean? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.updateList(id, name, color, reminder, isPersistent)
            _isLoading.value = false
            
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }
    
    fun deleteList(list: TodoListEntity) {
        viewModelScope.launch {
            val result = repository.deleteList(list.id)
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }
    
    fun sync() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.Syncing
            val result = repository.syncWithServer()
            _syncStatus.value = if (result.isSuccess) {
                SyncStatus.Success
            } else {
                SyncStatus.Error(result.exceptionOrNull()?.message ?: "Erro na sincronização")
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        object Success : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }
}
