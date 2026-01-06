use rusqlite::{Connection, Result as SqliteResult};
use serde::{Deserialize, Serialize};
use std::sync::Mutex;
use tauri::State;
use bcrypt::{hash, verify, DEFAULT_COST};
use uuid::Uuid;
use chrono::Utc;

// ============== MODELOS ==============

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct User {
    pub id: String,
    pub username: String,
    pub email: String,
    pub created_at: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TodoList {
    pub id: String,
    pub user_id: String,
    pub name: String,
    pub color: String,
    pub created_at: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Task {
    pub id: String,
    pub list_id: String,
    pub title: String,
    pub description: String,
    pub completed: bool,
    pub reminder: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct LoginRequest {
    pub email: String,
    pub password: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RegisterRequest {
    pub username: String,
    pub email: String,
    pub password: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct CreateListRequest {
    pub name: String,
    pub color: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct CreateTaskRequest {
    pub list_id: String,
    pub title: String,
    pub description: String,
    pub reminder: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct UpdateTaskRequest {
    pub id: String,
    pub title: Option<String>,
    pub description: Option<String>,
    pub completed: Option<bool>,
    pub reminder: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ApiResponse<T> {
    pub success: bool,
    pub message: String,
    pub data: Option<T>,
}

// ============== ESTADO DA APLICAÇÃO ==============

pub struct AppState {
    pub db: Mutex<Connection>,
    pub current_user: Mutex<Option<User>>,
}

// ============== INICIALIZAÇÃO DO BANCO DE DADOS ==============

fn init_database(conn: &Connection) -> SqliteResult<()> {
    conn.execute(
        "CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            created_at TEXT NOT NULL
        )",
        [],
    )?;

    conn.execute(
        "CREATE TABLE IF NOT EXISTS todo_lists (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            color TEXT NOT NULL DEFAULT '#3B82F6',
            created_at TEXT NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id)
        )",
        [],
    )?;

    conn.execute(
        "CREATE TABLE IF NOT EXISTS tasks (
            id TEXT PRIMARY KEY,
            list_id TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT DEFAULT '',
            completed INTEGER NOT NULL DEFAULT 0,
            reminder TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (list_id) REFERENCES todo_lists(id)
        )",
        [],
    )?;

    Ok(())
}

// ============== COMANDOS TAURI ==============

#[tauri::command]
fn register(state: State<AppState>, request: RegisterRequest) -> ApiResponse<User> {
    let db = state.db.lock().unwrap();
    
    // Verificar se email já existe
    let exists: bool = db
        .query_row(
            "SELECT COUNT(*) > 0 FROM users WHERE email = ?",
            [&request.email],
            |row| row.get(0),
        )
        .unwrap_or(false);

    if exists {
        return ApiResponse {
            success: false,
            message: "Email já cadastrado".to_string(),
            data: None,
        };
    }

    // Hash da senha
    let password_hash = match hash(&request.password, DEFAULT_COST) {
        Ok(h) => h,
        Err(_) => {
            return ApiResponse {
                success: false,
                message: "Erro ao processar senha".to_string(),
                data: None,
            }
        }
    };

    let user_id = Uuid::new_v4().to_string();
    let created_at = Utc::now().to_rfc3339();

    match db.execute(
        "INSERT INTO users (id, username, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)",
        [&user_id, &request.username, &request.email, &password_hash, &created_at],
    ) {
        Ok(_) => {
            let user = User {
                id: user_id,
                username: request.username,
                email: request.email,
                created_at,
            };
            
            // Salvar usuário atual
            *state.current_user.lock().unwrap() = Some(user.clone());
            
            ApiResponse {
                success: true,
                message: "Usuário registrado com sucesso".to_string(),
                data: Some(user),
            }
        }
        Err(e) => ApiResponse {
            success: false,
            message: format!("Erro ao registrar: {}", e),
            data: None,
        },
    }
}

#[tauri::command]
fn login(state: State<AppState>, request: LoginRequest) -> ApiResponse<User> {
    let db = state.db.lock().unwrap();
    
    let result: Result<(String, String, String, String, String), _> = db.query_row(
        "SELECT id, username, email, password_hash, created_at FROM users WHERE email = ?",
        [&request.email],
        |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?, row.get(4)?)),
    );

    match result {
        Ok((id, username, email, password_hash, created_at)) => {
            match verify(&request.password, &password_hash) {
                Ok(true) => {
                    let user = User {
                        id,
                        username,
                        email,
                        created_at,
                    };
                    
                    *state.current_user.lock().unwrap() = Some(user.clone());
                    
                    ApiResponse {
                        success: true,
                        message: "Login realizado com sucesso".to_string(),
                        data: Some(user),
                    }
                }
                _ => ApiResponse {
                    success: false,
                    message: "Senha incorreta".to_string(),
                    data: None,
                },
            }
        }
        Err(_) => ApiResponse {
            success: false,
            message: "Usuário não encontrado".to_string(),
            data: None,
        },
    }
}

#[tauri::command]
fn logout(state: State<AppState>) -> ApiResponse<()> {
    *state.current_user.lock().unwrap() = None;
    ApiResponse {
        success: true,
        message: "Logout realizado".to_string(),
        data: None,
    }
}

#[tauri::command]
fn get_current_user(state: State<AppState>) -> ApiResponse<User> {
    let user = state.current_user.lock().unwrap();
    match user.clone() {
        Some(u) => ApiResponse {
            success: true,
            message: "Usuário encontrado".to_string(),
            data: Some(u),
        },
        None => ApiResponse {
            success: false,
            message: "Nenhum usuário logado".to_string(),
            data: None,
        },
    }
}

#[tauri::command]
fn create_list(state: State<AppState>, request: CreateListRequest) -> ApiResponse<TodoList> {
    let user = state.current_user.lock().unwrap();
    
    let user_id = match user.as_ref() {
        Some(u) => u.id.clone(),
        None => {
            return ApiResponse {
                success: false,
                message: "Usuário não autenticado".to_string(),
                data: None,
            }
        }
    };
    drop(user);

    let db = state.db.lock().unwrap();
    let list_id = Uuid::new_v4().to_string();
    let created_at = Utc::now().to_rfc3339();

    match db.execute(
        "INSERT INTO todo_lists (id, user_id, name, color, created_at) VALUES (?, ?, ?, ?, ?)",
        [&list_id, &user_id, &request.name, &request.color, &created_at],
    ) {
        Ok(_) => {
            let list = TodoList {
                id: list_id,
                user_id,
                name: request.name,
                color: request.color,
                created_at,
            };
            ApiResponse {
                success: true,
                message: "Lista criada com sucesso".to_string(),
                data: Some(list),
            }
        }
        Err(e) => ApiResponse {
            success: false,
            message: format!("Erro ao criar lista: {}", e),
            data: None,
        },
    }
}

#[tauri::command]
fn get_lists(state: State<AppState>) -> ApiResponse<Vec<TodoList>> {
    let user = state.current_user.lock().unwrap();
    
    let user_id = match user.as_ref() {
        Some(u) => u.id.clone(),
        None => {
            return ApiResponse {
                success: false,
                message: "Usuário não autenticado".to_string(),
                data: None,
            }
        }
    };
    drop(user);

    let db = state.db.lock().unwrap();
    let mut stmt = match db.prepare(
        "SELECT id, user_id, name, color, created_at FROM todo_lists WHERE user_id = ? ORDER BY created_at DESC",
    ) {
        Ok(s) => s,
        Err(e) => {
            return ApiResponse {
                success: false,
                message: format!("Erro: {}", e),
                data: None,
            }
        }
    };

    let lists: Vec<TodoList> = stmt
        .query_map([&user_id], |row| {
            Ok(TodoList {
                id: row.get(0)?,
                user_id: row.get(1)?,
                name: row.get(2)?,
                color: row.get(3)?,
                created_at: row.get(4)?,
            })
        })
        .unwrap()
        .filter_map(|r| r.ok())
        .collect();

    ApiResponse {
        success: true,
        message: "Listas carregadas".to_string(),
        data: Some(lists),
    }
}

#[tauri::command]
fn delete_list(state: State<AppState>, list_id: String) -> ApiResponse<()> {
    let db = state.db.lock().unwrap();
    
    // Deletar tarefas da lista primeiro
    let _ = db.execute("DELETE FROM tasks WHERE list_id = ?", [&list_id]);
    
    match db.execute("DELETE FROM todo_lists WHERE id = ?", [&list_id]) {
        Ok(_) => ApiResponse {
            success: true,
            message: "Lista deletada".to_string(),
            data: None,
        },
        Err(e) => ApiResponse {
            success: false,
            message: format!("Erro: {}", e),
            data: None,
        },
    }
}

#[tauri::command]
fn create_task(state: State<AppState>, request: CreateTaskRequest) -> ApiResponse<Task> {
    let db = state.db.lock().unwrap();
    let task_id = Uuid::new_v4().to_string();
    let now = Utc::now().to_rfc3339();

    match db.execute(
        "INSERT INTO tasks (id, list_id, title, description, completed, reminder, created_at, updated_at) 
         VALUES (?, ?, ?, ?, 0, ?, ?, ?)",
        [
            &task_id,
            &request.list_id,
            &request.title,
            &request.description,
            &request.reminder.clone().unwrap_or_default(),
            &now,
            &now,
        ],
    ) {
        Ok(_) => {
            let task = Task {
                id: task_id,
                list_id: request.list_id,
                title: request.title,
                description: request.description,
                completed: false,
                reminder: request.reminder,
                created_at: now.clone(),
                updated_at: now,
            };
            ApiResponse {
                success: true,
                message: "Tarefa criada".to_string(),
                data: Some(task),
            }
        }
        Err(e) => ApiResponse {
            success: false,
            message: format!("Erro: {}", e),
            data: None,
        },
    }
}

#[tauri::command]
fn get_tasks(state: State<AppState>, list_id: String) -> ApiResponse<Vec<Task>> {
    let db = state.db.lock().unwrap();
    let mut stmt = match db.prepare(
        "SELECT id, list_id, title, description, completed, reminder, created_at, updated_at 
         FROM tasks WHERE list_id = ? ORDER BY completed ASC, created_at DESC",
    ) {
        Ok(s) => s,
        Err(e) => {
            return ApiResponse {
                success: false,
                message: format!("Erro: {}", e),
                data: None,
            }
        }
    };

    let tasks: Vec<Task> = stmt
        .query_map([&list_id], |row| {
            let reminder: Option<String> = row.get(5)?;
            Ok(Task {
                id: row.get(0)?,
                list_id: row.get(1)?,
                title: row.get(2)?,
                description: row.get(3)?,
                completed: row.get::<_, i32>(4)? == 1,
                reminder: if reminder.as_ref().map(|s| s.is_empty()).unwrap_or(true) { None } else { reminder },
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
            })
        })
        .unwrap()
        .filter_map(|r| r.ok())
        .collect();

    ApiResponse {
        success: true,
        message: "Tarefas carregadas".to_string(),
        data: Some(tasks),
    }
}

#[tauri::command]
fn update_task(state: State<AppState>, request: UpdateTaskRequest) -> ApiResponse<Task> {
    let db = state.db.lock().unwrap();
    let now = Utc::now().to_rfc3339();

    // Buscar tarefa atual
    let current: Result<Task, _> = db.query_row(
        "SELECT id, list_id, title, description, completed, reminder, created_at, updated_at FROM tasks WHERE id = ?",
        [&request.id],
        |row| {
            let reminder: Option<String> = row.get(5)?;
            Ok(Task {
                id: row.get(0)?,
                list_id: row.get(1)?,
                title: row.get(2)?,
                description: row.get(3)?,
                completed: row.get::<_, i32>(4)? == 1,
                reminder: if reminder.as_ref().map(|s| s.is_empty()).unwrap_or(true) { None } else { reminder },
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
            })
        },
    );

    match current {
        Ok(mut task) => {
            if let Some(title) = request.title {
                task.title = title;
            }
            if let Some(desc) = request.description {
                task.description = desc;
            }
            if let Some(completed) = request.completed {
                task.completed = completed;
            }
            if request.reminder.is_some() {
                task.reminder = request.reminder;
            }
            task.updated_at = now.clone();

            let completed_int = if task.completed { 1 } else { 0 };
            let reminder_str = task.reminder.clone().unwrap_or_default();

            match db.execute(
                "UPDATE tasks SET title = ?, description = ?, completed = ?, reminder = ?, updated_at = ? WHERE id = ?",
                [&task.title, &task.description, &completed_int.to_string(), &reminder_str, &now, &task.id],
            ) {
                Ok(_) => ApiResponse {
                    success: true,
                    message: "Tarefa atualizada".to_string(),
                    data: Some(task),
                },
                Err(e) => ApiResponse {
                    success: false,
                    message: format!("Erro: {}", e),
                    data: None,
                },
            }
        }
        Err(_) => ApiResponse {
            success: false,
            message: "Tarefa não encontrada".to_string(),
            data: None,
        },
    }
}

#[tauri::command]
fn toggle_task(state: State<AppState>, task_id: String) -> ApiResponse<Task> {
    let db = state.db.lock().unwrap();
    let now = Utc::now().to_rfc3339();

    match db.execute(
        "UPDATE tasks SET completed = NOT completed, updated_at = ? WHERE id = ?",
        [&now, &task_id],
    ) {
        Ok(_) => {
            let task: Result<Task, _> = db.query_row(
                "SELECT id, list_id, title, description, completed, reminder, created_at, updated_at FROM tasks WHERE id = ?",
                [&task_id],
                |row| {
                    let reminder: Option<String> = row.get(5)?;
                    Ok(Task {
                        id: row.get(0)?,
                        list_id: row.get(1)?,
                        title: row.get(2)?,
                        description: row.get(3)?,
                        completed: row.get::<_, i32>(4)? == 1,
                        reminder: if reminder.as_ref().map(|s| s.is_empty()).unwrap_or(true) { None } else { reminder },
                        created_at: row.get(6)?,
                        updated_at: row.get(7)?,
                    })
                },
            );
            
            match task {
                Ok(t) => ApiResponse {
                    success: true,
                    message: "Status alterado".to_string(),
                    data: Some(t),
                },
                Err(_) => ApiResponse {
                    success: false,
                    message: "Erro ao buscar tarefa".to_string(),
                    data: None,
                },
            }
        }
        Err(e) => ApiResponse {
            success: false,
            message: format!("Erro: {}", e),
            data: None,
        },
    }
}

#[tauri::command]
fn delete_task(state: State<AppState>, task_id: String) -> ApiResponse<()> {
    let db = state.db.lock().unwrap();
    
    match db.execute("DELETE FROM tasks WHERE id = ?", [&task_id]) {
        Ok(_) => ApiResponse {
            success: true,
            message: "Tarefa deletada".to_string(),
            data: None,
        },
        Err(e) => ApiResponse {
            success: false,
            message: format!("Erro: {}", e),
            data: None,
        },
    }
}

#[tauri::command]
fn get_pending_reminders(state: State<AppState>) -> ApiResponse<Vec<Task>> {
    let user = state.current_user.lock().unwrap();
    
    let user_id = match user.as_ref() {
        Some(u) => u.id.clone(),
        None => {
            return ApiResponse {
                success: false,
                message: "Usuário não autenticado".to_string(),
                data: None,
            }
        }
    };
    drop(user);

    let db = state.db.lock().unwrap();
    let now = Utc::now().to_rfc3339();
    
    let mut stmt = match db.prepare(
        "SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at 
         FROM tasks t 
         JOIN todo_lists l ON t.list_id = l.id 
         WHERE l.user_id = ? AND t.reminder IS NOT NULL AND t.reminder != '' AND t.reminder <= ? AND t.completed = 0",
    ) {
        Ok(s) => s,
        Err(e) => {
            return ApiResponse {
                success: false,
                message: format!("Erro: {}", e),
                data: None,
            }
        }
    };

    let tasks: Vec<Task> = stmt
        .query_map([&user_id, &now], |row| {
            Ok(Task {
                id: row.get(0)?,
                list_id: row.get(1)?,
                title: row.get(2)?,
                description: row.get(3)?,
                completed: row.get::<_, i32>(4)? == 1,
                reminder: row.get(5)?,
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
            })
        })
        .unwrap()
        .filter_map(|r| r.ok())
        .collect();

    ApiResponse {
        success: true,
        message: "Lembretes pendentes".to_string(),
        data: Some(tasks),
    }
}

// ============== ENTRADA PRINCIPAL ==============

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_shell::init())
        .setup(|app| {
            use tauri::Manager;
            
            // Obter diretorio de dados do app (funciona no Android)
            let app_dir = app.path().app_data_dir()
                .unwrap_or_else(|_| std::path::PathBuf::from("."));
            
            // Criar diretorio se nao existir
            std::fs::create_dir_all(&app_dir).ok();
            
            let db_path = app_dir.join("todo.db");
            
            // Log para debug
            println!("Database path: {:?}", db_path);
            
            // Abrir conexao com o banco de dados
            let conn = Connection::open(&db_path)
                .expect(&format!("Falha ao abrir banco de dados em {:?}", db_path));
            
            // Inicializar tabelas
            init_database(&conn).expect("Falha ao inicializar banco de dados");
            
            // Criar e gerenciar o estado
            let app_state = AppState {
                db: Mutex::new(conn),
                current_user: Mutex::new(None),
            };
            
            app.manage(app_state);
            
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            register,
            login,
            logout,
            get_current_user,
            create_list,
            get_lists,
            delete_list,
            create_task,
            get_tasks,
            update_task,
            toggle_task,
            delete_task,
            get_pending_reminders,
        ])
        .run(tauri::generate_context!())
        .expect("Erro ao executar aplicacao Tauri");
}