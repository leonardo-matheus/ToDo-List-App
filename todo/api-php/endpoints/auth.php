<?php
/**
 * Endpoints de Autenticação
 * Todo App - API PHP
 */

function handleAuth(?string $action, string $method): void {
    switch ($action) {
        case 'register':
            if ($method === 'POST') register();
            break;
        case 'verify':
            if ($method === 'POST') verifyEmail();
            break;
        case 'resend-code':
            if ($method === 'POST') resendCode();
            break;
        case 'forgot-password':
            if ($method === 'POST') forgotPassword();
            break;
        case 'verify-reset-code':
            if ($method === 'POST') verifyResetCode();
            break;
        case 'reset-password':
            if ($method === 'POST') resetPassword();
            break;
        case 'login':
            if ($method === 'POST') login();
            break;
        case 'me':
            if ($method === 'GET') me();
            break;
        case 'update-username':
            if ($method === 'PUT') updateUsername();
            break;
        case 'update-email':
            if ($method === 'PUT') updateEmail();
            break;
        case 'update-password':
            if ($method === 'PUT') updatePassword();
            break;
        default:
            jsonResponse(false, 'Ação de autenticação inválida', null, 400);
    }
}

function register(): void {
    $data = getJsonInput();
    
    $username = trim($data['username'] ?? '');
    $email = trim($data['email'] ?? '');
    $password = $data['password'] ?? '';
    
    // Validações
    if (empty($username) || empty($email) || empty($password)) {
        jsonResponse(false, 'Preencha todos os campos', null, 400);
    }
    
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        jsonResponse(false, 'Email inválido', null, 400);
    }
    
    if (strlen($password) < 6) {
        jsonResponse(false, 'Senha deve ter no mínimo 6 caracteres', null, 400);
    }
    
    $pdo = getDB();
    
    // Verificar se email já existe
    $stmt = $pdo->prepare("SELECT id, is_verified FROM users WHERE email = ?");
    $stmt->execute([$email]);
    $existing = $stmt->fetch();
    
    if ($existing) {
        if ($existing['is_verified']) {
            jsonResponse(false, 'Email já cadastrado', null, 409);
        } else {
            // Usuário existe mas não verificou, reenviar código
            $code = generateVerificationCode();
            $stmt = $pdo->prepare("UPDATE users SET verification_code = ?, code_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE) WHERE id = ?");
            $stmt->execute([$code, $existing['id']]);
            sendVerificationEmail($email, $username, $code);
            jsonResponse(true, 'Código reenviado para o email', ['email' => $email, 'requires_verification' => true]);
        }
    }
    
    // Criar usuário
    $id = generateUUID();
    $passwordHash = password_hash($password, PASSWORD_BCRYPT);
    $createdAt = date('Y-m-d H:i:s');
    $code = generateVerificationCode();
    
    $stmt = $pdo->prepare("
        INSERT INTO users (id, username, email, password_hash, created_at, is_verified, verification_code, code_expires_at) 
        VALUES (?, ?, ?, ?, ?, 0, ?, DATE_ADD(NOW(), INTERVAL 15 MINUTE))
    ");
    
    $stmt->execute([$id, $username, $email, $passwordHash, $createdAt, $code]);
    
    // Enviar email
    sendVerificationEmail($email, $username, $code);
    
    jsonResponse(true, 'Código de verificação enviado para o email', [
        'email' => $email,
        'requires_verification' => true
    ], 201);
}

