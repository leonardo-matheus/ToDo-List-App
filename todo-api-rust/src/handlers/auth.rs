use actix_web::{web, HttpRequest, HttpResponse};
use chrono::{Duration, Utc};
use rand::Rng;
use sqlx::MySqlPool;

use crate::config::Config;
use crate::errors::{ApiError, ApiResponse};
use crate::middleware::jwt::{create_token, get_auth_user};
use crate::models::*;

// Helper: Generate UUID
fn generate_uuid() -> String {
    uuid::Uuid::new_v4().to_string()
}

// Helper: Generate 6-digit verification code
fn generate_verification_code() -> String {
    let code: u32 = rand::thread_rng().gen_range(0..1000000);
    format!("{:06}", code)
}

// Helper: Send verification email (simplified - log only in this version)
async fn send_verification_email(config: &Config, email: &str, username: &str, code: &str) {
    log::info!("📧 Sending verification email to {} ({}): Code = {}", email, username, code);
    
    // If SMTP is configured, send real email
    if !config.smtp_user.is_empty() {
        use lettre::{Message, SmtpTransport, Transport};
        use lettre::transport::smtp::authentication::Credentials;
        
        let html_body = format!(r#"
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; background: #f5f5f5; padding: 20px; }}
                .container {{ max-width: 400px; margin: 0 auto; background: white; border-radius: 16px; padding: 32px; }}
                .logo {{ text-align: center; color: #7C6FFF; font-size: 28px; font-weight: bold; margin-bottom: 24px; }}
                .code {{ text-align: center; font-size: 36px; font-weight: bold; color: #333; letter-spacing: 8px; background: #f0f0f0; padding: 16px; border-radius: 8px; margin: 24px 0; }}
                .text {{ color: #666; text-align: center; line-height: 1.6; }}
                .footer {{ text-align: center; color: #999; font-size: 12px; margin-top: 24px; }}
            </style>
        </head>
        <body>
            <div class='container'>
                <div class='logo'>MyTudo</div>
                <p class='text'>Olá <strong>{}</strong>!</p>
                <p class='text'>Use o código abaixo para ativar sua conta:</p>
                <div class='code'>{}</div>
                <p class='text'>Este código expira em <strong>15 minutos</strong>.</p>
                <p class='footer'>Se você não solicitou este código, ignore este email.</p>
            </div>
        </body>
        </html>
        "#, username, code);

        let email_result = Message::builder()
            .from(config.smtp_from.parse().unwrap_or_else(|_| "noreply@localhost".parse().unwrap()))
            .to(email.parse().unwrap())
            .subject(format!("MyTudo - Código de Verificação: {}", code))
            .header(lettre::message::header::ContentType::TEXT_HTML)
            .body(html_body);

        if let Ok(email_msg) = email_result {
            let creds = Credentials::new(config.smtp_user.clone(), config.smtp_pass.clone());
            
            let mailer = SmtpTransport::relay(&config.smtp_host)
                .ok()
                .map(|m| m.port(config.smtp_port).credentials(creds).build());

            if let Some(mailer) = mailer {
                match mailer.send(&email_msg) {
                    Ok(_) => log::info!("✅ Email sent successfully to {}", email),
                    Err(e) => log::error!("❌ Failed to send email: {:?}", e),
                }
            }
        }
    }
}

// Helper: Send password reset email
async fn send_password_reset_email(config: &Config, email: &str, username: &str, code: &str) {
    log::info!("📧 Sending password reset email to {} ({}): Code = {}", email, username, code);
    
    if !config.smtp_user.is_empty() {
        use lettre::{Message, SmtpTransport, Transport};
        use lettre::transport::smtp::authentication::Credentials;
        
        let html_body = format!(r#"
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; background: #f5f5f5; padding: 20px; }}
                .container {{ max-width: 400px; margin: 0 auto; background: white; border-radius: 16px; padding: 32px; }}
                .logo {{ text-align: center; color: #7C6FFF; font-size: 28px; font-weight: bold; margin-bottom: 24px; }}
                .code {{ text-align: center; font-size: 36px; font-weight: bold; color: #333; letter-spacing: 8px; background: #f0f0f0; padding: 16px; border-radius: 8px; margin: 24px 0; }}
                .text {{ color: #666; text-align: center; line-height: 1.6; }}
                .footer {{ text-align: center; color: #999; font-size: 12px; margin-top: 24px; }}
            </style>
        </head>
        <body>
            <div class='container'>
                <div class='logo'>MyTudo</div>
                <p class='text'>Olá <strong>{}</strong>!</p>
                <p class='text'>Use o código abaixo para recuperar sua senha:</p>
                <div class='code'>{}</div>
                <p class='text'>Este código expira em <strong>15 minutos</strong>.</p>
                <p class='footer'>Se você não solicitou a recuperação de senha, ignore este email.</p>
            </div>
        </body>
        </html>
        "#, username, code);

        let email_result = Message::builder()
            .from(config.smtp_from.parse().unwrap_or_else(|_| "noreply@localhost".parse().unwrap()))
            .to(email.parse().unwrap())
            .subject(format!("MyTudo - Recuperar Senha: {}", code))
            .header(lettre::message::header::ContentType::TEXT_HTML)
            .body(html_body);

        if let Ok(email_msg) = email_result {
            let creds = Credentials::new(config.smtp_user.clone(), config.smtp_pass.clone());
            
            let mailer = SmtpTransport::relay(&config.smtp_host)
                .ok()
                .map(|m| m.port(config.smtp_port).credentials(creds).build());

            if let Some(mailer) = mailer {
                match mailer.send(&email_msg) {
                    Ok(_) => log::info!("✅ Password reset email sent to {}", email),
                    Err(e) => log::error!("❌ Failed to send email: {:?}", e),
                }
            }
        }
    }
}

// POST /auth/register
pub async fn register(
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<RegisterRequest>,
) -> Result<HttpResponse, ApiError> {
    let username = body.username.trim();
    let email = body.email.trim().to_lowercase();
    let password = &body.password;

    // Validations
    if username.is_empty() || email.is_empty() || password.is_empty() {
        return Err(ApiError::bad_request("Preencha todos os campos"));
    }

    if !email.contains('@') {
        return Err(ApiError::bad_request("Email inválido"));
    }

    if password.len() < 6 {
        return Err(ApiError::bad_request("Senha deve ter no mínimo 6 caracteres"));
    }

    // Check if email exists
    let existing: Option<(String, bool)> = sqlx::query_as(
        "SELECT id, is_verified FROM users WHERE email = ?"
    )
    .bind(&email)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    if let Some((id, is_verified)) = existing {
        if is_verified {
            return Err(ApiError::conflict("Email já cadastrado"));
        } else {
            // User exists but not verified, resend code
            let code = generate_verification_code();
            let expires_at = Utc::now() + Duration::minutes(15);

            sqlx::query(
                "UPDATE users SET verification_code = ?, code_expires_at = ? WHERE id = ?"
            )
            .bind(&code)
            .bind(expires_at)
            .bind(&id)
            .execute(pool.get_ref())
            .await
            .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

            send_verification_email(&config, &email, username, &code).await;

            return Ok(HttpResponse::Ok().json(ApiResponse::success(
                "Código reenviado para o email",
                RegisterResponse {
                    email,
                    requires_verification: true,
                },
            )));
        }
    }

    // Create new user
    let id = generate_uuid();
    let password_hash = bcrypt::hash(password, bcrypt::DEFAULT_COST)
        .map_err(|_| ApiError::internal("Failed to hash password"))?;
    let code = generate_verification_code();
    let now = Utc::now();
    let expires_at = now + Duration::minutes(15);

    sqlx::query(
        r#"
        INSERT INTO users (id, username, email, password_hash, created_at, is_verified, verification_code, code_expires_at)
        VALUES (?, ?, ?, ?, ?, 0, ?, ?)
        "#
    )
    .bind(&id)
    .bind(username)
    .bind(&email)
    .bind(&password_hash)
    .bind(now)
    .bind(&code)
    .bind(expires_at)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    send_verification_email(&config, &email, username, &code).await;

    Ok(HttpResponse::Created().json(ApiResponse::success(
        "Código de verificação enviado para o email",
        RegisterResponse {
            email,
            requires_verification: true,
        },
    )))
}

// POST /auth/verify
pub async fn verify_email(
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<VerifyEmailRequest>,
) -> Result<HttpResponse, ApiError> {
    let email = body.email.trim().to_lowercase();
    let code = body.code.trim();

    if email.is_empty() || code.is_empty() {
        return Err(ApiError::bad_request("Email e código são obrigatórios"));
    }

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE email = ?"
    )
    .bind(&email)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::not_found("Email não encontrado"))?;

    if user.is_verified {
        return Err(ApiError::bad_request("Email já verificado"));
    }

    let stored_code = user.verification_code.as_deref().unwrap_or("");
    if stored_code != code {
        return Err(ApiError::bad_request("Código inválido"));
    }

    if let Some(expires_at) = user.code_expires_at {
        if expires_at < Utc::now() {
            return Err(ApiError::bad_request("Código expirado. Solicite um novo."));
        }
    }

    // Activate account
    sqlx::query(
        "UPDATE users SET is_verified = 1, verification_code = NULL, code_expires_at = NULL WHERE id = ?"
    )
    .bind(&user.id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let token = create_token(&config, &user.id, &user.email)?;

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Conta ativada com sucesso!",
        VerifyResponse {
            user: user.into(),
            token,
        },
    )))
}

// POST /auth/resend-code
pub async fn resend_code(
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<ResendCodeRequest>,
) -> Result<HttpResponse, ApiError> {
    let email = body.email.trim().to_lowercase();

    if email.is_empty() {
        return Err(ApiError::bad_request("Email é obrigatório"));
    }

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE email = ?"
    )
    .bind(&email)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::not_found("Email não encontrado"))?;

    if user.is_verified {
        return Err(ApiError::bad_request("Email já verificado"));
    }

    let code = generate_verification_code();
    let expires_at = Utc::now() + Duration::minutes(15);

    sqlx::query(
        "UPDATE users SET verification_code = ?, code_expires_at = ? WHERE id = ?"
    )
    .bind(&code)
    .bind(expires_at)
    .bind(&user.id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    send_verification_email(&config, &email, &user.username, &code).await;

    Ok(HttpResponse::Ok().json(ApiResponse::<()>::success_no_data("Novo código enviado para o email")))
}

// POST /auth/forgot-password
pub async fn forgot_password(
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<ForgotPasswordRequest>,
) -> Result<HttpResponse, ApiError> {
    let email = body.email.trim().to_lowercase();

    if email.is_empty() {
        return Err(ApiError::bad_request("Email é obrigatório"));
    }

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE email = ?"
    )
    .bind(&email)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::not_found("Email não encontrado"))?;

    let code = generate_verification_code();
    let expires_at = Utc::now() + Duration::minutes(15);

    sqlx::query(
        "UPDATE users SET verification_code = ?, code_expires_at = ? WHERE id = ?"
    )
    .bind(&code)
    .bind(expires_at)
    .bind(&user.id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    send_password_reset_email(&config, &email, &user.username, &code).await;

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Código de recuperação enviado para o email",
        ForgotPasswordResponse { email },
    )))
}

// POST /auth/verify-reset-code
pub async fn verify_reset_code(
    pool: web::Data<MySqlPool>,
    body: web::Json<VerifyResetCodeRequest>,
) -> Result<HttpResponse, ApiError> {
    let email = body.email.trim().to_lowercase();
    let code = body.code.trim();

    if email.is_empty() || code.is_empty() {
        return Err(ApiError::bad_request("Email e código são obrigatórios"));
    }

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE email = ?"
    )
    .bind(&email)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::not_found("Email não encontrado"))?;

    let stored_code = user.verification_code.as_deref().unwrap_or("");
    if stored_code != code {
        return Err(ApiError::bad_request("Código inválido"));
    }

    if let Some(expires_at) = user.code_expires_at {
        if expires_at < Utc::now() {
            return Err(ApiError::bad_request("Código expirado. Solicite um novo."));
        }
    }

    // Generate reset token
    let reset_token = format!("{:x}", md5::compute(format!("{}{}{}", email, Utc::now().timestamp(), rand::thread_rng().gen::<u32>())));
    
    sqlx::query(
        "UPDATE users SET verification_code = ? WHERE id = ?"
    )
    .bind(&reset_token)
    .bind(&user.id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Código verificado",
        VerifyResetCodeResponse { reset_token },
    )))
}

// POST /auth/reset-password
pub async fn reset_password(
    pool: web::Data<MySqlPool>,
    body: web::Json<ResetPasswordRequest>,
) -> Result<HttpResponse, ApiError> {
    let email = body.email.trim().to_lowercase();
    let reset_token = &body.reset_token;
    let new_password = &body.new_password;
    let confirm_password = &body.confirm_password;

    if email.is_empty() || reset_token.is_empty() || new_password.is_empty() || confirm_password.is_empty() {
        return Err(ApiError::bad_request("Preencha todos os campos"));
    }

    if new_password != confirm_password {
        return Err(ApiError::bad_request("As senhas não coincidem"));
    }

    if new_password.len() < 6 {
        return Err(ApiError::bad_request("Senha deve ter no mínimo 6 caracteres"));
    }

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE email = ?"
    )
    .bind(&email)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::not_found("Email não encontrado"))?;

    let stored_token = user.verification_code.as_deref().unwrap_or("");
    if stored_token != reset_token {
        return Err(ApiError::bad_request("Token inválido"));
    }

    let password_hash = bcrypt::hash(new_password, bcrypt::DEFAULT_COST)
        .map_err(|_| ApiError::internal("Failed to hash password"))?;

    sqlx::query(
        "UPDATE users SET password_hash = ?, verification_code = NULL, code_expires_at = NULL WHERE id = ?"
    )
    .bind(&password_hash)
    .bind(&user.id)
    .execute(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    Ok(HttpResponse::Ok().json(ApiResponse::<()>::success_no_data("Senha alterada com sucesso!")))
}

// POST /auth/login
pub async fn login(
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<LoginRequest>,
) -> Result<HttpResponse, ApiError> {
    let email = body.email.trim().to_lowercase();
    let password = &body.password;

    if email.is_empty() || password.is_empty() {
        return Err(ApiError::bad_request("Preencha email e senha"));
    }

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE email = ?"
    )
    .bind(&email)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::unauthorized("Email ou senha incorretos"))?;

    let valid = bcrypt::verify(password, &user.password_hash)
        .map_err(|_| ApiError::internal("Password verification failed"))?;

    if !valid {
        return Err(ApiError::unauthorized("Email ou senha incorretos"));
    }

    if !user.is_verified {
        // Resend verification code
        let code = generate_verification_code();
        let expires_at = Utc::now() + Duration::minutes(15);

        sqlx::query(
            "UPDATE users SET verification_code = ?, code_expires_at = ? WHERE id = ?"
        )
        .bind(&code)
        .bind(expires_at)
        .bind(&user.id)
        .execute(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

        send_verification_email(&config, &email, &user.username, &code).await;

        return Err(ApiError::forbidden("Conta não verificada. Código enviado para o email."));
    }

    let token = create_token(&config, &user.id, &user.email)?;

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Login realizado com sucesso",
        LoginResponse {
            user: user.into(),
            token,
        },
    )))
}

