# Todo App - Android com Rust + Tauri

Um aplicativo de lista de tarefas completo para Android, desenvolvido com Rust (Tauri) e interface web moderna.

## Funcionalidades

- Registro e Login de usuários
- Criar múltiplas listas de tarefas
- Adicionar, editar e excluir tarefas
- Definir lembretes com notificações
- Marcar tarefas como concluídas
- Interface moderna e responsiva
- Dados persistidos localmente (SQLite)

## Pré-requisitos

### 1. Instalar Rust

```bash
# Windows (PowerShell)
winget install Rustlang.Rust.MSVC

# Ou via rustup
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

### 2. Instalar Tauri CLI

```bash
cargo install tauri-cli --version "^2.0"
```

### 3. Configurar Android SDK

#### Opção A: Instalar Android Studio (Recomendado)
1. Baixe e instale o [Android Studio](https://developer.android.com/studio)
2. Abra Android Studio > SDK Manager
3. Instale:
   - Android SDK Platform 34 (ou mais recente)
   - Android SDK Build-Tools
   - Android SDK Command-line Tools
   - NDK (Side by side) versão 26 ou superior

#### Opção B: Instalar via linha de comando
```bash
# Instalar sdkmanager e aceitar licenças
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125"
```

### 4. Configurar Variáveis de Ambiente

Adicione ao seu perfil do PowerShell (`$PROFILE`) ou variáveis de ambiente do sistema:

```powershell
# Editar perfil do PowerShell
notepad $PROFILE

# Adicionar estas linhas:
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:NDK_HOME = "$env:ANDROID_HOME\ndk\26.1.10909125"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH += ";$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\tools;$env:ANDROID_HOME\tools\bin"
```

Se instalou o Android Studio em local diferente, ajuste os caminhos.

### 5. Adicionar Targets do Rust para Android

```bash
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add i686-linux-android
rustup target add x86_64-linux-android
```

## Como Executar

### Desenvolvimento Desktop (para testar)

```bash
cd todo-app-tauri
cargo tauri dev
```

### Inicializar Projeto Android

```bash
cd todo-app-tauri/src-tauri
cargo tauri android init
```

### Desenvolvimento Android (com dispositivo/emulador conectado)

```bash
# Listar dispositivos disponíveis
adb devices

# Executar no dispositivo/emulador
cargo tauri android dev
```

### Gerar APK de Release

```bash
cd todo-app-tauri/src-tauri

# Build de debug (mais rápido, para testes)
cargo tauri android build

# Build de release (otimizado, para distribuição)
cargo tauri android build --release
```

O APK será gerado em:
```
src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk
```

## Estrutura do Projeto

```
todo-app-tauri/
├── dist/                    # Frontend (HTML/CSS/JS)
│   ├── index.html
│   ├── styles.css
│   └── app.js
├── src-tauri/              # Backend Rust
│   ├── Cargo.toml
│   ├── tauri.conf.json
│   ├── capabilities/
│   │   └── default.json
│   ├── src/
│   │   ├── lib.rs          # Lógica principal
│   │   └── main.rs
│   ├── icons/              # Ícones do app
│   └── gen/                # Código Android gerado
│       └── android/
├── package.json
└── README.md
```

## Assinando o APK para Play Store

### 1. Gerar Keystore

```bash
keytool -genkey -v -keystore todo-app-release.keystore -alias todo-app -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Configurar assinatura

Crie o arquivo `src-tauri/gen/android/keystore.properties`:

```properties
storePassword=sua_senha
keyPassword=sua_senha
keyAlias=todo-app
storeFile=../../../todo-app-release.keystore
```

### 3. Configurar build.gradle

Edite `src-tauri/gen/android/app/build.gradle.kts` para usar a keystore.

## Solução de Problemas

### Erro: NDK não encontrado
```bash
# Verificar se NDK está instalado
ls $env:ANDROID_HOME\ndk

# Instalar se necessário
sdkmanager "ndk;26.1.10909125"
```

### Erro: JAVA_HOME não configurado
```bash
# Verificar Java
java -version

# Usar o Java do Android Studio
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### Erro: Dispositivo não detectado
```bash
# Verificar conexão USB
adb devices

# Reiniciar servidor ADB
adb kill-server
adb start-server
```

### Erro: Permissões no Linux/Mac
```bash
# Dar permissão de execução
chmod +x ./gradlew
```

## Comandos Úteis

```bash
# Limpar build anterior
cd src-tauri/gen/android && ./gradlew clean

# Ver logs do dispositivo
adb logcat | grep -i tauri

# Instalar APK manualmente
adb install -r app-universal-release.apk

# Desinstalar app do dispositivo
adb uninstall com.todoapp.android
```

## Tecnologias Utilizadas

- **Tauri 2.0**: Framework para apps desktop/mobile com Rust
- **Rust**: Backend seguro e performático
- **SQLite**: Banco de dados local
- **HTML/CSS/JS**: Interface do usuário
- **bcrypt**: Hash seguro de senhas

## Licença

MIT License
