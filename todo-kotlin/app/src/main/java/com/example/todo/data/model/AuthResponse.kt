package com.example.todo.data.model

import com.squareup.moshi.Json

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val data: AuthData?
)

data class AuthData(
    val user: User? = null,
    val token: String? = null,
    val email: String? = null,
    @Json(name = "requires_verification") val requiresVerification: Boolean = false
)

data class VerifyEmailRequest(
    val email: String,
    val code: String
)

data class ResendCodeRequest(
    val email: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class VerifyResetCodeRequest(
    val email: String,
    val code: String
)

data class ResetTokenResponse(
    @Json(name = "reset_token") val resetToken: String
)

data class ResetPasswordRequest(
    val email: String,
    @Json(name = "reset_token") val resetToken: String,
    @Json(name = "new_password") val newPassword: String,
    @Json(name = "confirm_password") val confirmPassword: String
)
