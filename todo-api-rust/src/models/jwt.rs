use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    pub user_id: String,
    pub email: String,
    pub exp: i64,
    pub iat: i64,
}

impl Claims {
    pub fn new(user_id: String, email: String, expiration_seconds: i64) -> Self {
        let now = chrono::Utc::now().timestamp();
        Self {
            user_id,
            email,
            exp: now + expiration_seconds,
            iat: now,
        }
    }
}
