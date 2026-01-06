use actix_web::HttpResponse;
use serde_json::json;

use crate::errors::ApiResponse;

pub async fn index() -> HttpResponse {
    let endpoints = json!({
        "POST /auth/register": "Registrar usuário",
        "POST /auth/verify": "Verificar email",
        "POST /auth/resend-code": "Reenviar código",
        "POST /auth/forgot-password": "Esqueci minha senha",
        "POST /auth/verify-reset-code": "Verificar código de reset",
        "POST /auth/reset-password": "Resetar senha",
        "POST /auth/login": "Login",
        "GET /auth/me": "Dados do usuário",
        "PUT /auth/update-username": "Atualizar nome",
        "PUT /auth/update-email": "Atualizar email",
        "PUT /auth/update-password": "Atualizar senha",
        "GET /lists": "Listar listas",
        "POST /lists": "Criar lista",
        "GET /lists/{id}": "Obter lista",
        "PUT /lists/{id}": "Atualizar lista",
        "DELETE /lists/{id}": "Deletar lista",
        "GET /lists/{id}/tasks": "Tarefas da lista",
        "GET /tasks": "Listar todas as tarefas",
        "POST /tasks": "Criar tarefa",
        "GET /tasks/{id}": "Obter tarefa",
        "PUT /tasks/{id}": "Atualizar tarefa",
        "DELETE /tasks/{id}": "Deletar tarefa",
        "POST /sync/push": "Sincronizar para servidor",
        "POST /sync/pull": "Baixar do servidor",
        "POST /sync/full": "Sincronização completa"
    });

    HttpResponse::Ok().json(ApiResponse::success("Todo App API v1.0 (Rust)", endpoints))
}
