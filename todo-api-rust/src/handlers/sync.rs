use actix_web::{web, HttpRequest, HttpResponse};
use chrono::{DateTime, Utc};
use sqlx::MySqlPool;

use crate::config::Config;
use crate::errors::{ApiError, ApiResponse};
use crate::middleware::jwt::get_auth_user;
use crate::models::*;

// Helper: Generate UUID
fn generate_uuid() -> String {
    uuid::Uuid::new_v4().to_string()
}

// POST /sync/push
pub async fn sync_push(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<SyncPushRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let user_id = &claims.user_id;

    let mut synced_lists = 0;
    let mut synced_tasks = 0;

    // Start transaction
    let mut tx = pool.begin().await
        .map_err(|e| ApiError::internal(format!("Transaction error: {}", e)))?;

    // Process deleted lists
    for list_id in &body.deleted_lists {
        sqlx::query("UPDATE todo_lists SET deleted_at = NOW() WHERE id = ? AND user_id = ?")
            .bind(list_id)
            .bind(user_id)
            .execute(&mut *tx)
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        sqlx::query("UPDATE tasks SET deleted_at = NOW() WHERE list_id = ?")
            .bind(list_id)
            .execute(&mut *tx)
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
    }

    // Process deleted tasks
    for task_id in &body.deleted_tasks {
        sqlx::query(
            r#"
            UPDATE tasks t
            JOIN todo_lists l ON t.list_id = l.id
            SET t.deleted_at = NOW() 
            WHERE t.id = ? AND l.user_id = ?
            "#
        )
        .bind(task_id)
        .bind(user_id)
        .execute(&mut *tx)
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
    }

    // Process lists
    for list in &body.lists {
        let id = list.id.clone().unwrap_or_else(generate_uuid);
        let name = list.name.trim();
        let color = list.color.clone().unwrap_or_else(|| "#3B82F6".to_string());
        let created_at = list.created_at.clone().unwrap_or_else(|| Utc::now().to_rfc3339());

        // Check if exists
        let existing: Option<(String,)> = sqlx::query_as("SELECT id FROM todo_lists WHERE id = ?")
            .bind(&id)
            .fetch_optional(&mut *tx)
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        if existing.is_some() {
            sqlx::query(
                "UPDATE todo_lists SET name = ?, color = ?, updated_at = NOW(), deleted_at = NULL WHERE id = ? AND user_id = ?"
            )
            .bind(name)
            .bind(&color)
            .bind(&id)
            .bind(user_id)
            .execute(&mut *tx)
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
        } else {
            sqlx::query(
                "INSERT INTO todo_lists (id, user_id, name, color, created_at) VALUES (?, ?, ?, ?, ?)"
            )
            .bind(&id)
            .bind(user_id)
            .bind(name)
            .bind(&color)
            .bind(&created_at)
            .execute(&mut *tx)
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
        }

        synced_lists += 1;
    }

    // Process tasks
    for task in &body.tasks {
        let id = task.id.clone().unwrap_or_else(generate_uuid);
        let list_id = &task.list_id;
        let title = task.title.trim();
        let description = task.description.clone().unwrap_or_default();
        let completed = task.completed.unwrap_or(false);
        let reminder: Option<DateTime<Utc>> = task.reminder.as_ref().and_then(|r| r.parse().ok());
        let created_at = task.created_at.clone().unwrap_or_else(|| Utc::now().to_rfc3339());

        // Check if exists
        let existing: Option<(String,)> = sqlx::query_as("SELECT id FROM tasks WHERE id = ?")
            .bind(&id)
            .fetch_optional(&mut *tx)
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        if existing.is_some() {
            sqlx::query(
                "UPDATE tasks SET title = ?, description = ?, completed = ?, reminder = ?, updated_at = NOW(), deleted_at = NULL WHERE id = ?"
            )
            .bind(title)
            .bind(&description)
            .bind(completed)
            .bind(&reminder)
            .bind(&id)
            .execute(&mut *tx)
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
        } else {
            sqlx::query(
                "INSERT INTO tasks (id, list_id, title, description, completed, reminder, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            )
            .bind(&id)
            .bind(list_id)
            .bind(title)
            .bind(&description)
            .bind(completed)
            .bind(&reminder)
            .bind(&created_at)
            .execute(&mut *tx)
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
        }

        synced_tasks += 1;
    }

    tx.commit().await
        .map_err(|e| ApiError::internal(format!("Commit error: {}", e)))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Sincronização concluída",
        SyncPushResponse {
            synced_lists,
            synced_tasks,
            deleted_lists: body.deleted_lists.len(),
            deleted_tasks: body.deleted_tasks.len(),
            server_time: Utc::now().to_rfc3339(),
        },
    )))
}

