package com.example.todo.data.model

import com.squareup.moshi.Json

// Resposta genérica da API
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)

// ==================== REQUESTS ====================

data class CreateListRequest(
    val id: String? = null,
    val name: String,
    val color: String = "#3B82F6",
    @Json(name = "created_at") val createdAt: String? = null
)

data class UpdateListRequest(
    val name: String? = null,
    val color: String? = null
)

data class CreateTaskRequest(
    val id: String? = null,
    @Json(name = "list_id") val listId: String,
    val title: String,
    val description: String? = null,
    val completed: Boolean = false,
    val reminder: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val completed: Boolean? = null,
    val reminder: String? = null
)

// ==================== SYNC ====================

data class SyncPushRequest(
    val lists: List<TodoList> = emptyList(),
    val tasks: List<Task> = emptyList(),
    @Json(name = "deleted_lists") val deletedLists: List<String> = emptyList(),
    @Json(name = "deleted_tasks") val deletedTasks: List<String> = emptyList()
)

data class SyncPullRequest(
    @Json(name = "last_sync") val lastSync: String? = null
)

data class SyncPushResponse(
    @Json(name = "synced_lists") val syncedLists: Int,
    @Json(name = "synced_tasks") val syncedTasks: Int,
    @Json(name = "deleted_lists") val deletedLists: Int,
    @Json(name = "deleted_tasks") val deletedTasks: Int,
    @Json(name = "server_time") val serverTime: String
)

data class SyncPullResponse(
    val lists: List<TodoList>,
    val tasks: List<Task>,
    @Json(name = "deleted_lists") val deletedLists: List<String>,
    @Json(name = "deleted_tasks") val deletedTasks: List<String>,
    @Json(name = "server_time") val serverTime: String
)

// ==================== PROFILE UPDATE ====================

data class UpdateUsernameRequest(
    val username: String
)

data class UpdateUsernameResponse(
    val username: String
)

data class UpdateEmailRequest(
    val email: String,
    val password: String
)

data class UpdateEmailResponse(
    val email: String,
    val token: String
)

data class UpdatePasswordRequest(
    @Json(name = "current_password") val currentPassword: String,
    @Json(name = "new_password") val newPassword: String,
    @Json(name = "confirm_password") val confirmPassword: String
)
