# Todo App - API PHP

API REST para sincronização do Todo App com MySQL.

## Instalação

1. Faça upload de todos os arquivos para:
   ```
   /domains/todoapp.leonardomdev.me/public_html/
   ```

2. Acesse o instalador para criar as tabelas:
   ```
   https://todoapp.leonardomdev.me/install.php
   ```

3. Pronto! A API estará funcionando.

## Endpoints

### Autenticação

```
POST /auth/register
Body: { "username": "nome", "email": "email@ex.com", "password": "123456" }

POST /auth/login
Body: { "email": "email@ex.com", "password": "123456" }

GET /auth/me
Header: Authorization: Bearer {token}
```

### Listas

```
GET /lists
POST /lists
PUT /lists/{id}
DELETE /lists/{id}
GET /lists/{id}/tasks
```

### Tarefas

```
GET /tasks
POST /tasks
PUT /tasks/{id}
DELETE /tasks/{id}
```

### Sincronização

```
POST /sync/push   - Envia dados do app para o servidor
POST /sync/pull   - Baixa dados do servidor
POST /sync/full   - Sincronização completa (push + pull)
```

## Exemplo de Sincronização

```javascript
// Push - Enviar dados locais
fetch('https://todoapp.leonardomdev.me/sync/push', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token
    },
    body: JSON.stringify({
        lists: [...],
        tasks: [...],
        deleted_lists: ['id1', 'id2'],
        deleted_tasks: ['id3', 'id4']
    })
});

// Pull - Baixar dados
fetch('https://todoapp.leonardomdev.me/sync/pull', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token
    },
    body: JSON.stringify({
        last_sync: '2024-01-01T00:00:00Z' // opcional
    })
});
```

## Estrutura de Arquivos

```
public_html/
├── .htaccess
├── config.php
├── jwt.php
├── index.php
├── install.php
└── endpoints/
    ├── auth.php
    ├── lists.php
    ├── tasks.php
    └── sync.php
```
