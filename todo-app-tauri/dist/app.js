// ============== CONFIGURAÇÃO DA API ==============
const API_URL = 'https://191-235-32-212.nip.io/rust-api';

// ============== ESTADO DA APLICAÇÃO ==============
let currentUser = null;
let currentList = null;
let lists = [];
let tasks = [];
let authToken = null;
let reminderCheckInterval = null;
let syncInterval = null;
let isOnline = navigator.onLine;

// ============== ELEMENTOS DOM ==============
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => document.querySelectorAll(selector);

const authScreen = $('#auth-screen');
const listsScreen = $('#lists-screen');
const tasksScreen = $('#tasks-screen');
const loginForm = $('#login-form');
const registerForm = $('#register-form');
const createListForm = $('#create-list-form');
const taskForm = $('#task-form');
const createListModal = $('#create-list-modal');
const taskModal = $('#task-modal');
const listsContainer = $('#lists-container');
const tasksContainer = $('#tasks-container');

// ============== STORAGE ==============
function saveToStorage(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) {}
}

function getFromStorage(key) {
    try { return JSON.parse(localStorage.getItem(key)); } catch (e) { return null; }
}

function saveAuthToken(token) { authToken = token; saveToStorage('auth_token', token); }
function loadAuthToken() { authToken = getFromStorage('auth_token'); return authToken; }
function clearAuthToken() { authToken = null; localStorage.removeItem('auth_token'); }

// ============== UTILIDADES ==============
function showScreen(screen) {
    $$('.screen').forEach(s => s.classList.remove('active'));
    screen.classList.add('active');
}

function showModal(modal) { modal.classList.add('active'); }
function hideModal(modal) { modal.classList.remove('active'); }

