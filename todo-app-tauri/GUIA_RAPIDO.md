# Guia Rápido - Gerar APK Android

## Passo a Passo Simplificado

### 1. Pré-requisitos (instalar uma vez)

```powershell
# 1. Instalar Rust (se ainda não tem)
winget install Rustlang.Rust.MSVC

# 2. Reiniciar o terminal e instalar Tauri CLI
cargo install tauri-cli --version "^2.0"

# 3. Adicionar targets Android
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

# 4. Instalar Android Studio de: https://developer.android.com/studio
# Durante instalação, marcar Android SDK e Android Virtual Device

# 5. Após instalar Android Studio, abrir e ir em:
# Tools > SDK Manager > SDK Tools > Marcar "NDK (Side by side)" > Apply
```

### 2. Configurar Variáveis de Ambiente

Abra PowerShell como Administrador e execute:

```powershell
# Definir variáveis de ambiente permanentemente
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("NDK_HOME", "$env:LOCALAPPDATA\Android\Sdk\ndk\26.1.10909125", "User")
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")

# Adicionar ao PATH
$currentPath = [Environment]::GetEnvironmentVariable("PATH", "User")
$androidPaths = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:LOCALAPPDATA\Android\Sdk\tools"
[Environment]::SetEnvironmentVariable("PATH", "$currentPath;$androidPaths", "User")
```

**IMPORTANTE:** Feche e reabra o terminal após configurar.

### 3. Inicializar Android no Projeto

```powershell
cd todo-app-tauri/src-tauri

# Inicializar configuração Android
cargo tauri android init
```

### 4. Gerar o APK

```powershell
# Build do APK (pode demorar na primeira vez)
cargo tauri android build

# Ou para versão release otimizada
cargo tauri android build --release
```

### 5. Localizar o APK

O APK estará em:
```
todo-app-tauri/src-tauri/gen/android/app/build/outputs/apk/
```

Arquivos gerados:
- `universal/debug/app-universal-debug.apk` - Debug (maior, para testes)
- `universal/release/app-universal-release.apk` - Release (menor, otimizado)

### 6. Instalar no Celular

**Opção A - Via USB:**
```powershell
# Conecte o celular com depuração USB ativada
adb install -r caminho/para/app-universal-release.apk
```

**Opção B - Transferir arquivo:**
1. Copie o APK para o celular
2. Abra o gerenciador de arquivos no celular
3. Toque no APK para instalar
4. Permita "Instalar de fontes desconhecidas" se solicitado

---

## Testar no Emulador

```powershell
# Criar emulador (uma vez)
# Abra Android Studio > Device Manager > Create Device
# Escolha um dispositivo (ex: Pixel 6) e baixe uma imagem do sistema

# Executar no emulador
cargo tauri android dev
```

---

## Solução de Problemas Comuns

### "cargo: command not found"
```powershell
# Reinstalar Rust
winget install Rustlang.Rust.MSVC
# Reiniciar terminal
```

### "NDK not found"
```powershell
# Verificar versão do NDK instalada
ls $env:LOCALAPPDATA\Android\Sdk\ndk

# Atualizar NDK_HOME com a versão correta
$env:NDK_HOME = "$env:LOCALAPPDATA\Android\Sdk\ndk\SUA_VERSAO"
```

### "Unable to find Java"
```powershell
# Verificar se JAVA_HOME está correto
echo $env:JAVA_HOME

# Se Android Studio está instalado em local diferente, ajustar:
$env:JAVA_HOME = "CAMINHO\PARA\Android Studio\jbr"
```

### Build muito lento
A primeira build demora mais porque compila todas as dependências.
Builds subsequentes são muito mais rápidas.

### APK não instala
1. Ative "Fontes desconhecidas" nas configurações do celular
2. Se der erro de assinatura, use o APK de debug primeiro

---

## Recursos do App

O app inclui:
- Sistema de login/registro com senha criptografada (bcrypt)
- Múltiplas listas de tarefas com cores personalizadas
- Tarefas com título, descrição e lembretes
- Notificações de lembrete
- Marcar tarefas como concluídas
- Banco de dados local (SQLite)
- Interface dark mode moderna
