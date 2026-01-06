package com.example.todo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.todo.data.local.TodoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TaskActionReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_COMPLETE_TASK = "com.example.todo.ACTION_COMPLETE_TASK"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_LIST_ID = "list_id"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_COMPLETE_TASK) {
            val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
            val listId = intent.getStringExtra(EXTRA_LIST_ID) ?: return
            
            CoroutineScope(Dispatchers.IO).launch {
                val database = TodoDatabase.getInstance(context)
                val task = database.taskDao().getTaskById(taskId)
                
                task?.let {
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    database.taskDao().updateCompleted(taskId, true, now)
                    
                    // Atualizar notificação
                    PersistentNotificationService.updateNotification(context, listId)
                }
            }
        }
    }
}
