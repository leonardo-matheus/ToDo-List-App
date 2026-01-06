<?php
/**
 * Endpoints de Tarefas
 * Todo App - API PHP
 */

function handleTasks(?string $id, string $method): void {
    $auth = requireAuth();
    
    switch ($method) {
        case 'GET':
            if ($id) {
                getTask($auth['user_id'], $id);
            } else {
                getAllTasks($auth['user_id']);
            }
            break;
        case 'POST':
            createTask($auth['user_id']);
            break;
        case 'PUT':
            if ($id) updateTask($auth['user_id'], $id);
            break;
        case 'DELETE':
            if ($id) deleteTask($auth['user_id'], $id);
            break;
        default:
            jsonResponse(false, 'Método não permitido', null, 405);
    }
}

function getAllTasks(string $userId): void {
    $pdo = getDB();
    
    $stmt = $pdo->prepare("
        SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at
        FROM tasks t
        JOIN todo_lists l ON t.list_id = l.id
        WHERE l.user_id = ? AND t.deleted_at IS NULL AND l.deleted_at IS NULL
        ORDER BY t.completed ASC, t.created_at DESC
    ");
    $stmt->execute([$userId]);
    $tasks = $stmt->fetchAll();
    
    // Converter completed para boolean
    foreach ($tasks as &$task) {
        $task['completed'] = (bool) $task['completed'];
    }
    
    jsonResponse(true, 'Tarefas carregadas', $tasks);
}

function getTasksByList(string $listId): void {
    $auth = requireAuth();
    $pdo = getDB();
    
    // Verificar propriedade da lista
    $stmt = $pdo->prepare("SELECT id FROM todo_lists WHERE id = ? AND user_id = ?");
    $stmt->execute([$listId, $auth['user_id']]);
    
    if (!$stmt->fetch()) {
        jsonResponse(false, 'Lista não encontrada', null, 404);
    }
    
    $stmt = $pdo->prepare("
        SELECT id, list_id, title, description, completed, reminder, created_at, updated_at
        FROM tasks 
        WHERE list_id = ? AND deleted_at IS NULL
        ORDER BY completed ASC, created_at DESC
    ");
    $stmt->execute([$listId]);
    $tasks = $stmt->fetchAll();
    
    foreach ($tasks as &$task) {
        $task['completed'] = (bool) $task['completed'];
    }
    
    jsonResponse(true, 'Tarefas carregadas', $tasks);
}

function getTask(string $userId, string $taskId): void {
    $pdo = getDB();
    
    $stmt = $pdo->prepare("
        SELECT t.id, t.list_id, t.title, t.description, t.completed, t.reminder, t.created_at, t.updated_at
        FROM tasks t
        JOIN todo_lists l ON t.list_id = l.id
        WHERE t.id = ? AND l.user_id = ? AND t.deleted_at IS NULL
    ");
    $stmt->execute([$taskId, $userId]);
    $task = $stmt->fetch();
    
    if (!$task) {
        jsonResponse(false, 'Tarefa não encontrada', null, 404);
    }
    
    $task['completed'] = (bool) $task['completed'];
    
    jsonResponse(true, 'Tarefa encontrada', $task);
}

function createTask(string $userId): void {
    $data = getJsonInput();
    
    $id = $data['id'] ?? generateUUID();
    $listId = $data['list_id'] ?? '';
    $title = trim($data['title'] ?? '');
    $description = trim($data['description'] ?? '');
    $completed = (bool) ($data['completed'] ?? false);
    $reminder = $data['reminder'] ?? null;
    $createdAt = $data['created_at'] ?? date('Y-m-d H:i:s');
    
    if (empty($listId) || empty($title)) {
        jsonResponse(false, 'Lista e título são obrigatórios', null, 400);
    }
    
    $pdo = getDB();
    
    // Verificar propriedade da lista
    $stmt = $pdo->prepare("SELECT id FROM todo_lists WHERE id = ? AND user_id = ? AND deleted_at IS NULL");
    $stmt->execute([$listId, $userId]);
    
    if (!$stmt->fetch()) {
        jsonResponse(false, 'Lista não encontrada', null, 404);
    }
    
    // Verificar se já existe (para sincronização)
    $stmt = $pdo->prepare("SELECT id FROM tasks WHERE id = ?");
    $stmt->execute([$id]);
    
    if ($stmt->fetch()) {
        // Atualizar se já existe
        $stmt = $pdo->prepare("
            UPDATE tasks 
            SET title = ?, description = ?, completed = ?, reminder = ?, updated_at = NOW(), deleted_at = NULL
            WHERE id = ?
        ");
        $stmt->execute([$title, $description, $completed ? 1 : 0, $reminder, $id]);
    } else {
        // Criar nova
        $stmt = $pdo->prepare("
            INSERT INTO tasks (id, list_id, title, description, completed, reminder, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ");
        $stmt->execute([$id, $listId, $title, $description, $completed ? 1 : 0, $reminder, $createdAt]);
    }
    
    // Log de sincronização
    logSync($userId, 'task', $id, 'create');
    
    jsonResponse(true, 'Tarefa criada com sucesso', [
        'id' => $id,
        'list_id' => $listId,
        'title' => $title,
        'description' => $description,
        'completed' => $completed,
        'reminder' => $reminder,
        'created_at' => $createdAt
    ], 201);
}

function updateTask(string $userId, string $taskId): void {
    $data = getJsonInput();
    
    $pdo = getDB();
    
    // Verificar propriedade
    $stmt = $pdo->prepare("
        SELECT t.id FROM tasks t
        JOIN todo_lists l ON t.list_id = l.id
        WHERE t.id = ? AND l.user_id = ? AND t.deleted_at IS NULL
    ");
    $stmt->execute([$taskId, $userId]);
    
    if (!$stmt->fetch()) {
        jsonResponse(false, 'Tarefa não encontrada', null, 404);
    }
    
    $updates = [];
    $params = [];
    
    if (isset($data['title'])) {
        $updates[] = "title = ?";
        $params[] = trim($data['title']);
    }
    
    if (isset($data['description'])) {
        $updates[] = "description = ?";
        $params[] = trim($data['description']);
    }
    
    if (isset($data['completed'])) {
        $updates[] = "completed = ?";
        $params[] = $data['completed'] ? 1 : 0;
    }
    
    if (array_key_exists('reminder', $data)) {
        $updates[] = "reminder = ?";
        $params[] = $data['reminder'];
    }
    
    if (empty($updates)) {
        jsonResponse(false, 'Nenhum campo para atualizar', null, 400);
    }
    
    $updates[] = "updated_at = NOW()";
    $params[] = $taskId;
    
    $sql = "UPDATE tasks SET " . implode(', ', $updates) . " WHERE id = ?";
    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    
    // Log de sincronização
    logSync($userId, 'task', $taskId, 'update');
    
    // Retornar tarefa atualizada
    getTask($userId, $taskId);
}

function deleteTask(string $userId, string $taskId): void {
    $pdo = getDB();
    
    // Verificar propriedade
    $stmt = $pdo->prepare("
        SELECT t.id FROM tasks t
        JOIN todo_lists l ON t.list_id = l.id
        WHERE t.id = ? AND l.user_id = ?
    ");
    $stmt->execute([$taskId, $userId]);
    
    if (!$stmt->fetch()) {
        jsonResponse(false, 'Tarefa não encontrada', null, 404);
    }
    
    // Soft delete
    $stmt = $pdo->prepare("UPDATE tasks SET deleted_at = NOW() WHERE id = ?");
    $stmt->execute([$taskId]);
    
    // Log de sincronização
    logSync($userId, 'task', $taskId, 'delete');
    
    jsonResponse(true, 'Tarefa deletada com sucesso');
}

function generateUUID(): string {
    return sprintf(
        '%04x%04x-%04x-%04x-%04x-%04x%04x%04x',
        mt_rand(0, 0xffff), mt_rand(0, 0xffff),
        mt_rand(0, 0xffff),
        mt_rand(0, 0x0fff) | 0x4000,
        mt_rand(0, 0x3fff) | 0x8000,
        mt_rand(0, 0xffff), mt_rand(0, 0xffff), mt_rand(0, 0xffff)
    );
}

function logSync(string $userId, string $entityType, string $entityId, string $action): void {
    $pdo = getDB();
    $stmt = $pdo->prepare("
        INSERT INTO sync_log (user_id, entity_type, entity_id, action) 
        VALUES (?, ?, ?, ?)
    ");
    $stmt->execute([$userId, $entityType, $entityId, $action]);
}
