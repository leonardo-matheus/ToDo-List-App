<?php
/**
 * Funções JWT simples (sem dependências externas)
 * Todo App - API PHP
 */

function base64UrlEncode(string $data): string {
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}

function base64UrlDecode(string $data): string {
    return base64_decode(strtr($data, '-_', '+/'));
}

function createJWT(array $payload): string {
    $header = ['typ' => 'JWT', 'alg' => 'HS256'];
    
    $payload['iat'] = time();
    $payload['exp'] = time() + JWT_EXPIRATION;
    
    $headerEncoded = base64UrlEncode(json_encode($header));
    $payloadEncoded = base64UrlEncode(json_encode($payload));
    
    $signature = hash_hmac('sha256', "$headerEncoded.$payloadEncoded", JWT_SECRET, true);
    $signatureEncoded = base64UrlEncode($signature);
    
    return "$headerEncoded.$payloadEncoded.$signatureEncoded";
}

function verifyJWT(string $token): ?array {
    $parts = explode('.', $token);
    
    if (count($parts) !== 3) {
        return null;
    }
    
    [$headerEncoded, $payloadEncoded, $signatureEncoded] = $parts;
    
    $signature = base64UrlDecode($signatureEncoded);
    $expectedSignature = hash_hmac('sha256', "$headerEncoded.$payloadEncoded", JWT_SECRET, true);
    
    if (!hash_equals($expectedSignature, $signature)) {
        return null;
    }
    
    $payload = json_decode(base64UrlDecode($payloadEncoded), true);
    
    if (!$payload || !isset($payload['exp']) || $payload['exp'] < time()) {
        return null;
    }
    
    return $payload;
}

function getAuthUser(): ?array {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';
    
    if (empty($authHeader) || !preg_match('/Bearer\s+(.+)/', $authHeader, $matches)) {
        return null;
    }
    
    return verifyJWT($matches[1]);
}

function requireAuth(): array {
    $user = getAuthUser();
    
    if (!$user) {
        jsonResponse(false, 'Token inválido ou expirado', null, 401);
    }
    
    return $user;
}
