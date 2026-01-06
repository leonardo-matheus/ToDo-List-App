use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct User {
    pub id: String,
    pub username: String,
    pub email: String,
    #[serde(skip_serializing)]
    pub password_hash: String,
    pub is_verified: bool,
    #[serde(skip_serializing)]
    pub verification_code: Option<String>,
    #[serde(skip_serializing)]
    pub code_expires_at: Option<DateTime<Utc>>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Serialize)]
pub struct UserPublic {
    pub id: String,
    pub username: String,
    pub email: String,
    pub created_at: DateTime<Utc>,
}

impl From<User> for UserPublic {
    fn from(user: User) -> Self {
        Self {
            id: user.id,
            username: user.username,
            email: user.email,
            created_at: user.created_at,
        }
    }
}

#[derive(Debug, Deserialize)]
pub struct RegisterRequest {
    pub username: String,
    pub email: String,
    pub password: String,
}

#[derive(Debug, Deserialize)]
pub struct VerifyEmailRequest {
    pub email: String,
    pub code: String,
}

#[derive(Debug, Deserialize)]
pub struct ResendCodeRequest {
    pub email: String,
}

#[derive(Debug, Deserialize)]
pub struct ForgotPasswordRequest {
    pub email: String,
}

#[derive(Debug, Deserialize)]
pub struct VerifyResetCodeRequest {
    pub email: String,
    pub code: String,
}

#[derive(Debug, Deserialize)]
pub struct ResetPasswordRequest {
    pub email: String,
    pub reset_token: String,
    pub new_password: String,
    pub confirm_password: String,
}

#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub email: String,
    pub password: String,
}

#[derive(Debug, Serialize)]
pub struct LoginResponse {
    pub user: UserPublic,
    pub token: String,
}

#[derive(Debug, Serialize)]
pub struct RegisterResponse {
    pub email: String,
    pub requires_verification: bool,
}

#[derive(Debug, Serialize)]
pub struct VerifyResponse {
    pub user: UserPublic,
    pub token: String,
}

#[derive(Debug, Serialize)]
pub struct ForgotPasswordResponse {
    pub email: String,
}

#[derive(Debug, Serialize)]
pub struct VerifyResetCodeResponse {
    pub reset_token: String,
}

#[derive(Debug, Deserialize)]
pub struct UpdateUsernameRequest {
    pub username: String,
}

#[derive(Debug, Serialize)]
pub struct UpdateUsernameResponse {
    pub username: String,
}

#[derive(Debug, Deserialize)]
pub struct UpdateEmailRequest {
    pub email: String,
    pub password: String,
}

#[derive(Debug, Serialize)]
pub struct UpdateEmailResponse {
    pub email: String,
    pub token: String,
}

#[derive(Debug, Deserialize)]
pub struct UpdatePasswordRequest {
    pub current_password: String,
    pub new_password: String,
    pub confirm_password: String,
}
