-- =====================================================
-- Todo App - Script de Criação do Banco de Dados
-- MySQL 5.7+ / MariaDB 10.2+
-- =====================================================

-- Usar o banco de dados
-- USE d3f4ltco_todoapp;

-- =====================================================
-- TABELA: users (Usuários)
-- =====================================================
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY COMMENT 'UUID do usuário',
    username VARCHAR(100) NOT NULL COMMENT 'Nome de exibição',
    email VARCHAR(255) NOT NULL COMMENT 'Email único para login',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Hash bcrypt da senha',
    is_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=não verificado, 1=verificado',
    verification_code VARCHAR(6) NULL COMMENT 'Código de verificação de 6 dígitos',
    code_expires_at DATETIME NULL COMMENT 'Expiração do código',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Data de criação',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Última atualização',
    
    UNIQUE INDEX idx_email (email),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tabela de usuários';

-- Migração para adicionar campos de verificação (executar se tabela já existe)
-- ALTER TABLE users ADD COLUMN is_verified TINYINT(1) NOT NULL DEFAULT 1;
-- ALTER TABLE users ADD COLUMN verification_code VARCHAR(6) NULL;
-- ALTER TABLE users ADD COLUMN code_expires_at DATETIME NULL;

-- =====================================================
-- TABELA: todo_lists (Listas de Tarefas)
-- =====================================================
CREATE TABLE IF NOT EXISTS todo_lists (
    id VARCHAR(36) PRIMARY KEY COMMENT 'UUID da lista',
    user_id VARCHAR(36) NOT NULL COMMENT 'ID do usuário proprietário',
    name VARCHAR(255) NOT NULL COMMENT 'Nome da lista',
    color VARCHAR(20) NOT NULL DEFAULT '#3B82F6' COMMENT 'Cor da lista (hex)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Data de criação',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Última atualização',
    deleted_at DATETIME NULL DEFAULT NULL COMMENT 'Data de exclusão (soft delete)',
    
    INDEX idx_user_id (user_id),
    INDEX idx_deleted (deleted_at),
    INDEX idx_updated (updated_at),
    
    CONSTRAINT fk_lists_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE 
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Listas de tarefas';

-- =====================================================
-- TABELA: tasks (Tarefas)
-- =====================================================
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(36) PRIMARY KEY COMMENT 'UUID da tarefa',
    list_id VARCHAR(36) NOT NULL COMMENT 'ID da lista',
    title VARCHAR(500) NOT NULL COMMENT 'Título da tarefa',
    description TEXT NULL COMMENT 'Descrição detalhada',
    completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=pendente, 1=concluída',
    reminder DATETIME NULL DEFAULT NULL COMMENT 'Data/hora do lembrete',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Data de criação',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Última atualização',
    deleted_at DATETIME NULL DEFAULT NULL COMMENT 'Data de exclusão (soft delete)',
    
    INDEX idx_list_id (list_id),
    INDEX idx_completed (completed),
    INDEX idx_reminder (reminder),
    INDEX idx_deleted (deleted_at),
    INDEX idx_updated (updated_at),
    
    CONSTRAINT fk_tasks_list 
        FOREIGN KEY (list_id) 
        REFERENCES todo_lists(id) 
        ON DELETE CASCADE 
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tarefas';

-- =====================================================
-- TABELA: sync_log (Log de Sincronização)
-- =====================================================
CREATE TABLE IF NOT EXISTS sync_log (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID auto incremento',
    user_id VARCHAR(36) NOT NULL COMMENT 'ID do usuário',
    entity_type ENUM('user', 'list', 'task') NOT NULL COMMENT 'Tipo de entidade',
    entity_id VARCHAR(36) NOT NULL COMMENT 'ID da entidade',
    action ENUM('create', 'update', 'delete') NOT NULL COMMENT 'Ação realizada',
    synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Data da sincronização',
    device_id VARCHAR(100) NULL COMMENT 'ID do dispositivo (opcional)',
    
    INDEX idx_user_sync (user_id, synced_at),
    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_synced (synced_at),
    
    CONSTRAINT fk_sync_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE 
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Log de sincronização';

-- =====================================================
-- VIEWS ÚTEIS (Opcional)
-- =====================================================

-- View: Listas ativas com contagem de tarefas
CREATE OR REPLACE VIEW vw_lists_with_counts AS
SELECT 
    l.id,
    l.user_id,
    l.name,
    l.color,
    l.created_at,
    l.updated_at,
    COUNT(t.id) AS total_tasks,
    SUM(CASE WHEN t.completed = 1 THEN 1 ELSE 0 END) AS completed_tasks,
    SUM(CASE WHEN t.completed = 0 THEN 1 ELSE 0 END) AS pending_tasks
FROM todo_lists l
LEFT JOIN tasks t ON t.list_id = l.id AND t.deleted_at IS NULL
WHERE l.deleted_at IS NULL
GROUP BY l.id, l.user_id, l.name, l.color, l.created_at, l.updated_at;

-- View: Tarefas com lembretes pendentes
CREATE OR REPLACE VIEW vw_pending_reminders AS
SELECT 
    t.id,
    t.list_id,
    t.title,
    t.description,
    t.reminder,
    t.created_at,
    l.user_id,
    l.name AS list_name
FROM tasks t
JOIN todo_lists l ON t.list_id = l.id
WHERE t.deleted_at IS NULL 
    AND l.deleted_at IS NULL
    AND t.completed = 0 
    AND t.reminder IS NOT NULL 
    AND t.reminder <= NOW();

-- =====================================================
-- DADOS DE EXEMPLO (Opcional - Descomente para testar)
-- =====================================================

/*
-- Usuário de teste (senha: 123456)
INSERT INTO users (id, username, email, password_hash, created_at) VALUES 
('550e8400-e29b-41d4-a716-446655440000', 'Teste', 'teste@teste.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', NOW());

-- Lista de exemplo
INSERT INTO todo_lists (id, user_id, name, color, created_at) VALUES 
('550e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440000', 'Minha Primeira Lista', '#3B82F6', NOW());

-- Tarefas de exemplo
INSERT INTO tasks (id, list_id, title, description, completed, created_at) VALUES 
('550e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440001', 'Tarefa de exemplo 1', 'Descrição da tarefa', 0, NOW()),
('550e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440001', 'Tarefa de exemplo 2', '', 1, NOW()),
('550e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440001', 'Tarefa com lembrete', 'Não esquecer!', 0, NOW());

-- Atualizar lembrete para daqui 1 hora
UPDATE tasks SET reminder = DATE_ADD(NOW(), INTERVAL 1 HOUR) WHERE id = '550e8400-e29b-41d4-a716-446655440004';
*/

-- =====================================================
-- VERIFICAÇÃO
-- =====================================================
SELECT 'Banco de dados criado com sucesso!' AS status;

SHOW TABLES;
