use actix_cors::Cors;
use actix_web::{web, App, HttpServer, middleware::Logger};
use sqlx::mysql::MySqlPoolOptions;

mod config;
mod db;
mod errors;
mod handlers;
mod middleware;
mod models;

use config::Config;

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    // Load .env file
    dotenvy::dotenv().ok();
    
    // Initialize logger
    env_logger::init_from_env(env_logger::Env::default().default_filter_or("info"));
    
    // Load configuration
    let config = Config::from_env();
    
    // Create database pool
    let pool = MySqlPoolOptions::new()
        .max_connections(10)
        .connect(&config.database_url)
        .await
        .expect("Failed to create database pool");
    
    log::info!("✅ Connected to database");
    
    let host = config.host.clone();
    let port = config.port;
    
    log::info!("🚀 Starting server at http://{}:{}", host, port);
    
    // Start HTTP server
    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header()
            .max_age(3600);
        
        App::new()
            .wrap(Logger::default())
            .wrap(cors)
            .app_data(web::Data::new(pool.clone()))
            .app_data(web::Data::new(config.clone()))
            .app_data(web::JsonConfig::default().limit(4096 * 1024))
            // Root
            .route("/", web::get().to(handlers::root::index))
            .route("/api", web::get().to(handlers::root::index))
            // Auth routes
            .service(
                web::scope("/auth")
                    .route("/register", web::post().to(handlers::auth::register))
                    .route("/verify", web::post().to(handlers::auth::verify_email))
                    .route("/resend-code", web::post().to(handlers::auth::resend_code))
                    .route("/forgot-password", web::post().to(handlers::auth::forgot_password))
                    .route("/verify-reset-code", web::post().to(handlers::auth::verify_reset_code))
                    .route("/reset-password", web::post().to(handlers::auth::reset_password))
                    .route("/login", web::post().to(handlers::auth::login))
                    .route("/me", web::get().to(handlers::auth::me))
                    .route("/update-username", web::put().to(handlers::auth::update_username))
                    .route("/update-email", web::put().to(handlers::auth::update_email))
                    .route("/update-password", web::put().to(handlers::auth::update_password))
            )
            // Lists routes
            .service(
                web::scope("/lists")
                    .route("", web::get().to(handlers::lists::get_lists))
                    .route("", web::post().to(handlers::lists::create_list))
                    .route("/{id}", web::get().to(handlers::lists::get_list))
                    .route("/{id}", web::put().to(handlers::lists::update_list))
                    .route("/{id}", web::delete().to(handlers::lists::delete_list))
                    .route("/{id}/tasks", web::get().to(handlers::tasks::get_tasks_by_list))
            )
            // Tasks routes
            .service(
                web::scope("/tasks")
                    .route("", web::get().to(handlers::tasks::get_all_tasks))
                    .route("", web::post().to(handlers::tasks::create_task))
                    .route("/{id}", web::get().to(handlers::tasks::get_task))
                    .route("/{id}", web::put().to(handlers::tasks::update_task))
                    .route("/{id}", web::delete().to(handlers::tasks::delete_task))
            )
            // Sync routes
            .service(
                web::scope("/sync")
                    .route("/push", web::post().to(handlers::sync::sync_push))
                    .route("/pull", web::post().to(handlers::sync::sync_pull))
                    .route("/full", web::post().to(handlers::sync::sync_full))
            )
    })
    .bind(format!("{}:{}", host, port))?
    .run()
    .await
}
