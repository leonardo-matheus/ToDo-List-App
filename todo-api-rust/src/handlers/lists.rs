use actix_web::{web, HttpRequest, HttpResponse};
use chrono::Utc;
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

// GET /lists
pub async fn get_lists(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;

    let lists: Vec<TodoList> = sqlx::query_as(
        r#"
        SELECT id, user_id, name, color, created_at, updated_at, deleted_at
        FROM todo_lists 
        WHERE user_id = ? AND deleted_at IS NULL
        ORDER BY created_at DESC
        "#
    )
    .bind(&claims.user_id)
    .fetch_all(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let lists: Vec<ListResponse> = lists.into_iter().map(|l| l.into()).collect();

    Ok(HttpResponse::Ok().json(ApiResponse::success("Listas carregadas", lists)))
}

// GET /lists/{id}
pub async fn get_list(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    path: web::Path<String>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let list_id = path.into_inner();

    let list: Option<TodoList> = sqlx::query_as(
        r#"
        SELECT id, user_id, name, color, created_at, updated_at, deleted_at
        FROM todo_lists 
        WHERE id = ? AND user_id = ? AND deleted_at IS NULL
        "#
    )
    .bind(&list_id)
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let list = list.ok_or_else(|| ApiError::not_found("Lista não encontrada"))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success("Lista encontrada", ListResponse::from(list))))
}

// POST /lists
pub async fn create_list(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<CreateListRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;

    let id = body.id.clone().unwrap_or_else(generate_uuid);
    let name = body.name.trim();
    let color = body.color.clone().unwrap_or_else(|| "#3B82F6".to_string());
    let created_at = body.created_at.clone().unwrap_or_else(|| Utc::now().to_rfc3339());

    if name.is_empty() {
        return Err(ApiError::bad_request("Nome da lista é obrigatório"));
    }

    // Check if exists (for sync)
    let existing: Option<(String,)> = sqlx::query_as(
        "SELECT id FROM todo_lists WHERE id = ?"
    )
    .bind(&id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    if existing.is_some() {
        // Update if exists
        sqlx::query(
            r#"
            UPDATE todo_lists 
            SET name = ?, color = ?, updated_at = NOW(), deleted_at = NULL
            WHERE id = ? AND user_id = ?
            "#
        )
        .bind(name)
        .bind(&color)
        .bind(&id)
        .bind(&claims.user_id)
        .execute(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
    } else {
        // Create new
        sqlx::query(
            r#"
            INSERT INTO todo_lists (id, user_id, name, color, created_at) 
            VALUES (?, ?, ?, ?, ?)
            "#
        )
        .bind(&id)
        .bind(&claims.user_id)
        .bind(name)
        .bind(&color)
        .bind(&created_at)
        .execute(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;
    }

    log_sync(pool.get_ref(), &claims.user_id, "list", &id, "create").await;

    Ok(HttpResponse::Created().json(ApiResponse::success(
        "Lista criada com sucesso",
        ListResponse {
            id,
            user_id: claims.user_id,
            name: name.to_string(),
            color,
            created_at: Utc::now(),
            updated_at: Utc::now(),
        },
    )))
}

// PUT /lists/{id}
pub async fn update_list(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    path: web::Path<String>,
    body: web::Json<UpdateListRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let list_id = path.into_inner();

    // Check ownership
    let existing: Option<TodoList> = sqlx::query_as(
        "SELECT * FROM todo_lists WHERE id = ? AND user_id = ? AND deleted_at IS NULL"
    )
    .bind(&list_id)
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let mut list = existing.ok_or_else(|| ApiError::not_found("Lista não encontrada"))?;

    // Update fields
    let mut has_updates = false;

    if let Some(name) = &body.name {
        let name = name.trim();
        if !name.is_empty() {
            list.name = name.to_string();
            has_updates = true;
        }
    }

    if let Some(color) = &body.color {
        list.color = color.clone();
        has_updates = true;
    }

    if !has_updates {
        return Err(ApiError::bad_request("Nenhum campo para atualizar"));
    }

    sqlx::query(
        "UPDATE todo_lists SET name = ?, color = ?, updated_at = NOW() WHERE id = ? AND user_id = ?"
    )
    .bind(&list.name)
    .bind(&list.color)
    .bind(&list_id)
    .bind(&claims.user_id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    log_sync(pool.get_ref(), &claims.user_id, "list", &list_id, "update").await;

    // Return updated list
    let list: TodoList = sqlx::query_as(
        "SELECT * FROM todo_lists WHERE id = ?"
    )
    .bind(&list_id)
    .fetch_one(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success("Lista atualizada", ListResponse::from(list))))
}

// DELETE /lists/{id}
pub async fn delete_list(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    path: web::Path<String>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let list_id = path.into_inner();

    // Soft delete list
    sqlx::query(
        "UPDATE todo_lists SET deleted_at = NOW() WHERE id = ? AND user_id = ?"
    )
    .bind(&list_id)
    .bind(&claims.user_id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    // Soft delete tasks
    sqlx::query(
        "UPDATE tasks SET deleted_at = NOW() WHERE list_id = ?"
    )
    .bind(&list_id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    log_sync(pool.get_ref(), &claims.user_id, "list", &list_id, "delete").await;

    Ok(HttpResponse::Ok().json(ApiResponse::<()>::success_no_data("Lista deletada com sucesso")))
}
