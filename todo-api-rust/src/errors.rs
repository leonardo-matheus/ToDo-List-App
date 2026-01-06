use actix_web::{HttpResponse, http::StatusCode};
use serde::Serialize;
use std::fmt;

#[derive(Debug)]
pub struct ApiError {
    pub message: String,
    pub status: StatusCode,
}

impl ApiError {
    pub fn new(message: impl Into<String>, status: StatusCode) -> Self {
        Self {
            message: message.into(),
            status,
        }
    }

    pub fn bad_request(message: impl Into<String>) -> Self {
        Self::new(message, StatusCode::BAD_REQUEST)
    }

    pub fn unauthorized(message: impl Into<String>) -> Self {
        Self::new(message, StatusCode::UNAUTHORIZED)
    }

    pub fn forbidden(message: impl Into<String>) -> Self {
        Self::new(message, StatusCode::FORBIDDEN)
    }

    pub fn not_found(message: impl Into<String>) -> Self {
        Self::new(message, StatusCode::NOT_FOUND)
    }

    pub fn conflict(message: impl Into<String>) -> Self {
        Self::new(message, StatusCode::CONFLICT)
    }

    pub fn internal(message: impl Into<String>) -> Self {
        Self::new(message, StatusCode::INTERNAL_SERVER_ERROR)
    }
}

impl fmt::Display for ApiError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl actix_web::error::ResponseError for ApiError {
    fn error_response(&self) -> HttpResponse {
        let response = ApiResponse::<()>::error(&self.message);
        HttpResponse::build(self.status).json(response)
    }
}

#[derive(Serialize)]
pub struct ApiResponse<T: Serialize> {
    pub success: bool,
    pub message: String,
    pub data: Option<T>,
    pub timestamp: String,
}

impl<T: Serialize> ApiResponse<T> {
    pub fn success(message: impl Into<String>, data: T) -> Self {
        Self {
            success: true,
            message: message.into(),
            data: Some(data),
            timestamp: chrono::Utc::now().to_rfc3339(),
        }
    }

    pub fn success_no_data(message: impl Into<String>) -> ApiResponse<()> {
        ApiResponse {
            success: true,
            message: message.into(),
            data: None,
            timestamp: chrono::Utc::now().to_rfc3339(),
        }
    }

    pub fn error(message: impl Into<String>) -> ApiResponse<()> {
        ApiResponse {
            success: false,
            message: message.into(),
            data: None,
            timestamp: chrono::Utc::now().to_rfc3339(),
        }
    }
}
