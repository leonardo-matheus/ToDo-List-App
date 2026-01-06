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

// Helper: Log sync
async fn log_sync(pool: &MySqlPool, user_id: &str, entity_type: &str, entity_id: &str, action: &str) {
    let _ = sqlx::query(
        "INSERT INTO sync_log (user_id, entity_type, entity_id, action) VALUES (?, ?, ?, ?)"
    )
    .bind(user_id)
    .bind(entity_type)
    .bind(entity_id)
    .bind(action)
    .execute(pool)
    .await;
}

// GET /tasks
pub async fn get_all_tasks(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;

    let tasks: Vec<Task> = sqlx::query_as(
        r#"
        SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at, t.deleted_at
        FROM tasks t
        JOIN todo_lists l ON t.list_id = l.id
        WHERE l.user_id = ? AND t.deleted_at IS NULL AND l.deleted_at IS NULL
        ORDER BY t.completed ASC, t.created_at DESC
        "#
    )
    .bind(&claims.user_id)
    .fetch_all(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let tasks: Vec<TaskResponse> = tasks.into_iter().map(|t| t.into()).collect();

    Ok(HttpResponse::Ok().json(ApiResponse::success("Tarefas carregadas", tasks)))
}

// GET /lists/{id}/tasks
pub async fn get_tasks_by_list(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    path: web::Path<String>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let list_id = path.into_inner();

    // Check ownership
    let list_exists: Option<(String,)> = sqlx::query_as(
        "SELECT id FROM todo_lists WHERE id = ? AND user_id = ?"
    )
    .bind(&list_id)
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    if list_exists.is_none() {
        return Err(ApiError::not_found("Lista não encontrada"));
    }

    let tasks: Vec<Task> = sqlx::query_as(
        r#"
        SELECT id, list_id, title, description, completed, reminder, created_at, updated_at, deleted_at
        FROM tasks 
        WHERE list_id = ? AND deleted_at IS NULL
        ORDER BY completed ASC, created_at DESC
        "#
    )
    .bind(&list_id)
    .fetch_all(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let tasks: Vec<TaskResponse> = tasks.into_iter().map(|t| t.into()).collect();

    Ok(HttpResponse::Ok().json(ApiResponse::success("Tarefas carregadas", tasks)))
}

// GET /tasks/{id}
pub async fn get_task(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    path: web::Path<String>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let task_id = path.into_inner();

    let task: Option<Task> = sqlx::query_as(
        r#"
        SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at, t.deleted_at
        FROM tasks t
        JOIN todo_lists l ON t.list_id = l.id
        WHERE t.id = ? AND l.user_id = ? AND t.deleted_at IS NULL
        "#
    )
    .bind(&task_id)
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let task = task.ok_or_else(|| ApiError::not_found("Tarefa não encontrada"))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success("Tarefa encontrada", TaskResponse::from(task))))
}

// POST /tasks
pub async fn create_task(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<CreateTaskRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;

    let id = body.id.clone().unwrap_or_else(generate_uuid);
    let list_id = &body.list_id;
    let title = body.title.trim();
    let description = body.description.clone().unwrap_or_default();
    let completed = body.completed.unwrap_or(false);
    let reminder: Option<DateTime<Utc>> = body.reminder.as_ref().and_then(|r| r.parse().ok());
    let created_at = body.created_at.clone().unwrap_or_else(|| Utc::now().to_rfc3339());

    if list_id.is_empty() || title.is_empty() {
        return Err(ApiError::bad_request("Lista e título são obrigatórios"));
    }

    // Check list ownership
    let list_exists: Option<(String,)> = sqlx::query_as(
        "SELECT id FROM todo_lists WHERE id = ? AND user_id = ? AND deleted_at IS NULL"
    )
    .bind(list_id)
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    if list_exists.is_none() {
        return Err(ApiError::not_found("Lista não encontrada"));
    }

    // Check if exists (for sync)
    let existing: Option<(String,)> = sqlx::query_as(
        "SELECT id FROM tasks WHERE id = ?"
    )
    .bind(&id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    if existing.is_some() {
        // Update if exists
        sqlx::query(
            r#"
            UPDATE tasks 
            SET title = ?, description = ?, completed = ?, reminder = ?, updated_at = NOW(), deleted_at = NULL
            WHERE id = ?
            "#
        )
        .bind(title)
        .bind(&description)
        .bind(completed)
        .bind(&reminder)
        .bind(&id)
        .execute(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
    } else {
        // Create new
        sqlx::query(
            r#"
            INSERT INTO tasks (id, list_id, title, description, completed, reminder, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            "#
        )
        .bind(&id)
        .bind(list_id)
        .bind(title)
        .bind(&description)
        .bind(completed)
        .bind(&reminder)
        .bind(&created_at)
        .execute(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
    }

    log_sync(pool.get_ref(), &claims.user_id, "task", &id, "create").await;

    Ok(HttpResponse::Created().json(ApiResponse::success(
        "Tarefa criada com sucesso",
        TaskResponse {
            id,
            list_id: list_id.clone(),
            title: title.to_string(),
            description: if description.is_empty() { None } else { Some(description) },
            completed,
            reminder,
            created_at: Utc::now(),
            updated_at: Utc::now(),
        },
    )))
}

// PUT /tasks/{id}
pub async fn update_task(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    path: web::Path<String>,
    body: web::Json<UpdateTaskRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let task_id = path.into_inner();

    // Check ownership
    let existing: Option<Task> = sqlx::query_as(
        r#"
        SELECT t.* FROM tasks t
        JOIN todo_lists l ON t.list_id = l.id
        WHERE t.id = ? AND l.user_id = ? AND t.deleted_at IS NULL
        "#
    )
    .bind(&task_id)
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let mut task = existing.ok_or_else(|| ApiError::not_found("Tarefa não encontrada"))?;

    // Update fields
    if let Some(title) = &body.title {
        task.title = title.trim().to_string();
    }

    if let Some(description) = &body.description {
        task.description = if description.is_empty() { None } else { Some(description.clone()) };
    }

    if let Some(completed) = body.completed {
        task.completed = completed;
    }

    if let Some(reminder) = &body.reminder {
        task.reminder = if reminder.is_empty() { None } else { reminder.parse().ok() };
    }

    sqlx::query(
        r#"
        UPDATE tasks 
        SET title = ?, description = ?, completed = ?, reminder = ?, updated_at = NOW()
        WHERE id = ?
        "#
    )
    .bind(&task.title)
    .bind(&task.description)
    .bind(task.completed)
    .bind(&task.reminder)
    .bind(&task_id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    log_sync(pool.get_ref(), &claims.user_id, "task", &task_id, "update").await;

    // Return updated task
    let task: Task = sqlx::query_as(
        "SELECT * FROM tasks WHERE id = ?"
    )
    .bind(&task_id)
    .fetch_one(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success("Tarefa atualizada", TaskResponse::from(task))))
}

// DELETE /tasks/{id}
pub async fn delete_task(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    path: web::Path<String>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let task_id = path.into_inner();

    // Soft delete
    sqlx::query(
        r#"
        UPDATE tasks t
        JOIN todo_lists l ON t.list_id = l.id
        SET t.deleted_at = NOW() 
        WHERE t.id = ? AND l.user_id = ?
        "#
    )
    .bind(&task_id)
    .bind(&claims.user_id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    log_sync(pool.get_ref(), &claims.user_id, "task", &task_id, "delete").await;

    Ok(HttpResponse::Ok().json(ApiResponse::<()>::success_no_data("Tarefa deletada com sucesso")))
}
