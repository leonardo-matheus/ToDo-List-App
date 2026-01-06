<?php
/**
 * API Router Principal
 * Todo App - API PHP 8
 */

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/jwt.php';

setCorsHeaders();

// Parse da URL - múltiplas formas de obter o path
$requestUri = $_SERVER['REQUEST_URI'] ?? '';
$scriptName = $_SERVER['SCRIPT_NAME'] ?? '';

// Remover query string
$path = parse_url($requestUri, PHP_URL_PATH);

// Remover o diretório base se existir
$basePath = dirname($scriptName);
if ($basePath !== '/' && $basePath !== '\\') {
    $path = str_replace($basePath, '', $path);
}

// Remover index.php do path se existir
$path = str_replace('/index.php', '', $path);

// Limpar o path
$path = trim($path, '/');

// Debug - descomente para ver o path
// jsonResponse(true, 'Debug', ['path' => $path, 'uri' => $requestUri, 'script' => $scriptName]);

// Roteamento
$segments = $path ? explode('/', $path) : [];
$resource = $segments[0] ?? '';
$action = $segments[1] ?? null;
$subResource = $segments[2] ?? null;
$method = $_SERVER['REQUEST_METHOD'];

// Raiz - mostrar endpoints
if ($resource === '' || $resource === 'api') {
    jsonResponse(true, 'Todo App API v1.0', [
        'endpoints' => [
            'POST /auth/register' => 'Registrar usuário',
            'POST /auth/login' => 'Login',
            'GET /auth/me' => 'Dados do usuário',
            'GET /lists' => 'Listar listas',
            'POST /lists' => 'Criar lista',
            'PUT /lists/{id}' => 'Atualizar lista',
            'DELETE /lists/{id}' => 'Deletar lista',
            'GET /lists/{id}/tasks' => 'Tarefas da lista',
            'POST /tasks' => 'Criar tarefa',
            'PUT /tasks/{id}' => 'Atualizar tarefa',
            'DELETE /tasks/{id}' => 'Deletar tarefa',
            'POST /sync/push' => 'Sincronizar para servidor',
            'POST /sync/pull' => 'Baixar do servidor'
        ]
    ]);
}

// ============== AUTENTICAÇÃO ==============
if ($resource === 'auth') {
    require_once __DIR__ . '/endpoints/auth.php';
    handleAuth($action, $method);
    exit;
}

// ============== LISTAS ==============
if ($resource === 'lists') {
    require_once __DIR__ . '/endpoints/lists.php';
    
    // GET /lists/{id}/tasks
    if ($subResource === 'tasks' && $method === 'GET' && $action) {
        require_once __DIR__ . '/endpoints/tasks.php';
        getTasksByList($action);
        exit;
    }
    
    handleLists($action, $method);
    exit;
}

// ============== TAREFAS ==============
if ($resource === 'tasks') {
    require_once __DIR__ . '/endpoints/tasks.php';
    handleTasks($action, $method);
    exit;
}

// ============== SINCRONIZAÇÃO ==============
if ($resource === 'sync') {
    require_once __DIR__ . '/endpoints/sync.php';
    handleSync($action, $method);
    exit;
}

// Rota não encontrada
jsonResponse(false, 'Endpoint não encontrado', [
    'requested_path' => $path,
    'resource' => $resource,
    'action' => $action
], 404);
