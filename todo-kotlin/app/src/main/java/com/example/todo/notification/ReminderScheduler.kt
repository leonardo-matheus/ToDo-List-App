package com.example.todo.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.data.local.entity.TodoListEntity
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    fun scheduleReminder(context: Context, task: TaskEntity) {
        val reminder = task.reminder ?: return
        
        try {
            val reminderTime: Long
            
            // Tentar interpretar como timestamp numérico primeiro
            val timestamp = reminder.toLongOrNull()
            if (timestamp != null) {
                reminderTime = timestamp
            } else {
                // Tentar interpretar como data formatada
                val reminderDate = dateFormat.parse(reminder) ?: return
                reminderTime = reminderDate.time
            }
            
            val now = System.currentTimeMillis()
            
            // Não agendar se já passou
            if (reminderTime <= now) return
            
            val delay = reminderTime - now
            
            val data = workDataOf(
                ReminderWorker.KEY_TASK_ID to task.id,
                ReminderWorker.KEY_TASK_TITLE to task.title,
                ReminderWorker.KEY_TASK_DESCRIPTION to (task.description ?: ""),
                ReminderWorker.KEY_LIST_ID to task.listId
            )
            
            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("reminder_${task.id}")
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "reminder_${task.id}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun scheduleListReminder(context: Context, list: TodoListEntity) {
        val reminder = list.reminder ?: return
        
        try {
            val reminderTime: Long
            
            // Tentar interpretar como timestamp numérico primeiro
            val timestamp = reminder.toLongOrNull()
            if (timestamp != null) {
                reminderTime = timestamp
            } else {
                // Tentar interpretar como data formatada
                val reminderDate = dateFormat.parse(reminder) ?: return
                reminderTime = reminderDate.time
            }
            
            val now = System.currentTimeMillis()
            
            // Não agendar se já passou
            if (reminderTime <= now) return
            
            val delay = reminderTime - now
            
            val data = workDataOf(
                ListReminderWorker.KEY_LIST_ID to list.id,
                ListReminderWorker.KEY_LIST_NAME to list.name,
                ListReminderWorker.KEY_LIST_COLOR to list.color
            )
            
            val workRequest = OneTimeWorkRequestBuilder<ListReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("list_reminder_${list.id}")
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "list_reminder_${list.id}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun cancelReminder(context: Context, taskId: String) {
        WorkManager.getInstance(context)
            .cancelUniqueWork("reminder_$taskId")
    }
    
    fun cancelListReminder(context: Context, listId: String) {
        WorkManager.getInstance(context)
            .cancelUniqueWork("list_reminder_$listId")
    }
    
    fun cancelAllReminders(context: Context) {
        WorkManager.getInstance(context)
            .cancelAllWork()
    }
}
