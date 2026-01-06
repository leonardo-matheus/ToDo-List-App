package com.example.todo.ui.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todo.TodoApp
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.databinding.ActivityTasksBinding
import com.example.todo.databinding.DialogCreateTaskBinding
import com.example.todo.notification.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.*

class TasksActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_LIST_NAME = "list_name"
        const val EXTRA_LIST_COLOR = "list_color"
    }
    
    private lateinit var binding: ActivityTasksBinding
    private lateinit var viewModel: TasksViewModel
    private lateinit var adapter: TasksAdapter
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    private var listId: String = ""
    private var listName: String = ""
    private var listColor: String = "#3B82F6"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        listId = intent.getStringExtra(EXTRA_LIST_ID) ?: ""
        listName = intent.getStringExtra(EXTRA_LIST_NAME) ?: "Tarefas"
        listColor = intent.getStringExtra(EXTRA_LIST_COLOR) ?: "#3B82F6"
        
        if (listId.isEmpty()) {
            finish()
            return
        }
        
        setupToolbar()
        setupViewModel()
        setupRecyclerView()
        setupObservers()
        setupListeners()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = listName
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        try {
            window.statusBarColor = Color.parseColor(listColor)
            binding.toolbar.setBackgroundColor(Color.parseColor(listColor))
        } catch (e: Exception) {
            // Use default color
        }
    }
    
    private fun setupViewModel() {
        val app = application as TodoApp
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TasksViewModel(app.repository) as T
            }
        })[TasksViewModel::class.java]
        
        viewModel.loadTasks(listId)
    }
    
    private fun setupRecyclerView() {
        adapter = TasksAdapter(
            onItemClick = { task ->
                showEditTaskDialog(task)
            },
            onCheckClick = { task ->
                viewModel.toggleTaskCompletion(task)
            },
            onDeleteClick = { task ->
                showDeleteConfirmation(task)
            }
        )
        
        binding.rvTasks.apply {
            layoutManager = LinearLayoutManager(this@TasksActivity)
            adapter = this@TasksActivity.adapter
        }
    }
    
    private fun setupObservers() {
        viewModel.tasks.observe(this) { tasks ->
            adapter.submitList(tasks)
            binding.tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
            
            // Agendar lembretes
            tasks.forEach { task ->
                if (!task.reminder.isNullOrBlank() && !task.completed) {
                    ReminderScheduler.scheduleReminder(this, task)
                }
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }
    
    private fun setupListeners() {
        binding.fabAddTask.setOnClickListener {
            showCreateTaskDialog()
        }
    }
    
    private fun showCreateTaskDialog() {
        showTaskDialog(null)
    }
    
    private fun showEditTaskDialog(task: TaskEntity) {
        showTaskDialog(task)
    }
    
    private fun showTaskDialog(existingTask: TaskEntity?) {
        val dialogBinding = DialogCreateTaskBinding.inflate(layoutInflater)
        var selectedReminder: Calendar? = null
        
        existingTask?.let { task ->
            dialogBinding.etTaskTitle.setText(task.title)
            dialogBinding.etTaskDescription.setText(task.description ?: "")
            
            task.reminder?.let { reminder ->
                try {
                    val date = dateFormat.parse(reminder)
                    selectedReminder = Calendar.getInstance().apply { time = date!! }
                    dialogBinding.tvSelectedReminder.text = displayFormat.format(date!!)
                    dialogBinding.tvSelectedReminder.visibility = View.VISIBLE
                    dialogBinding.btnClearReminder.visibility = View.VISIBLE
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        
        dialogBinding.btnSetReminder.setOnClickListener {
            showDateTimePicker { calendar ->
                selectedReminder = calendar
                dialogBinding.tvSelectedReminder.text = displayFormat.format(calendar.time)
                dialogBinding.tvSelectedReminder.visibility = View.VISIBLE
                dialogBinding.btnClearReminder.visibility = View.VISIBLE
            }
        }
        
        dialogBinding.btnClearReminder.setOnClickListener {
            selectedReminder = null
            dialogBinding.tvSelectedReminder.visibility = View.GONE
            dialogBinding.btnClearReminder.visibility = View.GONE
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (existingTask == null) "Nova Tarefa" else "Editar Tarefa")
            .setView(dialogBinding.root)
            .setPositiveButton("Salvar") { _, _ ->
                val title = dialogBinding.etTaskTitle.text.toString().trim()
                val description = dialogBinding.etTaskDescription.text.toString().trim()
                val reminderStr = selectedReminder?.let { dateFormat.format(it.time) }
                
                if (title.isNotEmpty()) {
                    if (existingTask == null) {
                        viewModel.createTask(listId, title, description.ifEmpty { null }, selectedReminder?.timeInMillis)
                    } else {
                        viewModel.updateTask(
                            existingTask.id,
                            title,
                            description.ifEmpty { null },
                            reminderStr ?: ""
                        )
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showDateTimePicker(onDateTimeSelected: (Calendar) -> Unit) {
        val calendar = Calendar.getInstance()
        
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        onDateTimeSelected(calendar)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    private fun showDeleteConfirmation(task: TaskEntity) {
        AlertDialog.Builder(this)
            .setTitle("Deletar Tarefa")
            .setMessage("Tem certeza que deseja deletar '${task.title}'?")
            .setPositiveButton("Deletar") { _, _ ->
                ReminderScheduler.cancelReminder(this, task.id)
                viewModel.deleteTask(task)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
