package com.example.todo.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.ui.main.MainActivityNew

class ListReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val KEY_LIST_ID = "list_id"
        const val KEY_LIST_NAME = "list_name"
        const val KEY_LIST_COLOR = "list_color"
    }
    
    override suspend fun doWork(): Result {
        val listId = inputData.getString(KEY_LIST_ID) ?: return Result.failure()
        val listName = inputData.getString(KEY_LIST_NAME) ?: "Lista"
        
        // Verificar permissão de notificação no Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.failure()
            }
        }
        
        showNotification(listId, listName)
        
        return Result.success()
    }
    
    private fun showNotification(listId: String, listName: String) {
        val intent = Intent(context, MainActivityNew::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            listId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, TodoApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📋 Lembrete: $listName")
            .setContentText("Hora de verificar suas tarefas!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(listId.hashCode() + 1000, notification)
    }
}