function verifyEmail(): void {
    $data = getJsonInput();
    
    $email = trim($data['email'] ?? '');
    $code = trim($data['code'] ?? '');
    
    if (empty($email) || empty($code)) {
        jsonResponse(false, 'Email e código são obrigatórios', null, 400);
    }
    
    $pdo = getDB();
    
    $stmt = $pdo->prepare("
        SELECT id, username, email, verification_code, code_expires_at, is_verified, created_at
        FROM users 
        WHERE email = ?
    ");
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    
    if (!$user) {
        jsonResponse(false, 'Email não encontrado', null, 404);
    }
    
    if ($user['is_verified']) {
        jsonResponse(false, 'Email já verificado', null, 400);
    }
    
    if ($user['verification_code'] !== $code) {
        jsonResponse(false, 'Código inválido', null, 400);
    }
    
    if (strtotime($user['code_expires_at']) < time()) {
        jsonResponse(false, 'Código expirado. Solicite um novo.', null, 400);
    }
    
    // Ativar conta
    $stmt = $pdo->prepare("UPDATE users SET is_verified = 1, verification_code = NULL, code_expires_at = NULL WHERE id = ?");
    $stmt->execute([$user['id']]);
    
    // Gerar token
    $token = createJWT([
        'user_id' => $user['id'],
        'email' => $user['email']
    ]);
    
    jsonResponse(true, 'Conta ativada com sucesso!', [
        'user' => [
            'id' => $user['id'],
            'username' => $user['username'],
            'email' => $user['email'],
            'created_at' => $user['created_at']
        ],
        'token' => $token
    ]);
}

function resendCode(): void {
    $data = getJsonInput();
    $email = trim($data['email'] ?? '');
    
    if (empty($email)) {
        jsonResponse(false, 'Email é obrigatório', null, 400);
    }
    
    $pdo = getDB();
    $stmt = $pdo->prepare("SELECT id, username, is_verified FROM users WHERE email = ?");
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    
    if (!$user) {
        jsonResponse(false, 'Email não encontrado', null, 404);
    }
    
    if ($user['is_verified']) {
        jsonResponse(false, 'Email já verificado', null, 400);
    }
    
    $code = generateVerificationCode();
    $stmt = $pdo->prepare("UPDATE users SET verification_code = ?, code_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE) WHERE id = ?");
    $stmt->execute([$code, $user['id']]);
    
    sendVerificationEmail($email, $user['username'], $code);
    
    jsonResponse(true, 'Novo código enviado para o email');
}

function generateVerificationCode(): string {
    return str_pad(random_int(0, 999999), 6, '0', STR_PAD_LEFT);
}

function sendVerificationEmail(string $email, string $username, string $code): void {
    $subject = "MyTudo - Código de Verificação: $code";
    
    $message = "
    <html>
    <head>
        <style>
            body { font-family: Arial, sans-serif; background: #f5f5f5; padding: 20px; }
            .container { max-width: 400px; margin: 0 auto; background: white; border-radius: 16px; padding: 32px; }
            .logo { text-align: center; color: #7C6FFF; font-size: 28px; font-weight: bold; margin-bottom: 24px; }
            .code { text-align: center; font-size: 36px; font-weight: bold; color: #333; letter-spacing: 8px; background: #f0f0f0; padding: 16px; border-radius: 8px; margin: 24px 0; }
            .text { color: #666; text-align: center; line-height: 1.6; }
            .footer { text-align: center; color: #999; font-size: 12px; margin-top: 24px; }
        </style>
    </head>
    <body>
        <div class='container'>
            <div class='logo'>MyTudo</div>
            <p class='text'>Olá <strong>$username</strong>!</p>
            <p class='text'>Use o código abaixo para ativar sua conta:</p>
            <div class='code'>$code</div>
            <p class='text'>Este código expira em <strong>15 minutos</strong>.</p>
            <p class='footer'>Se você não solicitou este código, ignore este email.</p>
        </div>
    </body>
    </html>
    ";
    
    $headers = "MIME-Version: 1.0\r\n";
    $headers .= "Content-type: text/html; charset=UTF-8\r\n";
    $headers .= "From: MyTudo <noreply@leonardomdev.me>\r\n";
    
    mail($email, $subject, $message, $headers);
}

function forgotPassword(): void {
    $data = getJsonInput();
    $email = trim($data['email'] ?? '');
    
    if (empty($email)) {
        jsonResponse(false, 'Email é obrigatório', null, 400);
    }
    
    $pdo = getDB();
    $stmt = $pdo->prepare("SELECT id, username FROM users WHERE email = ?");
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    
    if (!$user) {
        jsonResponse(false, 'Email não encontrado', null, 404);
    }
    
    $code = generateVerificationCode();
    $stmt = $pdo->prepare("UPDATE users SET verification_code = ?, code_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE) WHERE id = ?");
    $stmt->execute([$code, $user['id']]);
    
    sendPasswordResetEmail($email, $user['username'], $code);
    
    jsonResponse(true, 'Código de recuperação enviado para o email', ['email' => $email]);
}

function verifyResetCode(): void {
    $data = getJsonInput();
    $email = trim($data['email'] ?? '');
    $code = trim($data['code'] ?? '');
    
    if (empty($email) || empty($code)) {
        jsonResponse(false, 'Email e código são obrigatórios', null, 400);
    }
    
    $pdo = getDB();
    $stmt = $pdo->prepare("SELECT id, verification_code, code_expires_at FROM users WHERE email = ?");
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    
    if (!$user) {
        jsonResponse(false, 'Email não encontrado', null, 404);
    }
    
    // Comparação direta
    if ($user['verification_code'] !== $code) {
        jsonResponse(false, 'Código inválido', null, 400);
    }
    
    if (strtotime($user['code_expires_at']) < time()) {
        jsonResponse(false, 'Código expirado. Solicite um novo.', null, 400);
    }
    
    // Gerar token simples para reset
    $resetToken = sha1($email . time() . rand(1000, 9999));
    $stmt = $pdo->prepare("UPDATE users SET verification_code = ? WHERE id = ?");
    $stmt->execute([$resetToken, $user['id']]);
    
    jsonResponse(true, 'Código verificado', ['reset_token' => $resetToken]);
}

function resetPassword(): void {
    $data = getJsonInput();
    $email = trim($data['email'] ?? '');
    $resetToken = $data['reset_token'] ?? '';
    $newPassword = $data['new_password'] ?? '';
    $confirmPassword = $data['confirm_password'] ?? '';
    
    if (empty($email) || empty($resetToken) || empty($newPassword) || empty($confirmPassword)) {
        jsonResponse(false, 'Preencha todos os campos', null, 400);
    }
    
    if ($newPassword !== $confirmPassword) {
        jsonResponse(false, 'As senhas não coincidem', null, 400);
    }
    
    if (strlen($newPassword) < 6) {
        jsonResponse(false, 'Senha deve ter no mínimo 6 caracteres', null, 400);
    }
    
    $pdo = getDB();
    $stmt = $pdo->prepare("SELECT id, verification_code FROM users WHERE email = ?");
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    
    if (!$user || $user['verification_code'] !== $resetToken) {
        jsonResponse(false, 'Token inválido', null, 400);
    }
    
    $newHash = password_hash($newPassword, PASSWORD_BCRYPT);
    $stmt = $pdo->prepare("UPDATE users SET password_hash = ?, verification_code = NULL, code_expires_at = NULL WHERE id = ?");
    $stmt->execute([$newHash, $user['id']]);
    
    jsonResponse(true, 'Senha alterada com sucesso!');
}

function sendPasswordResetEmail(string $email, string $username, string $code): void {
    $subject = "MyTudo - Recuperar Senha: $code";
    
    $message = "
    <html>
    <head>
        <style>
            body { font-family: Arial, sans-serif; background: #f5f5f5; padding: 20px; }
            .container { max-width: 400px; margin: 0 auto; background: white; border-radius: 16px; padding: 32px; }
            .logo { text-align: center; color: #7C6FFF; font-size: 28px; font-weight: bold; margin-bottom: 24px; }
            .code { text-align: center; font-size: 36px; font-weight: bold; color: #333; letter-spacing: 8px; background: #f0f0f0; padding: 16px; border-radius: 8px; margin: 24px 0; }
            .text { color: #666; text-align: center; line-height: 1.6; }
            .footer { text-align: center; color: #999; font-size: 12px; margin-top: 24px; }
        </style>
    </head>
    <body>
        <div class='container'>
            <div class='logo'>MyTudo</div>
            <p class='text'>Olá <strong>$username</strong>!</p>
            <p class='text'>Use o código abaixo para recuperar sua senha:</p>
            <div class='code'>$code</div>
            <p class='text'>Este código expira em <strong>15 minutos</strong>.</p>
            <p class='footer'>Se você não solicitou a recuperação de senha, ignore este email.</p>
        </div>
    </body>
    </html>
    ";
    
    $headers = "MIME-Version: 1.0\r\n";
    $headers .= "Content-type: text/html; charset=UTF-8\r\n";
    $headers .= "From: MyTudo <noreply@leonardomdev.me>\r\n";
    
    mail($email, $subject, $message, $headers);
}

function login(): void {
    $data = getJsonInput();
    
    $email = trim($data['email'] ?? '');
    $password = $data['password'] ?? '';
    
    if (empty($email) || empty($password)) {
        jsonResponse(false, 'Preencha email e senha', null, 400);
    }
    
    $pdo = getDB();
    
    $stmt = $pdo->prepare("
        SELECT id, username, email, password_hash, created_at, is_verified 
        FROM users 
        WHERE email = ?
    ");
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    
    if (!$user || !password_verify($password, $user['password_hash'])) {
        jsonResponse(false, 'Email ou senha incorretos', null, 401);
    }
    
    // Verificar se conta está ativada
    if (!$user['is_verified']) {
        // Reenviar código
        $code = generateVerificationCode();
        $stmt = $pdo->prepare("UPDATE users SET verification_code = ?, code_expires_at = DATE_ADD(NOW(), INTERVAL 15 MINUTE) WHERE id = ?");
        $stmt->execute([$code, $user['id']]);
        sendVerificationEmail($email, $user['username'], $code);
        
        jsonResponse(false, 'Conta não verificada. Código enviado para o email.', ['requires_verification' => true, 'email' => $email], 403);
    }
    
    // Gerar token
    $token = createJWT([
        'user_id' => $user['id'],
        'email' => $user['email']
    ]);
    
    jsonResponse(true, 'Login realizado com sucesso', [
        'user' => [
            'id' => $user['id'],
            'username' => $user['username'],
            'email' => $user['email'],
            'created_at' => $user['created_at']
        ],
        'token' => $token
    ]);
}

function me(): void {
    $auth = requireAuth();
    
    $pdo = getDB();
    $stmt = $pdo->prepare("
        SELECT id, username, email, created_at 
        FROM users 
        WHERE id = ?
    ");
    $stmt->execute([$auth['user_id']]);
    $user = $stmt->fetch();
    
    if (!$user) {
        jsonResponse(false, 'Usuário não encontrado', null, 404);
    }
    
    jsonResponse(true, 'Dados do usuário', $user);
}

function updateUsername(): void {
    $auth = requireAuth();
    $data = getJsonInput();
    
    $username = trim($data['username'] ?? '');
    
    if (empty($username)) {
        jsonResponse(false, 'Nome de usuário é obrigatório', null, 400);
    }
    
    if (strlen($username) < 3) {
        jsonResponse(false, 'Nome deve ter no mínimo 3 caracteres', null, 400);
    }
    
    $pdo = getDB();
    $stmt = $pdo->prepare("UPDATE users SET username = ? WHERE id = ?");
    $stmt->execute([$username, $auth['user_id']]);
    
    jsonResponse(true, 'Nome atualizado com sucesso', ['username' => $username]);
}

function updateEmail(): void {
    $auth = requireAuth();
    $data = getJsonInput();
    
    $email = trim($data['email'] ?? '');
    $password = $data['password'] ?? '';
    
    if (empty($email)) {
        jsonResponse(false, 'Email é obrigatório', null, 400);
    }
    
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        jsonResponse(false, 'Email inválido', null, 400);
    }
    
    if (empty($password)) {
        jsonResponse(false, 'Senha atual é obrigatória', null, 400);
    }
    
    $pdo = getDB();
    
    // Verificar senha atual
    $stmt = $pdo->prepare("SELECT password_hash FROM users WHERE id = ?");
    $stmt->execute([$auth['user_id']]);
    $user = $stmt->fetch();
    
    if (!password_verify($password, $user['password_hash'])) {
        jsonResponse(false, 'Senha incorreta', null, 401);
    }
    
    // Verificar se email já existe
    $stmt = $pdo->prepare("SELECT id FROM users WHERE email = ? AND id != ?");
    $stmt->execute([$email, $auth['user_id']]);
    if ($stmt->fetch()) {
        jsonResponse(false, 'Email já está em uso', null, 409);
    }
    
    $stmt = $pdo->prepare("UPDATE users SET email = ? WHERE id = ?");
    $stmt->execute([$email, $auth['user_id']]);
    
    // Gerar novo token com email atualizado
    $token = createJWT([
        'user_id' => $auth['user_id'],
        'email' => $email
    ]);
    
    jsonResponse(true, 'Email atualizado com sucesso', ['email' => $email, 'token' => $token]);
}

function updatePassword(): void {
    $auth = requireAuth();
    $data = getJsonInput();
    
    $currentPassword = $data['current_password'] ?? '';
    $newPassword = $data['new_password'] ?? '';
    $confirmPassword = $data['confirm_password'] ?? '';
    
    if (empty($currentPassword) || empty($newPassword) || empty($confirmPassword)) {
        jsonResponse(false, 'Preencha todos os campos', null, 400);
    }
    
    if ($newPassword !== $confirmPassword) {
        jsonResponse(false, 'As senhas não coincidem', null, 400);
    }
    
    if (strlen($newPassword) < 6) {
        jsonResponse(false, 'Senha deve ter no mínimo 6 caracteres', null, 400);
    }
    
    $pdo = getDB();
    
    // Verificar senha atual
    $stmt = $pdo->prepare("SELECT password_hash FROM users WHERE id = ?");
    $stmt->execute([$auth['user_id']]);
    $user = $stmt->fetch();
    
    if (!password_verify($currentPassword, $user['password_hash'])) {
        jsonResponse(false, 'Senha atual incorreta', null, 401);
    }
    
    // Atualizar senha
    $newHash = password_hash($newPassword, PASSWORD_BCRYPT);
    $stmt = $pdo->prepare("UPDATE users SET password_hash = ? WHERE id = ?");
    $stmt->execute([$newHash, $auth['user_id']]);
    
    jsonResponse(true, 'Senha atualizada com sucesso');
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
