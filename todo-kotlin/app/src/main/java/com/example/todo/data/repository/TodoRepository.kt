package com.example.todo.data.repository

import android.util.Log
import com.example.todo.data.api.RetrofitInstance
import com.example.todo.data.local.PreferencesManager
import com.example.todo.data.local.TodoDatabase
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.data.local.entity.UserEntity
import com.example.todo.data.model.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class TodoRepository(
    private val database: TodoDatabase,
    private val preferencesManager: PreferencesManager
) {
    private val api = RetrofitInstance.api
    private val todoListDao = database.todoListDao()
    private val taskDao = database.taskDao()
    private val userDao = database.userDao()
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    // ==================== AUTH ====================
    
    sealed class LoginResult {
        data class Success(val authData: AuthData) : LoginResult()
        data class RequiresVerification(val email: String, val message: String) : LoginResult()
        data class Error(val message: String) : LoginResult()
    }
    
    suspend fun login(email: String, password: String): Result<AuthData> {
        Log.d("TodoRepository", "Iniciando login para: $email")
        return try {
            Log.d("TodoRepository", "Chamando API de login...")
            val response = api.login(LoginRequest(email, password))
            Log.d("TodoRepository", "Resposta da API: isSuccessful=${response.isSuccessful}, code=${response.code()}")
            Log.d("TodoRepository", "Body: ${response.body()}")
            if (response.isSuccessful && response.body()?.success == true) {
                val authData = response.body()?.data!!
                Log.d("TodoRepository", "Login OK! Token: ${authData.token?.take(20)}...")
                preferencesManager.saveAuthData(authData.token!!, authData.user!!.id)
                
                // Salvar usuário localmente
                userDao.insert(UserEntity(
                    id = authData.user.id,
                    username = authData.user.username,
                    email = authData.user.email,
                    createdAt = authData.user.createdAt
                ))
                
                Result.success(authData)
            } else {
                // Verificar se precisa de verificação
                val body = response.body()
                if (body?.data?.requiresVerification == true) {
                    Result.failure(VerificationRequiredException(body.data.email ?: email, body.message))
                } else {
                    Log.e("TodoRepository", "Login falhou: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Erro no login"))
                }
            }
        } catch (e: Exception) {
            Log.e("TodoRepository", "Exceção no login: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    class VerificationRequiredException(val email: String, message: String) : Exception(message)
    
    suspend fun register(username: String, email: String, password: String): Result<AuthData> {
        return try {
            val response = api.register(RegisterRequest(username, email, password))
            if (response.isSuccessful && response.body()?.success == true) {
                val authData = response.body()?.data!!
                
                // Se requer verificação, retornar com email
                if (authData.requiresVerification) {
                    Result.failure(VerificationRequiredException(authData.email ?: email, response.body()?.message ?: "Código enviado"))
                } else {
                    preferencesManager.saveAuthData(authData.token!!, authData.user!!.id)
                    
                    // Salvar usuário localmente
                    userDao.insert(UserEntity(
                        id = authData.user.id,
                        username = authData.user.username,
                        email = authData.user.email,
                        createdAt = authData.user.createdAt
                    ))
                    
                    Result.success(authData)
                }
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro no registro"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun verifyEmail(email: String, code: String): Result<AuthData> {
        return try {
            val response = api.verifyEmail(VerifyEmailRequest(email, code))
            if (response.isSuccessful && response.body()?.success == true) {
                val authData = response.body()?.data!!
                preferencesManager.saveAuthData(authData.token!!, authData.user!!.id)
                
                // Salvar usuário localmente
                userDao.insert(UserEntity(
                    id = authData.user.id,
                    username = authData.user.username,
                    email = authData.user.email,
                    createdAt = authData.user.createdAt
                ))
                
                Result.success(authData)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Código inválido"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun resendCode(email: String): Result<Unit> {
        return try {
            val response = api.resendCode(ResendCodeRequest(email))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao reenviar código"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun forgotPassword(email: String): Result<Unit> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao enviar código"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun verifyResetCode(email: String, code: String): Result<String> {
        return try {
            Log.d("TodoRepository", "DEBUG verifyResetCode: email='$email', code='$code'")
            val request = VerifyResetCodeRequest(email, code)
            Log.d("TodoRepository", "DEBUG request: $request")
            val response = api.verifyResetCode(request)
            Log.d("TodoRepository", "DEBUG response: ${response.code()} - ${response.body()}")
            if (response.isSuccessful && response.body()?.success == true) {
                val token = response.body()?.data?.resetToken ?: ""
                Result.success(token)
            } else {
                val errorMsg = response.body()?.message ?: "Código inválido"
                Log.e("TodoRepository", "DEBUG error: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("TodoRepository", "DEBUG exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun resetPassword(email: String, resetToken: String, newPassword: String, confirmPassword: String): Result<Unit> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(email, resetToken, newPassword, confirmPassword))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao redefinir senha"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        preferencesManager.clearAll()
        todoListDao.deleteAll()
        taskDao.deleteAll()
        userDao.deleteAll()
    }
    
    suspend fun isLoggedIn(): Boolean = preferencesManager.isLoggedIn()
    
    fun getToken(): Flow<String?> = preferencesManager.tokenFlow
    
    fun getUserInfo(): Flow<UserEntity?> = kotlinx.coroutines.flow.flow {
        emit(userDao.getUser())
    }
    suspend fun getCurrentUser(): UserEntity? = userDao.getUser()
    
    // ==================== PROFILE UPDATE ====================
    
    suspend fun updateUsername(username: String): Result<String> {
        return try {
            val response = api.updateUsername(UpdateUsernameRequest(username))
            if (response.isSuccessful && response.body()?.success == true) {
                val newUsername = response.body()?.data?.username ?: username
                // Atualizar localmente
                userDao.getUser()?.let { user ->
                    userDao.insert(user.copy(username = newUsername))
                }
                Result.success(newUsername)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao atualizar nome"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateEmail(email: String, password: String): Result<String> {
        return try {
            val response = api.updateEmail(UpdateEmailRequest(email, password))
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data!!
                // Atualizar token
                preferencesManager.updateToken(data.token)
                // Atualizar email localmente
                userDao.getUser()?.let { user ->
                    userDao.insert(user.copy(email = data.email))
                }
                Result.success(data.email)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao atualizar email"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updatePassword(currentPassword: String, newPassword: String, confirmPassword: String): Result<Unit> {
        return try {
            val response = api.updatePassword(UpdatePasswordRequest(currentPassword, newPassword, confirmPassword))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao atualizar senha"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== LISTS ====================
    
    fun getAllLists(): Flow<List<TodoListEntity>> = todoListDao.getAllLists()
    
    suspend fun getListById(id: String): TodoListEntity? = todoListDao.getListById(id)
    
    suspend fun createList(name: String, color: String = "#3B82F6", reminder: String? = null, isPersistent: Boolean = false): Result<TodoListEntity> {
        val userId = preferencesManager.getUserId() ?: return Result.failure(Exception("Usuário não logado"))
        val now = dateFormat.format(Date())
        val id = UUID.randomUUID().toString()
        
        val listEntity = TodoListEntity(
            id = id,
            userId = userId,
            name = name,
            color = color,
            reminder = reminder,
            isPersistent = isPersistent,
            createdAt = now,
            updatedAt = now,
            isSynced = false
        )
        
        todoListDao.insert(listEntity)
        
        // Tentar sincronizar imediatamente
        try {
            val response = api.createList(CreateListRequest(
                id = id,
                name = name,
                color = color,
                createdAt = now
            ))
            if (response.isSuccessful) {
                todoListDao.markAsSynced(id)
            }
        } catch (e: Exception) {
            // Falha na sincronização, será sincronizado depois
        }
        
        return Result.success(listEntity)
    }
    
    suspend fun updateList(id: String, name: String?, color: String?, reminder: String? = null, isPersistent: Boolean? = null): Result<Unit> {
        val existing = todoListDao.getListById(id) ?: return Result.failure(Exception("Lista não encontrada"))
        val now = dateFormat.format(Date())
        
        val updated = existing.copy(
            name = name ?: existing.name,
            color = color ?: existing.color,
            reminder = if (reminder != null) reminder else existing.reminder,
            isPersistent = isPersistent ?: existing.isPersistent,
            updatedAt = now,
            isSynced = false
        )
        
        todoListDao.update(updated)
        
        try {
            api.updateList(id, UpdateListRequest(name, color))
            todoListDao.markAsSynced(id)
        } catch (e: Exception) {
            // Sincroniza depois
        }
        
        return Result.success(Unit)
    }
    
    suspend fun deleteList(id: String): Result<Unit> {
        todoListDao.markAsDeleted(id)
        
        try {
            val response = api.deleteList(id)
            if (response.isSuccessful) {
                todoListDao.deleteById(id)
                taskDao.deleteByListId(id)
            }
        } catch (e: Exception) {
            // Sincroniza depois
        }
        
        return Result.success(Unit)
    }
    
    // ==================== TASKS ====================
    
    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()
    
    fun getTasksByList(listId: String): Flow<List<TaskEntity>> = taskDao.getTasksByListId(listId)
    
    suspend fun getTaskById(id: String): TaskEntity? = taskDao.getTaskById(id)
    
    suspend fun createTask(
        listId: String,
        title: String,
        description: String? = null,
        reminder: String? = null
    ): Result<TaskEntity> {
        val now = dateFormat.format(Date())
        val id = UUID.randomUUID().toString()
        
        val taskEntity = TaskEntity(
            id = id,
            listId = listId,
            title = title,
            description = description,
            completed = false,
            reminder = reminder,
            createdAt = now,
            updatedAt = now,
            isSynced = false
        )
        
        taskDao.insert(taskEntity)
        
        try {
            val response = api.createTask(CreateTaskRequest(
                id = id,
                listId = listId,
                title = title,
                description = description,
                reminder = reminder,
                createdAt = now
            ))
            if (response.isSuccessful) {
                taskDao.markAsSynced(id)
            }
        } catch (e: Exception) {
            // Sincroniza depois
        }
        
        return Result.success(taskEntity)
    }
    
    suspend fun updateTask(
        id: String,
        title: String? = null,
        description: String? = null,
        completed: Boolean? = null,
        reminder: String? = null
    ): Result<Unit> {
        val existing = taskDao.getTaskById(id) ?: return Result.failure(Exception("Tarefa não encontrada"))
        val now = dateFormat.format(Date())
        
        val updated = existing.copy(
            title = title ?: existing.title,
            description = description ?: existing.description,
            completed = completed ?: existing.completed,
            reminder = if (reminder == "") null else (reminder ?: existing.reminder),
            updatedAt = now,
            isSynced = false
        )
        
        taskDao.update(updated)
        
        try {
            api.updateTask(id, UpdateTaskRequest(title, description, completed, reminder))
            taskDao.markAsSynced(id)
        } catch (e: Exception) {
            // Sincroniza depois
        }
        
        return Result.success(Unit)
    }
    
    suspend fun toggleTaskCompleted(id: String): Result<Unit> {
        val existing = taskDao.getTaskById(id) ?: return Result.failure(Exception("Tarefa não encontrada"))
        val now = dateFormat.format(Date())
        
        taskDao.updateCompleted(id, !existing.completed, now)
        
        try {
            api.updateTask(id, UpdateTaskRequest(completed = !existing.completed))
            taskDao.markAsSynced(id)
        } catch (e: Exception) {
            // Sincroniza depois
        }
        
        return Result.success(Unit)
    }
    
    suspend fun updateTasksOrder(tasks: List<TaskEntity>) {
        tasks.forEachIndexed { index, task ->
            taskDao.updatePosition(task.id, index)
        }
    }
    
    suspend fun deleteTask(id: String): Result<Unit> {
        taskDao.markAsDeleted(id)
        
        try {
            val response = api.deleteTask(id)
            if (response.isSuccessful) {
                taskDao.deleteById(id)
            }
        } catch (e: Exception) {
            // Sincroniza depois
        }
        
        return Result.success(Unit)
    }
    
    suspend fun getTasksWithReminders(): List<TaskEntity> = taskDao.getTasksWithReminders()
    
    // ==================== SYNC ====================
    
    suspend fun syncWithServer(): Result<Unit> {
        android.util.Log.d("TodoRepository", "syncWithServer called")
        return try {
            val userId = preferencesManager.getUserId()
            android.util.Log.d("TodoRepository", "userId: $userId")
            
            if (userId == null) {
                android.util.Log.e("TodoRepository", "User not logged in")
                return Result.failure(Exception("Usuário não logado"))
            }
            
            // 1. Push: Enviar alterações locais para o servidor
            val unsyncedLists = todoListDao.getUnsyncedLists()
            val unsyncedTasks = taskDao.getUnsyncedTasks()
            val deletedLists = todoListDao.getDeletedLists()
            val deletedTasks = taskDao.getDeletedTasks()
            
            android.util.Log.d("TodoRepository", "Unsynced lists: ${unsyncedLists.size}, tasks: ${unsyncedTasks.size}")
            android.util.Log.d("TodoRepository", "Deleted lists: ${deletedLists.size}, tasks: ${deletedTasks.size}")
            
            if (unsyncedLists.isNotEmpty() || unsyncedTasks.isNotEmpty() || 
                deletedLists.isNotEmpty() || deletedTasks.isNotEmpty()) {
                
                val pushRequest = SyncPushRequest(
                    lists = unsyncedLists.map { it.toTodoList() },
                    tasks = unsyncedTasks.map { it.toTask() },
                    deletedLists = deletedLists.map { it.id },
                    deletedTasks = deletedTasks.map { it.id }
                )
                
                android.util.Log.d("TodoRepository", "Pushing data to server...")
                val pushResponse = api.syncPush(pushRequest)
                android.util.Log.d("TodoRepository", "Push response: ${pushResponse.isSuccessful}")
                
                if (pushResponse.isSuccessful) {
                    // Marcar como sincronizado
                    unsyncedLists.forEach { todoListDao.markAsSynced(it.id) }
                    unsyncedTasks.forEach { taskDao.markAsSynced(it.id) }
                    
                    // Remover deletados já sincronizados
                    todoListDao.deleteAllSyncedDeleted()
                    taskDao.deleteAllSyncedDeleted()
                } else {
                    android.util.Log.e("TodoRepository", "Push failed: ${pushResponse.errorBody()?.string()}")
                }
            }
            
            // 2. Pull: Baixar alterações do servidor
            val lastSync = preferencesManager.getLastSync()
            android.util.Log.d("TodoRepository", "Pulling from server, lastSync: $lastSync")
            
            val pullResponse = api.syncPull(SyncPullRequest(lastSync))
            android.util.Log.d("TodoRepository", "Pull response: ${pullResponse.isSuccessful}")
            
            if (pullResponse.isSuccessful && pullResponse.body()?.success == true) {
                val data = pullResponse.body()?.data!!
                android.util.Log.d("TodoRepository", "Received lists: ${data.lists.size}, tasks: ${data.tasks.size}")
                
                // Inserir/atualizar listas do servidor
                data.lists.forEach { list ->
                    todoListDao.insert(TodoListEntity(
                        id = list.id,
                        userId = list.userId,
                        name = list.name,
                        color = list.color,
                        createdAt = list.createdAt,
                        updatedAt = list.updatedAt,
                        isSynced = true
                    ))
                }
                
                // Inserir/atualizar tarefas do servidor
                data.tasks.forEach { task ->
                    // Converter reminder datetime para timestamp se necessário
                    val reminderTimestamp = task.reminder?.let { reminderValue ->
                        try {
                            // Tentar converter datetime para timestamp
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            sdf.parse(reminderValue)?.time?.toString()
                        } catch (e: Exception) {
                            // Se já for timestamp ou formato inválido, manter como está
                            reminderValue
                        }
                    }
                    
                    taskDao.insert(TaskEntity(
                        id = task.id,
                        listId = task.listId,
                        title = task.title,
                        description = task.description,
                        completed = task.completed,
                        reminder = reminderTimestamp,
                        createdAt = task.createdAt,
                        updatedAt = task.updatedAt,
                        isSynced = true
                    ))
                }
                
                // Deletar itens removidos no servidor
                data.deletedLists.forEach { todoListDao.deleteById(it) }
                data.deletedTasks.forEach { taskDao.deleteById(it) }
                
                // Salvar timestamp da última sincronização
                preferencesManager.saveLastSync(data.serverTime)
                android.util.Log.d("TodoRepository", "Sync completed successfully")
            } else {
                android.util.Log.e("TodoRepository", "Pull failed: ${pullResponse.errorBody()?.string()}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("TodoRepository", "Sync error: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // ==================== HELPERS ====================
    
    private fun TodoListEntity.toTodoList() = TodoList(
        id = id,
        userId = userId,
        name = name,
        color = color,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    
    private fun TaskEntity.toTask(): Task {
        // Converter timestamp em milissegundos para datetime format se necessário
        val reminderFormatted = reminder?.let { reminderValue ->
            try {
                val timestamp = reminderValue.toLongOrNull()
                if (timestamp != null) {
                    // É um timestamp em milissegundos, converter para datetime
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(timestamp))
                } else {
                    // Já está no formato correto
                    reminderValue
                }
            } catch (e: Exception) {
                reminderValue
            }
        }
        
        return Task(
            id = id,
            listId = listId,
            title = title,
            description = description,
            completed = completed,
            reminder = reminderFormatted,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    suspend fun getTaskCountByList(listId: String): Int = taskDao.getTaskCountByList(listId)
    
    suspend fun getCompletedTaskCountByList(listId: String): Int = taskDao.getCompletedTaskCountByList(listId)
}
