use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct TodoList {
    pub id: String,
    pub user_id: String,
    pub name: String,
    pub color: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    #[sqlx(default)]
    pub deleted_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Serialize)]
pub struct ListResponse {
    pub id: String,
    pub user_id: String,
    pub name: String,
    pub color: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

impl From<TodoList> for ListResponse {
    fn from(list: TodoList) -> Self {
        Self {
            id: list.id,
            user_id: list.user_id,
            name: list.name,
            color: list.color,
            created_at: list.created_at,
            updated_at: list.updated_at,
        }
    }
}

#[derive(Debug, Deserialize)]
pub struct CreateListRequest {
    pub id: Option<String>,
    pub name: String,
    pub color: Option<String>,
    pub created_at: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateListRequest {
    pub name: Option<String>,
    pub color: Option<String>,
}
