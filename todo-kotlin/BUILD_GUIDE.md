# 📱 Guia de Build e Deploy - Todo App Kotlin

## ⚙️ Pré-requisitos

### 1. Instalar Android Studio
- Baixe em: https://developer.android.com/studio
- Durante instalação, inclua:
  - Android SDK
  - Android SDK Platform-Tools
  - Android SDK Build-Tools

### 2. Configurar Variáveis de Ambiente
Adicione ao PATH do Windows:
```
C:\Users\SEU_USUARIO\AppData\Local\Android\Sdk\platform-tools
```

### 3. Instalar JDK 17
- Baixe o OpenJDK 17 ou use o JDK bundled do Android Studio

---

## 📲 Preparar o Galaxy S20

### 1. Ativar Opções de Desenvolvedor
1. Vá em **Configurações** > **Sobre o telefone**
2. Toque **7 vezes** em "Número da versão"
3. Aparecerá: "Você agora é um desenvolvedor!"

### 2. Habilitar Depuração USB
1. Vá em **Configurações** > **Opções do desenvolvedor**
2. Ative **Depuração USB**
3. (Opcional) Ative **Instalar via USB**

### 3. Conectar USB
1. Conecte o cabo USB ao PC
2. No celular, escolha **Transferir arquivos / MTP**
3. Aceite o popup de autorização de depuração

### 4. Verificar Conexão
```powershell
adb devices
```
Deve mostrar algo como:
```
List of devices attached
RF8N31XXXXX    device
```

---

## 🔧 Configurar o Projeto

### 1. Atualizar URL da API
Edite `app/src/main/java/com/example/todo/data/api/RetrofitInstance.kt`:
```kotlin
private const val BASE_URL = "https://SEU_DOMINIO.COM/api-php/"
```
Substitua pelo endereço real da sua API PHP.

### 2. Configurar Keystore (para Release)
Crie um arquivo `app/keystore.properties`:
```properties
storePassword=sua_senha
keyPassword=sua_senha
keyAlias=todo_app
storeFile=../keystore/todo_release.jks
```

---

## 🚀 Comandos de Build

### Método 1: Via Android Studio (Recomendado)

1. Abra o Android Studio
2. File > Open > Selecione a pasta `todo-kotlin`
3. Aguarde o Gradle sync
4. Selecione seu dispositivo no dropdown
5. Clique no botão ▶️ Run

### Método 2: Via Terminal (PowerShell)

#### Build Debug e Instalar
```powershell
cd c:\Users\Windows\Desktop\todo\todo-kotlin

# Limpar builds anteriores
.\gradlew clean

# Build debug e instalar no dispositivo conectado
.\gradlew installDebug
```

#### Apenas Gerar APK Debug
```powershell
.\gradlew assembleDebug
```
APK gerado em: `app/build/outputs/apk/debug/app-debug.apk`

#### Build Release (Assinado)
```powershell
.\gradlew assembleRelease
```
APK gerado em: `app/build/outputs/apk/release/app-release.apk`

#### Build e Instalar em Um Comando
```powershell
.\gradlew clean installDebug
```

---

## 📦 Instalar APK Manualmente

### Via ADB
```powershell
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Reinstalar (substituir versão existente)
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🐛 Debugging

### Ver Logs do App
```powershell
adb logcat -s "TodoApp"
```

### Ver Todos os Logs
```powershell
adb logcat
```

### Filtrar Erros
```powershell
adb logcat *:E
```

---

## ❗ Problemas Comuns

### "Device unauthorized"
1. Desconecte o USB
2. Revogue autorizações: **Opções do desenvolvedor** > **Revogar autorizações de depuração USB**
3. Reconecte e aceite o popup

### "INSTALL_FAILED_USER_RESTRICTED"
1. **Configurações** > **Opções do desenvolvedor**
2. Ative **Instalar via USB**
3. Tente novamente

### "SDK location not found"
Crie um arquivo `local.properties` na raiz do projeto:
```properties
sdk.dir=C\:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk
```

### Gradle sync failed
```powershell
.\gradlew --refresh-dependencies
```

### App não conecta na API
1. Verifique se o celular tem acesso à internet
2. Confirme que a URL da API está correta em `RetrofitInstance.kt`
3. Se usando localhost, use o IP da máquina (ex: `http://192.168.1.100/api-php/`)

---

## 📋 Checklist Final

- [ ] Android Studio instalado
- [ ] ADB funcionando (`adb devices` mostra dispositivo)
- [ ] Depuração USB ativada no Galaxy S20
- [ ] URL da API configurada em `RetrofitInstance.kt`
- [ ] Gradle sync concluído sem erros
- [ ] Build e install funcionando

---

## 🔐 Gerar APK de Release Assinado

### 1. Criar Keystore
```powershell
keytool -genkey -v -keystore todo_release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias todo_app
```

### 2. Configurar no build.gradle.kts
Já está configurado para ler de `keystore.properties`.

### 3. Build Release
```powershell
.\gradlew assembleRelease
```

---

## 📱 Comandos Úteis

| Comando | Descrição |
|---------|-----------|
| `.\gradlew tasks` | Lista todas as tasks disponíveis |
| `.\gradlew clean` | Limpa todos os builds |
| `.\gradlew assembleDebug` | Gera APK debug |
| `.\gradlew assembleRelease` | Gera APK release |
| `.\gradlew installDebug` | Build e instala no dispositivo |
| `adb devices` | Lista dispositivos conectados |
| `adb install <apk>` | Instala APK no dispositivo |
| `adb shell am start -n com.example.todo/.ui.main.MainActivity` | Abre o app |
| `adb uninstall com.example.todo` | Desinstala o app |

---

**Pronto!** Agora você pode compilar e instalar o app no seu Galaxy S20! 🎉
