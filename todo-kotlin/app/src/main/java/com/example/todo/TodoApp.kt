package com.example.todo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.todo.data.api.RetrofitInstance
import com.example.todo.data.local.PreferencesManager
import com.example.todo.data.local.TodoDatabase
import com.example.todo.data.repository.TodoRepository
import com.example.todo.util.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TodoApp : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    lateinit var repository: TodoRepository
        private set
    
    lateinit var preferencesManager: PreferencesManager
        private set
    
    lateinit var themeManager: ThemeManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar ThemeManager e aplicar tema
        themeManager = ThemeManager(this)
        applicationScope.launch {
            val mode = themeManager.themeMode.first()
            themeManager.applyTheme(mode)
        }
        
        // Inicializar PreferencesManager
        preferencesManager = PreferencesManager(this)
        
        // Inicializar Retrofit com o PreferencesManager
        RetrofitInstance.init(preferencesManager)
        
        // Inicializar Database
        val database = TodoDatabase.getInstance(this)
        
        // Inicializar Repository
        repository = TodoRepository(database, preferencesManager)
        
        // Criar canal de notificação
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Lembretes de Tarefas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de lembretes das tarefas"
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "todo_reminders"
    }
}
