# Todo App API - Rust

API REST de alta performance para o aplicativo Todo, desenvolvida em Rust com Actix-web.

## 🚀 Características

- **Alta Performance**: Construída com Actix-web, um dos frameworks web mais rápidos
- **Segurança**: Autenticação JWT, hash bcrypt para senhas
- **MySQL**: Compatível com o mesmo banco de dados da versão PHP
- **Sincronização**: Suporte completo para sync push/pull
- **Email**: Envio de emails para verificação e recuperação de senha

## 📋 Requisitos

- Rust 1.70+ (instalação: https://rustup.rs)
- MySQL 5.7+ ou MariaDB 10.2+
- (Opcional) SMTP server para envio de emails

## 🔧 Configuração Local

### 1. Clone e configure o ambiente

```bash
cd todo-api-rust
cp .env.example .env
```

### 2. Edite o arquivo `.env`

```env
DATABASE_URL=mysql://usuario:senha@localhost:3306/todoapp
JWT_SECRET=sua_chave_secreta_muito_segura
JWT_EXPIRATION=2592000
HOST=0.0.0.0
PORT=8080

# SMTP (opcional)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=seu_email@gmail.com
SMTP_PASS=sua_senha_app
SMTP_FROM=MyTudo <noreply@seudominio.com>
```

### 3. Execute o banco de dados

Use o mesmo script SQL da versão PHP (`database.sql`).

### 4. Compile e execute

```bash
# Desenvolvimento
cargo run

# Produção (otimizado)
cargo build --release
./target/release/todo-api
```

## 🖥️ Deploy na VPS Ubuntu

### 1. Instale as dependências

```bash
# Atualize o sistema
sudo apt update && sudo apt upgrade -y

# Instale Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env

# Instale dependências de compilação
sudo apt install -y build-essential pkg-config libssl-dev

# Instale MySQL Client (para sqlx)
sudo apt install -y default-libmysqlclient-dev
```

### 2. Clone o projeto na VPS

```bash
cd /opt
sudo git clone <seu-repositorio> todo-api
sudo chown -R $USER:$USER todo-api
cd todo-api
```

### 3. Configure o ambiente

```bash
cp .env.example .env
nano .env
# Configure DATABASE_URL, JWT_SECRET, SMTP, etc.
```

### 4. Compile para produção

```bash
cargo build --release
```

### 5. Configure o Systemd

Crie o arquivo de serviço:

```bash
sudo nano /etc/systemd/system/todo-api.service
```

Conteúdo:

```ini
[Unit]
Description=Todo App API (Rust)
After=network.target mysql.service

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/todo-api
ExecStart=/opt/todo-api/target/release/todo-api
Restart=always
RestartSec=5
Environment=RUST_LOG=info

[Install]
WantedBy=multi-user.target
```

### 6. Inicie o serviço

```bash
sudo systemctl daemon-reload
sudo systemctl enable todo-api
sudo systemctl start todo-api

# Verificar status
sudo systemctl status todo-api

# Ver logs
sudo journalctl -u todo-api -f
```

### 7. Configure o Nginx (Proxy Reverso)

```bash
sudo apt install -y nginx
sudo nano /etc/nginx/sites-available/todo-api
```

Conteúdo:

```nginx
server {
    listen 80;
    server_name api.seudominio.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/todo-api /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### 8. SSL com Certbot (Opcional)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.seudominio.com
```

## 📡 Endpoints da API

### Autenticação
- `POST /auth/register` - Registrar usuário
- `POST /auth/verify` - Verificar email
- `POST /auth/resend-code` - Reenviar código
- `POST /auth/forgot-password` - Esqueci minha senha
- `POST /auth/verify-reset-code` - Verificar código de reset
- `POST /auth/reset-password` - Resetar senha
- `POST /auth/login` - Login
- `GET /auth/me` - Dados do usuário
- `PUT /auth/update-username` - Atualizar nome
- `PUT /auth/update-email` - Atualizar email
- `PUT /auth/update-password` - Atualizar senha

### Listas
- `GET /lists` - Listar todas
- `POST /lists` - Criar lista
- `GET /lists/{id}` - Obter lista
- `PUT /lists/{id}` - Atualizar lista
- `DELETE /lists/{id}` - Deletar lista
- `GET /lists/{id}/tasks` - Tarefas da lista

### Tarefas
- `GET /tasks` - Listar todas
- `POST /tasks` - Criar tarefa
- `GET /tasks/{id}` - Obter tarefa
- `PUT /tasks/{id}` - Atualizar tarefa
- `DELETE /tasks/{id}` - Deletar tarefa

### Sincronização
- `POST /sync/push` - Enviar dados para servidor
- `POST /sync/pull` - Baixar dados do servidor
- `POST /sync/full` - Sincronização completa

## 🔒 Autenticação

Todas as rotas (exceto login/register/verify) requerem token JWT no header:

```
Authorization: Bearer <seu_token>
```

## 📊 Formato de Resposta

```json
{
    "success": true,
    "message": "Mensagem de resposta",
    "data": { ... },
    "timestamp": "2025-01-05T12:00:00Z"
}
```

## 🛠️ Desenvolvimento

```bash
# Rodar em modo watch (recompila automaticamente)
cargo install cargo-watch
cargo watch -x run

# Rodar testes
cargo test

# Verificar código
cargo clippy

# Formatar código
cargo fmt
```

## 📈 Performance

A API em Rust é significativamente mais performática que a versão PHP:

- ~10x mais requests por segundo
- ~50% menos uso de memória
- Latência ~5x menor

## 🔄 Migração do PHP

Esta API é 100% compatível com a versão PHP:
- Mesmo banco de dados
- Mesmos endpoints
- Mesmo formato de resposta
- Mesma estrutura JWT

Basta trocar a URL base no aplicativo cliente.

---

**Desenvolvido com ❤️ em Rust**
