<?php
/**
 * Endpoints de Listas
 * Todo App - API PHP
 */

function handleLists(?string $id, string $method): void {
    $auth = requireAuth();
    
    switch ($method) {
        case 'GET':
            if ($id) {
                getList($auth['user_id'], $id);
            } else {
                getLists($auth['user_id']);
            }
            break;
        case 'POST':
            createList($auth['user_id']);
            break;
        case 'PUT':
            if ($id) updateList($auth['user_id'], $id);
            break;
        case 'DELETE':
            if ($id) deleteList($auth['user_id'], $id);
            break;
        default:
            jsonResponse(false, 'Método não permitido', null, 405);
    }
}

function getLists(string $userId): void {
    $pdo = getDB();
    
    $stmt = $pdo->prepare("
        SELECT id, user_id, name, color, created_at, updated_at
        FROM todo_lists 
        WHERE user_id = ? AND deleted_at IS NULL
        ORDER BY created_at DESC
    ");
    $stmt->execute([$userId]);
    $lists = $stmt->fetchAll();
    
    jsonResponse(true, 'Listas carregadas', $lists);
}

function getList(string $userId, string $listId): void {
    $pdo = getDB();
    
    $stmt = $pdo->prepare("
        SELECT id, user_id, name, color, created_at, updated_at
        FROM todo_lists 
        WHERE id = ? AND user_id = ? AND deleted_at IS NULL
    ");
    $stmt->execute([$listId, $userId]);
    $list = $stmt->fetch();
    
    if (!$list) {
        jsonResponse(false, 'Lista não encontrada', null, 404);
    }
    
    jsonResponse(true, 'Lista encontrada', $list);
}

function createList(string $userId): void {
    $data = getJsonInput();
    
    $id = $data['id'] ?? generateUUID();
    $name = trim($data['name'] ?? '');
    $color = $data['color'] ?? '#3B82F6';
    $createdAt = $data['created_at'] ?? date('Y-m-d H:i:s');
    
    if (empty($name)) {
        jsonResponse(false, 'Nome da lista é obrigatório', null, 400);
    }
    
    $pdo = getDB();
    
    // Verificar se já existe (para sincronização)
    $stmt = $pdo->prepare("SELECT id FROM todo_lists WHERE id = ?");
    $stmt->execute([$id]);
    
    if ($stmt->fetch()) {
        // Atualizar se já existe
        $stmt = $pdo->prepare("
            UPDATE todo_lists 
            SET name = ?, color = ?, updated_at = NOW(), deleted_at = NULL
            WHERE id = ? AND user_id = ?
        ");
        $stmt->execute([$name, $color, $id, $userId]);
    } else {
        // Criar nova
        $stmt = $pdo->prepare("
            INSERT INTO todo_lists (id, user_id, name, color, created_at) 
            VALUES (?, ?, ?, ?, ?)
        ");
        $stmt->execute([$id, $userId, $name, $color, $createdAt]);
    }
    
    // Log de sincronização
    logSync($userId, 'list', $id, 'create');
    
    jsonResponse(true, 'Lista criada com sucesso', [
        'id' => $id,
        'user_id' => $userId,
        'name' => $name,
        'color' => $color,
        'created_at' => $createdAt
    ], 201);
}

function updateList(string $userId, string $listId): void {
    $data = getJsonInput();
    
    $name = trim($data['name'] ?? '');
    $color = $data['color'] ?? null;
    
    $pdo = getDB();
    
    // Verificar propriedade
    $stmt = $pdo->prepare("SELECT id FROM todo_lists WHERE id = ? AND user_id = ? AND deleted_at IS NULL");
    $stmt->execute([$listId, $userId]);
    
    if (!$stmt->fetch()) {
        jsonResponse(false, 'Lista não encontrada', null, 404);
    }
    
    $updates = [];
    $params = [];
    
    if (!empty($name)) {
        $updates[] = "name = ?";
        $params[] = $name;
    }
    
    if ($color !== null) {
        $updates[] = "color = ?";
        $params[] = $color;
    }
    
    if (empty($updates)) {
        jsonResponse(false, 'Nenhum campo para atualizar', null, 400);
    }
    
    $updates[] = "updated_at = NOW()";
    $params[] = $listId;
    $params[] = $userId;
    
    $sql = "UPDATE todo_lists SET " . implode(', ', $updates) . " WHERE id = ? AND user_id = ?";
    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    
    // Log de sincronização
    logSync($userId, 'list', $listId, 'update');
    
    // Retornar lista atualizada
    getList($userId, $listId);
}

function deleteList(string $userId, string $listId): void {
    $pdo = getDB();
    
    // Soft delete
    $stmt = $pdo->prepare("
        UPDATE todo_lists 
        SET deleted_at = NOW() 
        WHERE id = ? AND user_id = ?
    ");
    $stmt->execute([$listId, $userId]);
    
    // Soft delete das tarefas
    $stmt = $pdo->prepare("
        UPDATE tasks 
        SET deleted_at = NOW() 
        WHERE list_id = ?
    ");
    $stmt->execute([$listId]);
    
    // Log de sincronização
    logSync($userId, 'list', $listId, 'delete');
    
    jsonResponse(true, 'Lista deletada com sucesso');
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
