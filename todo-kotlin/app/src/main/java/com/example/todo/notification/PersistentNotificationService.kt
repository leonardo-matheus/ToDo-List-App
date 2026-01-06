package com.example.todo.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.data.local.TodoDatabase
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.ui.main.MainActivityNew
import kotlinx.coroutines.*

class PersistentNotificationService : Service() {
    
    companion object {
        const val ACTION_START = "com.example.todo.START_PERSISTENT"
        const val ACTION_STOP = "com.example.todo.STOP_PERSISTENT"
        const val ACTION_UPDATE = "com.example.todo.UPDATE_PERSISTENT"
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_LIST_NAME = "list_name"
        const val EXTRA_LIST_COLOR = "list_color"
        const val NOTIFICATION_CHANNEL_ID = "persistent_list_channel"
        const val NOTIFICATION_ID = 9999
        
        fun startService(context: Context, listId: String, listName: String, listColor: String) {
            val intent = Intent(context, PersistentNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LIST_ID, listId)
                putExtra(EXTRA_LIST_NAME, listName)
                putExtra(EXTRA_LIST_COLOR, listColor)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context, listId: String) {
            val intent = Intent(context, PersistentNotificationService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_LIST_ID, listId)
            }
            context.startService(intent)
        }
        
        fun updateNotification(context: Context, listId: String) {
            val intent = Intent(context, PersistentNotificationService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_LIST_ID, listId)
            }
            context.startService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentListId: String? = null
    private var currentListName: String = ""
    private var currentListColor: String = "#42A5F5"
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentListId = intent.getStringExtra(EXTRA_LIST_ID)
                currentListName = intent.getStringExtra(EXTRA_LIST_NAME) ?: ""
                currentListColor = intent.getStringExtra(EXTRA_LIST_COLOR) ?: "#42A5F5"
                startForeground(NOTIFICATION_ID, createNotification(emptyList(), 0, 0))
                updateNotificationWithTasks()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE -> {
                updateNotificationWithTasks()
            }
        }
        return START_STICKY
    }
    
    private fun updateNotificationWithTasks() {
        currentListId?.let { listId ->
            serviceScope.launch {
                val database = TodoDatabase.getInstance(applicationContext)
                val tasks = database.taskDao().getTasksByListIdSync(listId)
                val totalTasks = tasks.size
                val completedTasks = tasks.count { it.completed }
                val pendingTasks = tasks.filter { !it.completed }
                
                // Se todas as tarefas estiverem concluídas, parar o serviço
                if (totalTasks > 0 && completedTasks == totalTasks) {
                    withContext(Dispatchers.Main) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    return@launch
                }
                
                val notification = createNotification(
                    pendingTasks.take(5),
                    completedTasks,
                    totalTasks
                )
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }
    
    private fun createNotification(pendingTasks: List<TaskEntity>, completed: Int, total: Int): Notification {
        val intent = Intent(this, MainActivityNew::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📋 $currentListName")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        
        if (total > 0) {
            val progress = "$completed/$total concluídas"
            builder.setContentText(progress)
            
            // Usar InboxStyle para mostrar as tarefas pendentes
            if (pendingTasks.isNotEmpty()) {
                val inboxStyle = NotificationCompat.InboxStyle()
                    .setBigContentTitle("📋 $currentListName")
                    .setSummaryText(progress)
                
                pendingTasks.forEachIndexed { index, task ->
                    inboxStyle.addLine("☐ ${task.title}")
                }
                
                val remainingTasks = (total - completed) - pendingTasks.size
                if (remainingTasks > 0) {
                    inboxStyle.addLine("... e mais $remainingTasks tarefa(s)")
                }
                
                builder.setStyle(inboxStyle)
                
                // Adicionar ação para marcar a primeira tarefa como concluída
                if (pendingTasks.isNotEmpty()) {
                    val firstTask = pendingTasks.first()
                    val completeIntent = Intent(this, TaskActionReceiver::class.java).apply {
                        action = TaskActionReceiver.ACTION_COMPLETE_TASK
                        putExtra(TaskActionReceiver.EXTRA_TASK_ID, firstTask.id)
                        putExtra(TaskActionReceiver.EXTRA_LIST_ID, currentListId)
                    }
                    val completePendingIntent = PendingIntent.getBroadcast(
                        this,
                        firstTask.id.hashCode(),
                        completeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        R.drawable.ic_check,
                        "✓ ${firstTask.title.take(15)}${if (firstTask.title.length > 15) "..." else ""}",
                        completePendingIntent
                    )
                }
                
                // Adicionar ação para a segunda tarefa se existir
                if (pendingTasks.size >= 2) {
                    val secondTask = pendingTasks[1]
                    val completeIntent = Intent(this, TaskActionReceiver::class.java).apply {
                        action = TaskActionReceiver.ACTION_COMPLETE_TASK
                        putExtra(TaskActionReceiver.EXTRA_TASK_ID, secondTask.id)
                        putExtra(TaskActionReceiver.EXTRA_LIST_ID, currentListId)
                    }
                    val completePendingIntent = PendingIntent.getBroadcast(
                        this,
                        secondTask.id.hashCode(),
                        completeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        R.drawable.ic_check,
                        "✓ ${secondTask.title.take(15)}${if (secondTask.title.length > 15) "..." else ""}",
                        completePendingIntent
                    )
                }
            }
        } else {
            builder.setContentText("Adicione tarefas a esta lista")
        }
        
        return builder.build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Lista Persistente",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação fixa da lista em modo persistente"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