function showToast(message, type = 'success') {
    const toast = $('#toast');
    toast.textContent = message;
    toast.className = `toast ${type} show`;
    setTimeout(() => toast.classList.remove('show'), 3000);
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('pt-BR', {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
}

function formatDateTimeLocal(dateStr) {
    if (!dateStr) return '';
    return new Date(dateStr).toISOString().slice(0, 16);
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ============== API LOCAL (Tauri) ==============
async function invoke(cmd, args = {}) {
    try {
        return await window.__TAURI__.core.invoke(cmd, args);
    } catch (error) {
        console.error(`Erro ${cmd}:`, error);
        throw error;
    }
}

// ============== API REMOTA (PHP) ==============
async function apiRequest(endpoint, method = 'GET', body = null) {
    if (!isOnline) throw new Error('Offline');
    
    const headers = { 'Content-Type': 'application/json' };
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    
    const options = { method, headers };
    if (body && method !== 'GET') options.body = JSON.stringify(body);
    
    const response = await fetch(`${API_URL}/${endpoint}`, options);
    return await response.json();
}

// ============== SINCRONIZAÇÃO ==============
async function syncWithServer() {
    if (!isOnline || !authToken) return;
    
    try {
        const localListsRes = await invoke('get_lists');
        const localLists = localListsRes.success ? localListsRes.data : [];
        
        let localTasks = [];
        for (const list of localLists) {
            const tasksRes = await invoke('get_tasks', { listId: list.id });
            if (tasksRes.success) localTasks = localTasks.concat(tasksRes.data);
        }
        
        const syncRes = await apiRequest('sync/full', 'POST', {
            lists: localLists,
            tasks: localTasks,
            deleted_lists: getFromStorage('deleted_lists') || [],
            deleted_tasks: getFromStorage('deleted_tasks') || [],
            last_sync: getFromStorage('last_sync')
        });
        
        if (syncRes.success) {
            saveToStorage('deleted_lists', []);
            saveToStorage('deleted_tasks', []);
            saveToStorage('last_sync', syncRes.data.server_time);
            await loadLists();
            if (currentList) await loadTasks();
            console.log('Sync OK!');
        }
    } catch (error) {
        console.error('Sync error:', error);
    }
}

function startSyncInterval() {
    syncInterval = setInterval(syncWithServer, 120000);
    syncWithServer();
}

function stopSyncInterval() {
    if (syncInterval) { clearInterval(syncInterval); syncInterval = null; }
}

// ============== AUTENTICAÇÃO ==============
async function handleLogin(e) {
    e.preventDefault();
    const email = $('#login-email').value;
    const password = $('#login-password').value;

    try {
        const localRes = await invoke('login', { request: { email, password } });

        if (localRes.success) {
            currentUser = localRes.data;
            if (isOnline) {
                try {
                    const apiRes = await apiRequest('auth/login', 'POST', { email, password });
                    if (apiRes.success) saveAuthToken(apiRes.data.token);
                } catch (e) {}
            }
            afterLogin();
        } else if (isOnline) {
            const apiRes = await apiRequest('auth/login', 'POST', { email, password });
            if (apiRes.success) {
                saveAuthToken(apiRes.data.token);
                currentUser = apiRes.data.user;
                await invoke('register', { request: { username: currentUser.username, email, password } }).catch(() => {});
                afterLogin();
            } else {
                showToast(apiRes.message || 'Erro no login', 'error');
            }
        } else {
            showToast(localRes.message, 'error');
        }
    } catch (error) {
        showToast('Erro ao fazer login', 'error');
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const username = $('#register-username').value;
    const email = $('#register-email').value;
    const password = $('#register-password').value;

    try {
        const localRes = await invoke('register', { request: { username, email, password } });

        if (localRes.success) {
            currentUser = localRes.data;
            if (isOnline) {
                try {
                    const apiRes = await apiRequest('auth/register', 'POST', { username, email, password });
                    if (apiRes.success) saveAuthToken(apiRes.data.token);
                } catch (e) {}
            }
            afterLogin();
        } else {
            showToast(localRes.message, 'error');
        }
    } catch (error) {
        showToast('Erro ao registrar', 'error');
    }
}

async function afterLogin() {
    showToast('Login realizado!');
    await loadLists();
    showScreen(listsScreen);
    $('#welcome-user').textContent = `Olá, ${currentUser.username}!`;
    startReminderCheck();
    startSyncInterval();
}

async function handleLogout() {
    await invoke('logout');
    currentUser = null; currentList = null; lists = []; tasks = [];
    clearAuthToken(); stopReminderCheck(); stopSyncInterval();
    loginForm.reset(); registerForm.reset();
    showScreen(authScreen);
    showToast('Logout realizado');
}

// ============== LISTAS ==============
async function loadLists() {
    const response = await invoke('get_lists');
    if (response.success) { lists = response.data || []; renderLists(); }
}

function renderLists() {
    if (lists.length === 0) {
        listsContainer.innerHTML = `<div class="empty-state"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/></svg><h3>Nenhuma lista</h3><p>Toque + para criar</p></div>`;
        return;
    }

    listsContainer.innerHTML = lists.map(list => `
        <div class="list-card" data-id="${list.id}" style="border-left-color: ${list.color}">
            <div class="list-icon" style="background: ${list.color}">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg>
            </div>
            <div class="list-info"><h3>${escapeHtml(list.name)}</h3><p>${formatDate(list.created_at)}</p></div>
            <div class="list-arrow"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg></div>
        </div>
    `).join('');

    $$('.list-card').forEach(card => card.addEventListener('click', () => openList(card.dataset.id)));
}

async function handleCreateList(e) {
    e.preventDefault();
    const name = $('#list-name').value;
    const color = document.querySelector('input[name="list-color"]:checked').value;
    
    const response = await invoke('create_list', { request: { name, color } });
    if (response.success) {
        lists.unshift(response.data);
        renderLists();
        hideModal(createListModal);
        createListForm.reset();
        showToast('Lista criada!');
        syncWithServer();
    } else {
        showToast(response.message, 'error');
    }
}

async function deleteCurrentList() {
    if (!currentList || !confirm(`Excluir "${currentList.name}"?`)) return;
    
    const response = await invoke('delete_list', { listId: currentList.id });
    if (response.success) {
        const deleted = getFromStorage('deleted_lists') || [];
        deleted.push(currentList.id);
        saveToStorage('deleted_lists', deleted);
        lists = lists.filter(l => l.id !== currentList.id);
        currentList = null;
        renderLists();
        showScreen(listsScreen);
        showToast('Lista excluída');
        syncWithServer();
    }
}

function openList(listId) {
    currentList = lists.find(l => l.id === listId);
    if (!currentList) return;
    $('#current-list-name').textContent = currentList.name;
    loadTasks();
    showScreen(tasksScreen);
}

// ============== TAREFAS ==============
async function loadTasks() {
    if (!currentList) return;
    const response = await invoke('get_tasks', { listId: currentList.id });
    if (response.success) { tasks = response.data || []; renderTasks(); }
}

function renderTasks() {
    const completed = tasks.filter(t => t.completed).length;
    $('#tasks-count').textContent = `${tasks.length} tarefa${tasks.length !== 1 ? 's' : ''}`;
    $('#tasks-completed').textContent = `${completed} concluída${completed !== 1 ? 's' : ''}`;

    if (tasks.length === 0) {
        tasksContainer.innerHTML = `<div class="empty-state"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg><h3>Nenhuma tarefa</h3><p>Toque + para adicionar</p></div>`;
        return;
    }

    tasksContainer.innerHTML = tasks.map(task => `
        <div class="task-card ${task.completed ? 'completed' : ''}" data-id="${task.id}">
            <div class="task-checkbox ${task.completed ? 'checked' : ''}" data-id="${task.id}"></div>
            <div class="task-content">
                <div class="task-title">${escapeHtml(task.title)}</div>
                ${task.description ? `<div class="task-description">${escapeHtml(task.description)}</div>` : ''}
                ${task.reminder ? `<div class="task-reminder"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/></svg>${formatDate(task.reminder)}</div>` : ''}
            </div>
            <div class="task-actions">
                <button class="task-action-btn edit" data-id="${task.id}"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg></button>
                <button class="task-action-btn delete" data-id="${task.id}"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg></button>
            </div>
        </div>
    `).join('');

    $$('.task-checkbox').forEach(cb => cb.addEventListener('click', e => { e.stopPropagation(); toggleTask(cb.dataset.id); }));
    $$('.task-action-btn.edit').forEach(btn => btn.addEventListener('click', e => { e.stopPropagation(); editTask(btn.dataset.id); }));
    $$('.task-action-btn.delete').forEach(btn => btn.addEventListener('click', e => { e.stopPropagation(); deleteTask(btn.dataset.id); }));
}

function openTaskModal(task = null) {
    $('#task-modal-title').textContent = task ? 'Editar Tarefa' : 'Nova Tarefa';
    $('#task-id').value = task?.id || '';
    $('#task-title').value = task?.title || '';
    $('#task-description').value = task?.description || '';
    $('#task-reminder').value = task?.reminder ? formatDateTimeLocal(task.reminder) : '';
    showModal(taskModal);
}

async function handleTaskSubmit(e) {
    e.preventDefault();
    const taskId = $('#task-id').value;
    const title = $('#task-title').value;
    const description = $('#task-description').value;
    const reminderInput = $('#task-reminder').value;
    const reminder = reminderInput ? new Date(reminderInput).toISOString() : null;

    if (taskId) {
        const res = await invoke('update_task', { request: { id: taskId, title, description, reminder } });
        if (res.success) {
            const idx = tasks.findIndex(t => t.id === taskId);
            if (idx !== -1) tasks[idx] = res.data;
            renderTasks(); hideModal(taskModal); showToast('Tarefa atualizada!'); syncWithServer();
        }
    } else {
        const res = await invoke('create_task', { request: { list_id: currentList.id, title, description, reminder } });
        if (res.success) {
            tasks.unshift(res.data);
            renderTasks(); hideModal(taskModal); taskForm.reset(); showToast('Tarefa criada!'); syncWithServer();
        }
    }
}

async function toggleTask(taskId) {
    const res = await invoke('toggle_task', { taskId });
    if (res.success) {
        const idx = tasks.findIndex(t => t.id === taskId);
        if (idx !== -1) tasks[idx] = res.data;
        renderTasks();
        if (res.data.completed) showToast('Concluída!');
        syncWithServer();
    }
}

function editTask(taskId) {
    const task = tasks.find(t => t.id === taskId);
    if (task) openTaskModal(task);
}

async function deleteTask(taskId) {
    if (!confirm('Excluir tarefa?')) return;
    const res = await invoke('delete_task', { taskId });
    if (res.success) {
        const deleted = getFromStorage('deleted_tasks') || [];
        deleted.push(taskId);
        saveToStorage('deleted_tasks', deleted);
        tasks = tasks.filter(t => t.id !== taskId);
        renderTasks(); showToast('Tarefa excluída'); syncWithServer();
    }
}

// ============== NOTIFICAÇÕES ==============
async function requestNotificationPermission() {
    try { if (window.__TAURI__?.notification) return await window.__TAURI__.notification.requestPermission() === 'granted'; } catch (e) {}
    return false;
}

async function sendNotification(title, body) {
    try { if (window.__TAURI__?.notification) await window.__TAURI__.notification.sendNotification({ title, body }); } catch (e) {}
}

async function checkReminders() {
    try {
        const res = await invoke('get_pending_reminders');
        if (res.success && res.data) for (const task of res.data) await sendNotification('Lembrete', task.title);
    } catch (e) {}
}

function startReminderCheck() { requestNotificationPermission(); checkReminders(); reminderCheckInterval = setInterval(checkReminders, 60000); }
function stopReminderCheck() { if (reminderCheckInterval) { clearInterval(reminderCheckInterval); reminderCheckInterval = null; } }

// ============== INIT ==============
document.addEventListener('DOMContentLoaded', async () => {
    window.addEventListener('online', () => { isOnline = true; syncWithServer(); showToast('Online!'); });
    window.addEventListener('offline', () => { isOnline = false; showToast('Offline', 'error'); });
    
    loginForm.addEventListener('submit', handleLogin);
    registerForm.addEventListener('submit', handleRegister);
    $('#show-register').addEventListener('click', e => { e.preventDefault(); loginForm.classList.remove('active'); registerForm.classList.add('active'); });
    $('#show-login').addEventListener('click', e => { e.preventDefault(); registerForm.classList.remove('active'); loginForm.classList.add('active'); });
    $('#logout-btn').addEventListener('click', handleLogout);
    $('#add-list-btn').addEventListener('click', () => showModal(createListModal));
    $('#cancel-list').addEventListener('click', () => hideModal(createListModal));
    createListForm.addEventListener('submit', handleCreateList);
    $('#back-to-lists').addEventListener('click', () => { currentList = null; showScreen(listsScreen); });
    $('#delete-list-btn').addEventListener('click', deleteCurrentList);
    $('#add-task-btn').addEventListener('click', () => openTaskModal());
    $('#cancel-task').addEventListener('click', () => { hideModal(taskModal); taskForm.reset(); });
    taskForm.addEventListener('submit', handleTaskSubmit);
    [createListModal, taskModal].forEach(m => m.addEventListener('click', e => { if (e.target === m) hideModal(m); }));
    
    loadAuthToken();
    
    try {
        const res = await invoke('get_current_user');
        if (res.success && res.data) {
            currentUser = res.data;
            $('#welcome-user').textContent = `Olá, ${currentUser.username}!`;
            await loadLists();
            showScreen(listsScreen);
            startReminderCheck();
            startSyncInterval();
        }
    } catch (e) {}
});
