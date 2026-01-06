package com.example.todo.data.api

import com.example.todo.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface TodoApi {

    // ==================== AUTH ====================
    
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("auth/verify")
    suspend fun verifyEmail(@Body request: VerifyEmailRequest): Response<AuthResponse>
    
    @POST("auth/resend-code")
    suspend fun resendCode(@Body request: ResendCodeRequest): Response<ApiResponse<Any?>>
    
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiResponse<Any?>>
    
    @POST("auth/verify-reset-code")
    suspend fun verifyResetCode(@Body request: VerifyResetCodeRequest): Response<ApiResponse<ResetTokenResponse>>
    
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<Any?>>
    
    @GET("auth/me")
    suspend fun getMe(): Response<ApiResponse<User>>
    
    @PUT("auth/update-username")
    suspend fun updateUsername(@Body request: UpdateUsernameRequest): Response<ApiResponse<UpdateUsernameResponse>>
    
    @PUT("auth/update-email")
    suspend fun updateEmail(@Body request: UpdateEmailRequest): Response<ApiResponse<UpdateEmailResponse>>
    
    @PUT("auth/update-password")
    suspend fun updatePassword(@Body request: UpdatePasswordRequest): Response<ApiResponse<Any?>>

    // ==================== LISTS ====================
    
    @GET("lists")
    suspend fun getLists(): Response<ApiResponse<List<TodoList>>>

    @GET("lists/{id}")
    suspend fun getList(@Path("id") id: String): Response<ApiResponse<TodoList>>
    
    @POST("lists")
    suspend fun createList(@Body request: CreateListRequest): Response<ApiResponse<TodoList>>
    
    @PUT("lists/{id}")
    suspend fun updateList(
        @Path("id") id: String,
        @Body request: UpdateListRequest
    ): Response<ApiResponse<TodoList>>
    
    @DELETE("lists/{id}")
    suspend fun deleteList(@Path("id") id: String): Response<ApiResponse<Any?>>

    @GET("lists/{id}/tasks")
    suspend fun getTasksByList(@Path("id") id: String): Response<ApiResponse<List<Task>>>

    // ==================== TASKS ====================
    
    @GET("tasks")
    suspend fun getTasks(): Response<ApiResponse<List<Task>>>

    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Response<ApiResponse<Task>>
    
    @POST("tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): Response<ApiResponse<Task>>
    
    @PUT("tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body request: UpdateTaskRequest
    ): Response<ApiResponse<Task>>
    
    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<ApiResponse<Any?>>
    
    // ==================== SYNC ====================
    
    @POST("sync/push")
    suspend fun syncPush(@Body request: SyncPushRequest): Response<ApiResponse<SyncPushResponse>>
    
    @POST("sync/pull")
    suspend fun syncPull(@Body request: SyncPullRequest): Response<ApiResponse<SyncPullResponse>>
}

