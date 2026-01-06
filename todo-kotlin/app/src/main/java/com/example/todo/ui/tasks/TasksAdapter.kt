package com.example.todo.ui.tasks

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.*

class TasksAdapter(
    private val onItemClick: (TaskEntity) -> Unit,
    private val onCheckClick: (TaskEntity) -> Unit,
    private val onDeleteClick: (TaskEntity) -> Unit
) : ListAdapter<TaskEntity, TasksAdapter.TaskViewHolder>(TaskDiffCallback()) {
    
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val parseFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(task: TaskEntity) {
            binding.apply {
                tvTaskTitle.text = task.title
                cbCompleted.isChecked = task.completed
                
                // Strike-through para tarefas completadas
                if (task.completed) {
                    tvTaskTitle.paintFlags = tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tvTaskTitle.alpha = 0.6f
                } else {
                    tvTaskTitle.paintFlags = tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tvTaskTitle.alpha = 1f
                }
                
                // Descrição
                if (!task.description.isNullOrBlank()) {
                    tvTaskDescription.text = task.description
                    tvTaskDescription.visibility = View.VISIBLE
                } else {
                    tvTaskDescription.visibility = View.GONE
                }
                
                // Lembrete
                if (!task.reminder.isNullOrBlank()) {
                    try {
                        val reminderDate = parseFormat.parse(task.reminder)
                        tvReminder.text = "⏰ ${dateFormat.format(reminderDate!!)}"
                        tvReminder.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        tvReminder.visibility = View.GONE
                    }
                } else {
                    tvReminder.visibility = View.GONE
                }
                
                root.setOnClickListener { onItemClick(task) }
                cbCompleted.setOnClickListener { onCheckClick(task) }
                btnDelete.setOnClickListener { onDeleteClick(task) }
            }
        }
    }
    
    class TaskDiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem == newItem
        }
    }
}
