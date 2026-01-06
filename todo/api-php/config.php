<?php
/**
 * Configurações do banco de dados e API
 * Todo App - API PHP
 */

// Configurações do Banco de Dados MySQL
define('DB_HOST', 'localhost');
define('DB_NAME', 'd3f4ltco_todoapp');
define('DB_USER', 'd3f4ltco_todoapp');
define('DB_PASS', 'xQJUR5pvrg3Kk26J5KFq');
define('DB_CHARSET', 'utf8mb4');

// Configurações da API
define('JWT_SECRET', 'sua_chave_secreta_muito_segura_aqui_12345!@#$%');
define('JWT_EXPIRATION', 86400 * 30); // 30 dias

// Configurações CORS
define('ALLOWED_ORIGINS', '*');

// Timezone
date_default_timezone_set('America/Sao_Paulo');

// Conexão PDO
function getDB(): PDO {
    static $pdo = null;
    
    if ($pdo === null) {
        $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET;
        
        $options = [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ];
        
        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
        } catch (PDOException $e) {
            http_response_code(500);
            die(json_encode(['success' => false, 'message' => 'Erro de conexão com o banco de dados']));
        }
    }
    
    return $pdo;
}

// Headers CORS e JSON
function setCorsHeaders(): void {
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With');
    header('Content-Type: application/json; charset=utf-8');
    
    if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
        http_response_code(200);
        exit;
    }
}

// Resposta JSON padrão
function jsonResponse(bool $success, string $message, $data = null, int $code = 200): void {
    http_response_code($code);
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data' => $data,
        'timestamp' => date('c')
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

// Obter dados JSON do request
function getJsonInput(): array {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    return is_array($data) ? $data : [];
}
