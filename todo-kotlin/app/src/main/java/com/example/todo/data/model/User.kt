package com.example.todo.data.model

import com.squareup.moshi.Json

data class User(
    val id: String,
    val username: String,
    val email: String,
    @Json(name = "created_at") val createdAt: String
)
