package com.example.todo.data.model

import com.squareup.moshi.Json

data class Task(
    val id: String,
    @Json(name = "list_id") val listId: String,
    val title: String,
    val description: String?,
    val completed: Boolean,
    val reminder: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)
