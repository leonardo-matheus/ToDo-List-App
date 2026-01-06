# ============================================
# Script de Configuração - Todo App Tauri
# Execute como Administrador se necessário
# ============================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Setup Todo App - Android com Tauri" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Verificar Rust
Write-Host "[1/5] Verificando Rust..." -ForegroundColor Yellow
$rustVersion = rustc --version 2>$null
if ($rustVersion) {
    Write-Host "  Rust instalado: $rustVersion" -ForegroundColor Green
} else {
    Write-Host "  Rust nao encontrado. Instalando..." -ForegroundColor Red
    Write-Host "  Execute: winget install Rustlang.Rust.MSVC" -ForegroundColor Yellow
    Write-Host "  Apos instalar, reinicie o terminal e execute novamente." -ForegroundColor Yellow
    exit 1
}

# Verificar Tauri CLI
Write-Host ""
Write-Host "[2/5] Verificando Tauri CLI..." -ForegroundColor Yellow
$tauriVersion = cargo tauri --version 2>$null
if ($tauriVersion) {
    Write-Host "  Tauri CLI instalado: $tauriVersion" -ForegroundColor Green
} else {
    Write-Host "  Instalando Tauri CLI..." -ForegroundColor Yellow
    cargo install tauri-cli --version "^2.0"
}

# Adicionar targets Android
Write-Host ""
Write-Host "[3/5] Adicionando targets Android ao Rust..." -ForegroundColor Yellow
rustup target add aarch64-linux-android 2>$null
rustup target add armv7-linux-androideabi 2>$null
rustup target add i686-linux-android 2>$null
rustup target add x86_64-linux-android 2>$null
Write-Host "  Targets Android adicionados!" -ForegroundColor Green

# Verificar Android SDK
Write-Host ""
Write-Host "[4/5] Verificando Android SDK..." -ForegroundColor Yellow

$androidHome = $env:ANDROID_HOME
if (-not $androidHome) {
    $androidHome = "$env:LOCALAPPDATA\Android\Sdk"
}

if (Test-Path $androidHome) {
    Write-Host "  Android SDK encontrado: $androidHome" -ForegroundColor Green
    
    # Verificar NDK
    $ndkPath = Get-ChildItem "$androidHome\ndk" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ($ndkPath) {
        Write-Host "  NDK encontrado: $($ndkPath.FullName)" -ForegroundColor Green
        $env:NDK_HOME = $ndkPath.FullName
    } else {
        Write-Host "  NDK nao encontrado!" -ForegroundColor Red
        Write-Host "  Abra Android Studio > SDK Manager > SDK Tools" -ForegroundColor Yellow
        Write-Host "  Marque 'NDK (Side by side)' e clique em Apply" -ForegroundColor Yellow
    }
} else {
    Write-Host "  Android SDK nao encontrado!" -ForegroundColor Red
    Write-Host "  Instale Android Studio: https://developer.android.com/studio" -ForegroundColor Yellow
    exit 1
}

# Verificar Java
Write-Host ""
Write-Host "[5/5] Verificando Java..." -ForegroundColor Yellow

$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    # Tentar encontrar Java do Android Studio
    $possibleJava = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jre",
        "$env:ProgramFiles\Java\jdk-*"
    )
    
    foreach ($path in $possibleJava) {
        $found = Get-Item $path -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $javaHome = $found.FullName
            break
        }
    }
}

if ($javaHome -and (Test-Path $javaHome)) {
    Write-Host "  Java encontrado: $javaHome" -ForegroundColor Green
} else {
    Write-Host "  Java nao encontrado!" -ForegroundColor Red
    Write-Host "  JAVA_HOME deve apontar para o JDK do Android Studio" -ForegroundColor Yellow
}

# Configurar variaveis de ambiente
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Configurando Variaveis de Ambiente" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$configEnv = Read-Host "Deseja configurar variaveis de ambiente automaticamente? (S/N)"
if ($configEnv -eq "S" -or $configEnv -eq "s") {
    
    [Environment]::SetEnvironmentVariable("ANDROID_HOME", $androidHome, "User")
    Write-Host "  ANDROID_HOME = $androidHome" -ForegroundColor Green
    
    if ($ndkPath) {
        [Environment]::SetEnvironmentVariable("NDK_HOME", $ndkPath.FullName, "User")
        Write-Host "  NDK_HOME = $($ndkPath.FullName)" -ForegroundColor Green
    }
    
    if ($javaHome) {
        [Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
        Write-Host "  JAVA_HOME = $javaHome" -ForegroundColor Green
    }
    
    Write-Host ""
    Write-Host "  Variaveis configuradas! Reinicie o terminal." -ForegroundColor Yellow
}

# Resumo final
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Proximos Passos" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Reinicie o terminal (PowerShell/CMD)" -ForegroundColor White
Write-Host ""
Write-Host "2. Navegue ate o projeto:" -ForegroundColor White
Write-Host "   cd todo-app-tauri/src-tauri" -ForegroundColor Yellow
Write-Host ""
Write-Host "3. Inicialize o projeto Android:" -ForegroundColor White
Write-Host "   cargo tauri android init" -ForegroundColor Yellow
Write-Host ""
Write-Host "4. Gere o APK:" -ForegroundColor White
Write-Host "   cargo tauri android build" -ForegroundColor Yellow
Write-Host ""
Write-Host "5. O APK estara em:" -ForegroundColor White
Write-Host "   src-tauri/gen/android/app/build/outputs/apk/" -ForegroundColor Yellow
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