// GET /auth/me
pub async fn me(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE id = ?"
    )
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::not_found("Usuário não encontrado"))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success("Dados do usuário", UserPublic::from(user))))
}

// PUT /auth/update-username
pub async fn update_username(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<UpdateUsernameRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let username = body.username.trim();

    if username.is_empty() {
        return Err(ApiError::bad_request("Nome de usuário é obrigatório"));
    }

    if username.len() < 3 {
        return Err(ApiError::bad_request("Nome deve ter no mínimo 3 caracteres"));
    }

    sqlx::query("UPDATE users SET username = ? WHERE id = ?")
        .bind(username)
        .bind(&claims.user_id)
        .execute(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Nome atualizado com sucesso",
        UpdateUsernameResponse {
            username: username.to_string(),
        },
    )))
}

// PUT /auth/update-email
pub async fn update_email(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<UpdateEmailRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let new_email = body.email.trim().to_lowercase();
    let password = &body.password;

    if new_email.is_empty() {
        return Err(ApiError::bad_request("Email é obrigatório"));
    }

    if !new_email.contains('@') {
        return Err(ApiError::bad_request("Email inválido"));
    }

    if password.is_empty() {
        return Err(ApiError::bad_request("Senha atual é obrigatória"));
    }

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE id = ?"
    )
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::not_found("Usuário não encontrado"))?;

    let valid = bcrypt::verify(password, &user.password_hash)
        .map_err(|_| ApiError::internal("Password verification failed"))?;

    if !valid {
        return Err(ApiError::unauthorized("Senha incorreta"));
    }

    // Check if email is already in use
    let existing: Option<(String,)> = sqlx::query_as(
        "SELECT id FROM users WHERE email = ? AND id != ?"
    )
    .bind(&new_email)
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    if existing.is_some() {
        return Err(ApiError::conflict("Email já está em uso"));
    }

    sqlx::query("UPDATE users SET email = ? WHERE id = ?")
        .bind(&new_email)
        .bind(&claims.user_id)
        .execute(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let token = create_token(&config, &claims.user_id, &new_email)?;

    Ok(HttpResponse::Ok().json(ApiResponse::success(
        "Email atualizado com sucesso",
        UpdateEmailResponse {
            email: new_email,
            token,
        },
    )))
}

