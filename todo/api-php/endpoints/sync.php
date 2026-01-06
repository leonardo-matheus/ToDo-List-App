<?php
/**
 * Endpoints de Sincronização
 * Todo App - API PHP
 * 
 * Estratégia: Last-Write-Wins com timestamp
 */

function handleSync(?string $action, string $method): void {
    if ($method !== 'POST') {
        jsonResponse(false, 'Método não permitido', null, 405);
    }
    
    $auth = requireAuth();
    
    switch ($action) {
        case 'push':
            syncPush($auth['user_id']);
            break;
        case 'pull':
            syncPull($auth['user_id']);
            break;
        case 'full':
            syncFull($auth['user_id']);
            break;
        default:
            jsonResponse(false, 'Ação de sincronização inválida', null, 400);
    }
}

/**
 * Push - Recebe dados do cliente e salva no servidor
 */
function syncPush(string $userId): void {
    $data = getJsonInput();
    
    $lists = $data['lists'] ?? [];
    $tasks = $data['tasks'] ?? [];
    $deletedLists = $data['deleted_lists'] ?? [];
    $deletedTasks = $data['deleted_tasks'] ?? [];
    
    $pdo = getDB();
    $pdo->beginTransaction();
    
    try {
        $syncedLists = [];
        $syncedTasks = [];
        
        // Processar listas deletadas
        foreach ($deletedLists as $listId) {
            $stmt = $pdo->prepare("UPDATE todo_lists SET deleted_at = NOW() WHERE id = ? AND user_id = ?");
            $stmt->execute([$listId, $userId]);
            
            $stmt = $pdo->prepare("UPDATE tasks SET deleted_at = NOW() WHERE list_id = ?");
            $stmt->execute([$listId]);
        }
        
        // Processar tarefas deletadas
        foreach ($deletedTasks as $taskId) {
            $stmt = $pdo->prepare("
                UPDATE tasks t
                JOIN todo_lists l ON t.list_id = l.id
                SET t.deleted_at = NOW() 
                WHERE t.id = ? AND l.user_id = ?
            ");
            $stmt->execute([$taskId, $userId]);
        }
        
        // Processar listas
        foreach ($lists as $list) {
            $result = upsertList($pdo, $userId, $list);
            if ($result) {
                $syncedLists[] = $result;
            }
        }
        
        // Processar tarefas
        foreach ($tasks as $task) {
            $result = upsertTask($pdo, $userId, $task);
            if ($result) {
                $syncedTasks[] = $result;
            }
        }
        
        $pdo->commit();
        
        jsonResponse(true, 'Sincronização concluída', [
            'synced_lists' => count($syncedLists),
            'synced_tasks' => count($syncedTasks),
            'deleted_lists' => count($deletedLists),
            'deleted_tasks' => count($deletedTasks),
            'server_time' => date('c')
        ]);
        
    } catch (Exception $e) {
        $pdo->rollBack();
        jsonResponse(false, 'Erro na sincronização: ' . $e->getMessage(), null, 500);
    }
}

/**
 * Pull - Envia dados do servidor para o cliente
 */
function syncPull(string $userId): void {
    $data = getJsonInput();
    $lastSync = $data['last_sync'] ?? null;
    
    $pdo = getDB();
    
    // Se não tem lastSync, envia tudo
    if (!$lastSync) {
        // Todas as listas ativas
        $stmt = $pdo->prepare("
            SELECT id, user_id, name, color, created_at, updated_at
            FROM todo_lists 
            WHERE user_id = ? AND deleted_at IS NULL
        ");
        $stmt->execute([$userId]);
        $lists = $stmt->fetchAll();
        
        // Todas as tarefas ativas
        $stmt = $pdo->prepare("
            SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at
            FROM tasks t
            JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NULL AND l.deleted_at IS NULL
        ");
        $stmt->execute([$userId]);
        $tasks = $stmt->fetchAll();
        
        // IDs deletados (para limpar no cliente)
        $stmt = $pdo->prepare("SELECT id FROM todo_lists WHERE user_id = ? AND deleted_at IS NOT NULL");
        $stmt->execute([$userId]);
        $deletedLists = array_column($stmt->fetchAll(), 'id');
        
        $stmt = $pdo->prepare("
            SELECT t.id FROM tasks t
            JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NOT NULL
        ");
        $stmt->execute([$userId]);
        $deletedTasks = array_column($stmt->fetchAll(), 'id');
        
    } else {
        // Apenas mudanças desde lastSync
        $stmt = $pdo->prepare("
            SELECT id, user_id, name, color, created_at, updated_at
            FROM todo_lists 
            WHERE user_id = ? AND deleted_at IS NULL AND updated_at > ?
        ");
        $stmt->execute([$userId, $lastSync]);
        $lists = $stmt->fetchAll();
        
        $stmt = $pdo->prepare("
            SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at
            FROM tasks t
            JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NULL AND l.deleted_at IS NULL AND t.updated_at > ?
        ");
        $stmt->execute([$userId, $lastSync]);
        $tasks = $stmt->fetchAll();
        
        $stmt = $pdo->prepare("
            SELECT id FROM todo_lists 
            WHERE user_id = ? AND deleted_at IS NOT NULL AND deleted_at > ?
        ");
        $stmt->execute([$userId, $lastSync]);
        $deletedLists = array_column($stmt->fetchAll(), 'id');
        
        $stmt = $pdo->prepare("
            SELECT t.id FROM tasks t
            JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NOT NULL AND t.deleted_at > ?
        ");
        $stmt->execute([$userId, $lastSync]);
        $deletedTasks = array_column($stmt->fetchAll(), 'id');
    }
    
    // Converter completed para boolean
    foreach ($tasks as &$task) {
        $task['completed'] = (bool) $task['completed'];
    }
    
    jsonResponse(true, 'Dados sincronizados', [
        'lists' => $lists,
        'tasks' => $tasks,
        'deleted_lists' => $deletedLists,
        'deleted_tasks' => $deletedTasks,
        'server_time' => date('c')
    ]);
}

/**
 * Full Sync - Push e Pull em uma única chamada
 */
function syncFull(string $userId): void {
    $data = getJsonInput();
    
    $localLists = $data['lists'] ?? [];
    $localTasks = $data['tasks'] ?? [];
    $deletedLists = $data['deleted_lists'] ?? [];
    $deletedTasks = $data['deleted_tasks'] ?? [];
    $lastSync = $data['last_sync'] ?? null;
    
    $pdo = getDB();
    $pdo->beginTransaction();
    
    try {
        // 1. Processar deletados do cliente
        foreach ($deletedLists as $listId) {
            $stmt = $pdo->prepare("UPDATE todo_lists SET deleted_at = NOW() WHERE id = ? AND user_id = ?");
            $stmt->execute([$listId, $userId]);
            $stmt = $pdo->prepare("UPDATE tasks SET deleted_at = NOW() WHERE list_id = ?");
            $stmt->execute([$listId]);
        }
        
        foreach ($deletedTasks as $taskId) {
            $stmt = $pdo->prepare("
                UPDATE tasks t JOIN todo_lists l ON t.list_id = l.id
                SET t.deleted_at = NOW() WHERE t.id = ? AND l.user_id = ?
            ");
            $stmt->execute([$taskId, $userId]);
        }
        
        // 2. Upsert listas do cliente
        foreach ($localLists as $list) {
            upsertList($pdo, $userId, $list);
        }
        
        // 3. Upsert tarefas do cliente
        foreach ($localTasks as $task) {
            upsertTask($pdo, $userId, $task);
        }
        
        $pdo->commit();
        
        // 4. Buscar todos os dados atualizados
        $stmt = $pdo->prepare("
            SELECT id, user_id, name, color, created_at, updated_at
            FROM todo_lists WHERE user_id = ? AND deleted_at IS NULL
        ");
        $stmt->execute([$userId]);
        $serverLists = $stmt->fetchAll();
        
        $stmt = $pdo->prepare("
            SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at
            FROM tasks t JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NULL AND l.deleted_at IS NULL
        ");
        $stmt->execute([$userId]);
        $serverTasks = $stmt->fetchAll();
        
        foreach ($serverTasks as &$task) {
            $task['completed'] = (bool) $task['completed'];
        }
        
        // 5. IDs deletados no servidor
        $stmt = $pdo->prepare("SELECT id FROM todo_lists WHERE user_id = ? AND deleted_at IS NOT NULL");
        $stmt->execute([$userId]);
        $serverDeletedLists = array_column($stmt->fetchAll(), 'id');
        
        $stmt = $pdo->prepare("
            SELECT t.id FROM tasks t JOIN todo_lists l ON t.list_id = l.id
            WHERE l.user_id = ? AND t.deleted_at IS NOT NULL
        ");
        $stmt->execute([$userId]);
        $serverDeletedTasks = array_column($stmt->fetchAll(), 'id');
        
        jsonResponse(true, 'Sincronização completa', [
            'lists' => $serverLists,
            'tasks' => $serverTasks,
            'deleted_lists' => $serverDeletedLists,
            'deleted_tasks' => $serverDeletedTasks,
            'server_time' => date('c')
        ]);
        
    } catch (Exception $e) {
        $pdo->rollBack();
        jsonResponse(false, 'Erro na sincronização: ' . $e->getMessage(), null, 500);
    }
}

function upsertList(PDO $pdo, string $userId, array $list): ?array {
    $id = $list['id'] ?? '';
    $name = trim($list['name'] ?? '');
    $color = $list['color'] ?? '#3B82F6';
    $createdAt = $list['created_at'] ?? date('Y-m-d H:i:s');
    $updatedAt = $list['updated_at'] ?? date('Y-m-d H:i:s');
    
    if (empty($id) || empty($name)) return null;
    
    // Verificar se existe
    $stmt = $pdo->prepare("SELECT updated_at FROM todo_lists WHERE id = ? AND user_id = ?");
    $stmt->execute([$id, $userId]);
    $existing = $stmt->fetch();
    
    if ($existing) {
        // Last-Write-Wins: só atualiza se o cliente for mais recente
        if ($updatedAt > $existing['updated_at']) {
            $stmt = $pdo->prepare("
                UPDATE todo_lists SET name = ?, color = ?, updated_at = ?, deleted_at = NULL
                WHERE id = ? AND user_id = ?
            ");
            $stmt->execute([$name, $color, $updatedAt, $id, $userId]);
        }
    } else {
        $stmt = $pdo->prepare("
            INSERT INTO todo_lists (id, user_id, name, color, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $stmt->execute([$id, $userId, $name, $color, $createdAt, $updatedAt]);
    }
    
    return ['id' => $id, 'name' => $name];
}

function upsertTask(PDO $pdo, string $userId, array $task): ?array {
    $id = $task['id'] ?? '';
    $listId = $task['list_id'] ?? '';
    $title = trim($task['title'] ?? '');
    $description = trim($task['description'] ?? '');
    $completed = (bool) ($task['completed'] ?? false);
    $reminder = $task['reminder'] ?? null;
    $createdAt = $task['created_at'] ?? date('Y-m-d H:i:s');
    $updatedAt = $task['updated_at'] ?? date('Y-m-d H:i:s');
    
    if (empty($id) || empty($listId) || empty($title)) return null;
    
    // Verificar propriedade da lista
    $stmt = $pdo->prepare("SELECT id FROM todo_lists WHERE id = ? AND user_id = ?");
    $stmt->execute([$listId, $userId]);
    if (!$stmt->fetch()) return null;
    
    // Verificar se existe
    $stmt = $pdo->prepare("SELECT updated_at FROM tasks WHERE id = ?");
    $stmt->execute([$id]);
    $existing = $stmt->fetch();
    
    if ($existing) {
        if ($updatedAt > $existing['updated_at']) {
            $stmt = $pdo->prepare("
                UPDATE tasks SET title = ?, description = ?, completed = ?, reminder = ?, updated_at = ?, deleted_at = NULL
                WHERE id = ?
            ");
            $stmt->execute([$title, $description, $completed ? 1 : 0, $reminder, $updatedAt, $id]);
        }
    } else {
        $stmt = $pdo->prepare("
            INSERT INTO tasks (id, list_id, title, description, completed, reminder, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ");
        $stmt->execute([$id, $listId, $title, $description, $completed ? 1 : 0, $reminder, $createdAt, $updatedAt]);
    }
    
    return ['id' => $id, 'title' => $title];
}
