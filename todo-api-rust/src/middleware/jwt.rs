use actix_web::HttpRequest;
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};

use crate::config::Config;
use crate::errors::ApiError;
use crate::models::Claims;

pub fn create_token(config: &Config, user_id: &str, email: &str) -> Result<String, ApiError> {
    let claims = Claims::new(
        user_id.to_string(),
        email.to_string(),
        config.jwt_expiration,
    );

    encode(
        &Header::default(),
        &claims,
        &EncodingKey::from_secret(config.jwt_secret.as_bytes()),
    )
    .map_err(|_| ApiError::internal("Failed to create token"))
}

pub fn verify_token(config: &Config, token: &str) -> Result<Claims, ApiError> {
    decode::<Claims>(
        token,
        &DecodingKey::from_secret(config.jwt_secret.as_bytes()),
        &Validation::default(),
    )
    .map(|data| data.claims)
    .map_err(|_| ApiError::unauthorized("Token inválido ou expirado"))
}

pub fn extract_token(req: &HttpRequest) -> Option<String> {
    req.headers()
        .get("Authorization")
        .and_then(|h| h.to_str().ok())
        .and_then(|h| {
            if h.starts_with("Bearer ") {
                Some(h[7..].to_string())
            } else {
                None
            }
        })
}

pub fn get_auth_user(req: &HttpRequest, config: &Config) -> Result<Claims, ApiError> {
    let token = extract_token(req).ok_or_else(|| ApiError::unauthorized("Token não fornecido"))?;
    verify_token(config, &token)
}
