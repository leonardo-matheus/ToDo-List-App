package com.example.todo.data.model

import com.squareup.moshi.Json

data class TodoList(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val name: String,
    val color: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)
