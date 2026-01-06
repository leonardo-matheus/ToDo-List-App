package com.example.todo.ui.listdetail

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.todo.R
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.databinding.ItemTaskModernBinding
import java.text.SimpleDateFormat
import java.util.*

class ModernTasksAdapter(
    private val onTaskClick: (TaskEntity) -> Unit,
    private val onTaskLongClick: (TaskEntity) -> Unit
) : ListAdapter<TaskEntity, ModernTasksAdapter.TaskViewHolder>(TaskDiffCallback()) {

    private val items = mutableListOf<TaskEntity>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskModernBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    override fun submitList(list: List<TaskEntity>?) {
        items.clear()
        list?.let { items.addAll(it) }
        super.submitList(list?.toList())
    }
    
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < items.size && toPosition < items.size) {
            val item = items.removeAt(fromPosition)
            items.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
        }
    }
    
    fun getItems(): List<TaskEntity> = items.toList()

    inner class TaskViewHolder(
        private val binding: ItemTaskModernBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskEntity) {
            binding.apply {
                tvTaskTitle.text = task.title

                // Completed state
                if (task.completed) {
                    checkboxBg.setBackgroundResource(R.drawable.bg_checkbox_checked)
                    ivCheck.visibility = View.VISIBLE
                    tvTaskTitle.paintFlags = tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tvTaskTitle.alpha = 0.5f
                } else {
                    checkboxBg.setBackgroundResource(R.drawable.bg_checkbox_unchecked)
                    ivCheck.visibility = View.GONE
                    tvTaskTitle.paintFlags = tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tvTaskTitle.alpha = 1f
                }

                // Time
                task.reminder?.let { reminder ->
                    try {
                        val time = reminder.toLong()
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        tvTime.text = sdf.format(Date(time))
                        tvTime.visibility = View.VISIBLE
                        ivReminder.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        tvTime.text = reminder
                        tvTime.visibility = View.VISIBLE
                        ivReminder.visibility = View.VISIBLE
                    }
                } ?: run {
                    tvTime.text = "--:--"
                    ivReminder.visibility = View.GONE
                }

                // Priority (for now, hide it)
                tvPriority.visibility = View.GONE

                // Click listeners
                root.setOnClickListener {
                    onTaskClick(task)
                }

                root.setOnLongClickListener {
                    onTaskLongClick(task)
                    true
                }
            }
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