// PUT /auth/update-password
pub async fn update_password(
    req: HttpRequest,
    pool: web::Data<MySqlPool>,
    config: web::Data<Config>,
    body: web::Json<UpdatePasswordRequest>,
) -> Result<HttpResponse, ApiError> {
    let claims = get_auth_user(&req, &config)?;
    let current_password = &body.current_password;
    let new_password = &body.new_password;
    let confirm_password = &body.confirm_password;

    if current_password.is_empty() || new_password.is_empty() || confirm_password.is_empty() {
        return Err(ApiError::bad_request("Preencha todos os campos"));
    }

    if new_password != confirm_password {
        return Err(ApiError::bad_request("As senhas não coincidem"));
    }

    if new_password.len() < 6 {
        return Err(ApiError::bad_request("Senha deve ter no mínimo 6 caracteres"));
    }

    let user: Option<User> = sqlx::query_as(
        "SELECT * FROM users WHERE id = ?"
    )
    .bind(&claims.user_id)
    .fetch_optional(pool.get_ref())
    .await
    .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    let user = user.ok_or_else(|| ApiError::not_found("Usuário não encontrado"))?;

    let valid = bcrypt::verify(current_password, &user.password_hash)
        .map_err(|_| ApiError::internal("Password verification failed"))?;

    if !valid {
        return Err(ApiError::unauthorized("Senha atual incorreta"));
    }

    let password_hash = bcrypt::hash(new_password, bcrypt::DEFAULT_COST)
        .map_err(|_| ApiError::internal("Failed to hash password"))?;

    sqlx::query("UPDATE users SET password_hash = ? WHERE id = ?")
        .bind(&password_hash)
        .bind(&claims.user_id)
        .execute(pool.get_ref())
        .await
        .map_err(|e| ApiError::internal(format!("Database error: {}", e)))?;

    Ok(HttpResponse::Ok().json(ApiResponse::<()>::success_no_data("Senha atualizada com sucesso")))
}
