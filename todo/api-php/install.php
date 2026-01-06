<?php
/**
 * Script de instalação - Cria as tabelas no MySQL
 * Executar apenas uma vez: https://todoapp.leonardomdev.me/install.php
 */

require_once 'config.php';

setCorsHeaders();

try {
    $pdo = getDB();
    
    // Tabela de usuários
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS users (
            id VARCHAR(36) PRIMARY KEY,
            username VARCHAR(100) NOT NULL,
            email VARCHAR(255) UNIQUE NOT NULL,
            password_hash VARCHAR(255) NOT NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_email (email)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    
    // Tabela de listas
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS todo_lists (
            id VARCHAR(36) PRIMARY KEY,
            user_id VARCHAR(36) NOT NULL,
            name VARCHAR(255) NOT NULL,
            color VARCHAR(20) NOT NULL DEFAULT '#3B82F6',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted_at DATETIME NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            INDEX idx_user_id (user_id),
            INDEX idx_deleted (deleted_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    
    // Tabela de tarefas
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS tasks (
            id VARCHAR(36) PRIMARY KEY,
            list_id VARCHAR(36) NOT NULL,
            title VARCHAR(500) NOT NULL,
            description TEXT,
            completed TINYINT(1) NOT NULL DEFAULT 0,
            reminder DATETIME NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted_at DATETIME NULL,
            FOREIGN KEY (list_id) REFERENCES todo_lists(id) ON DELETE CASCADE,
            INDEX idx_list_id (list_id),
            INDEX idx_completed (completed),
            INDEX idx_reminder (reminder),
            INDEX idx_deleted (deleted_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    
    // Tabela de sincronização (para controle de conflitos)
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS sync_log (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id VARCHAR(36) NOT NULL,
            entity_type ENUM('user', 'list', 'task') NOT NULL,
            entity_id VARCHAR(36) NOT NULL,
            action ENUM('create', 'update', 'delete') NOT NULL,
            synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            device_id VARCHAR(100) NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            INDEX idx_user_sync (user_id, synced_at),
            INDEX idx_entity (entity_type, entity_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    
    jsonResponse(true, 'Banco de dados instalado com sucesso!', [
        'tables' => ['users', 'todo_lists', 'tasks', 'sync_log']
    ]);
    
} catch (PDOException $e) {
    jsonResponse(false, 'Erro ao criar tabelas: ' . $e->getMessage(), null, 500);
}