// POST /sync/pull
pub async fn sync_pull(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<SyncPullRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let user_id = &claims.user_id;

    let (lists, tasks, deleted_lists, deleted_tasks) = if let Some(last_sync) = &body.last_sync {
        // Only changes since last sync
        let last_sync_dt: DateTime<Utc> = last_sync.parse()
            .map_err(|_| ApiError::bad_request("Invalid last_sync format"))?;

        let lists: Vec<TodoList> = sqlx::query_as(
            r#"
            SELECT id, user_id, name, color, created_at, updated_at, deleted_at
            FROM todo_lists 
            WHERE user_id = ? AND deleted_at IS NULL AND updated_at > ?
            "#
        )
        .bind(user_id)
        .bind(last_sync_dt)
        .fetch_all(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        let tasks: Vec<Task> = sqlx::query_as(
            r#"
            SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at, t.deleted_at
            FROM tasks t
            JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NULL AND l.deleted_at IS NULL AND t.updated_at > ?
            "#
        )
        .bind(user_id)
        .bind(last_sync_dt)
        .fetch_all(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        let deleted_lists: Vec<String> = sqlx::query_scalar(
            "SELECT id FROM todo_lists WHERE user_id = ? AND deleted_at IS NOT NULL AND deleted_at > ?"
        )
        .bind(user_id)
        .bind(last_sync_dt)
        .fetch_all(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        let deleted_tasks: Vec<String> = sqlx::query_scalar(
            r#"
            SELECT t.id FROM tasks t
            JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NOT NULL AND t.deleted_at > ?
            "#
        )
        .bind(user_id)
        .bind(last_sync_dt)
        .fetch_all(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        (lists, tasks, deleted_lists, deleted_tasks)
    } else {
        // All data
        let lists: Vec<TodoList> = sqlx::query_as(
            "SELECT * FROM todo_lists WHERE user_id = ? AND deleted_at IS NULL"
        )
        .bind(user_id)
        .fetch_all(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        let tasks: Vec<Task> = sqlx::query_as(
            r#"
            SELECT t.* FROM tasks t
            JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NULL AND l.deleted_at IS NULL
            "#
        )
        .bind(user_id)
        .fetch_all(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        let deleted_lists: Vec<String> = sqlx::query_scalar(
            "SELECT id FROM todo_lists WHERE user_id = ? AND deleted_at IS NOT NULL"
        )
        .bind(user_id)
        .fetch_all(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        let deleted_tasks: Vec<String> = sqlx::query_scalar(
            r#"
            SELECT t.id FROM tasks t
            JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NOT NULL
            "#
        )
        .bind(user_id)
        .fetch_all(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        (lists, tasks, deleted_lists, deleted_tasks)
    };

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Dados sincronizados",
        SyncPullResponse {
            lists: lists.into_iter().map(|l| l.into()).collect(),
            tasks: tasks.into_iter().map(|t| t.into()).collect(),
            deleted_lists,
            deleted_tasks,
            server_time: Utc::now().to_rfc3339(),
        },
    )))
}

// POST /sync/full
pub async fn sync_full(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let user_id = &claims.user_id;

    let lists: Vec<TodoList> = sqlx::query_as(
        "SELECT * FROM todo_lists WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at DESC"
    )
    .bind(user_id)
    .fetch_all(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let tasks: Vec<Task> = sqlx::query_as(
        r#"
        SELECT t.* FROM tasks t
        JOIN todo_lists l ON t.list_id = l.id
        WHERE l.user_id = ? AND t.deleted_at IS NULL AND l.deleted_at IS NULL
        ORDER BY t.completed ASC, t.created_at DESC
        "#
    )
    .bind(user_id)
    .fetch_all(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Sincronização completa",
        SyncFullResponse {
            lists: lists.into_iter().map(|l| l.into()).collect(),
            tasks: tasks.into_iter().map(|t| t.into()).collect(),
            server_time: Utc::now().to_rfc3339(),
        },
    )))
}
