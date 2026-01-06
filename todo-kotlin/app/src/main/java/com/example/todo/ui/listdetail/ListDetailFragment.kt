package com.example.todo.ui.listdetail

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.data.local.entity.TaskEntity
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.databinding.FragmentListDetailBinding
import com.example.todo.notification.PersistentNotificationService
import com.example.todo.notification.ReminderScheduler
import com.example.todo.ui.tasks.TasksViewModel
import com.example.todo.util.ViewModelFactory
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.*

class ListDetailFragment : Fragment() {

    private var _binding: FragmentListDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TasksViewModel by viewModels {
        ViewModelFactory((requireActivity().application as TodoApp).repository)
    }

    private lateinit var tasksAdapter: ModernTasksAdapter
    private var currentList: TodoListEntity? = null

    companion object {
        private const val ARG_LIST_ID = "list_id"
        private const val ARG_LIST_NAME = "list_name"
        private const val ARG_LIST_COLOR = "list_color"
        private const val ARG_LIST_IS_PERSISTENT = "list_is_persistent"

        fun newInstance(list: TodoListEntity): ListDetailFragment {
            return ListDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LIST_ID, list.id)
                    putString(ARG_LIST_NAME, list.name)
                    putString(ARG_LIST_COLOR, list.color)
                    putBoolean(ARG_LIST_IS_PERSISTENT, list.isPersistent)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listId = arguments?.getString(ARG_LIST_ID) ?: return
        val listName = arguments?.getString(ARG_LIST_NAME) ?: ""
        val listColor = arguments?.getString(ARG_LIST_COLOR) ?: "#4A90A4"
        val isPersistent = arguments?.getBoolean(ARG_LIST_IS_PERSISTENT) ?: false

        currentList = TodoListEntity(
            id = listId,
            name = listName,
            color = listColor,
            userId = "",
            createdAt = "",
            updatedAt = "",
            isSynced = true,
            isDeleted = false,
            isPersistent = isPersistent
        )

        binding.tvTitle.text = listName

        setupRecyclerView()
        setupObservers(listId)
        setupClickListeners(listId)
    }

    private fun setupRecyclerView() {
        tasksAdapter = ModernTasksAdapter(
            onTaskClick = { task ->
                toggleTaskCompletion(task)
            },
            onTaskLongClick = { task ->
                showDeleteTaskDialog(task)
            }
        )

        binding.rvTasks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tasksAdapter
        }

        // Add drag to reorder + swipe to delete
        val dragSwipeHandler = DragSwipeCallback(
            context = requireContext(),
            adapter = tasksAdapter,
            onDelete = { task ->
                viewModel.deleteTask(task)
                Toast.makeText(context, "Tarefa apagada", Toast.LENGTH_SHORT).show()
                
                // Atualizar notificação persistente
                currentList?.let { list ->
                    if (list.isPersistent) {
                        PersistentNotificationService.updateNotification(requireContext(), list.id)
                    }
                }
            },
            onMove = { fromPos, toPos ->
                tasksAdapter.moveItem(fromPos, toPos)
                // Salvar nova ordem
                viewModel.updateTasksOrder(tasksAdapter.getItems())
            }
        )
        ItemTouchHelper(dragSwipeHandler).attachToRecyclerView(binding.rvTasks)
    }

    private fun setupObservers(listId: String) {
        viewModel.loadTasks(listId)

        // Observar a lista para obter dados atualizados (incluindo isPersistent)
        viewModel.list.observe(viewLifecycleOwner) { list ->
            list?.let {
                currentList = it
                binding.tvTitle.text = it.name
            }
        }

        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            tasksAdapter.submitList(tasks)
            
            // Agendar lembretes para tarefas não concluídas
            tasks.forEach { task ->
                if (!task.reminder.isNullOrBlank() && !task.completed) {
                    ReminderScheduler.scheduleReminder(requireContext(), task)
                }
            }
            
            // Atualizar notificação persistente se existir
            currentList?.let { list ->
                if (list.isPersistent) {
                    PersistentNotificationService.updateNotification(requireContext(), list.id)
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners(listId: String) {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.fabAddTask.setOnClickListener {
            showCreateTaskDialog(listId)
        }

        binding.btnNewList.setOnClickListener {
            showCreateTaskDialog(listId)
        }
    }

    private fun toggleTaskCompletion(task: TaskEntity) {
        viewModel.toggleTaskCompletion(task)
        
        // Atualizar notificação persistente imediatamente
        currentList?.let { list ->
            if (list.isPersistent) {
                PersistentNotificationService.updateNotification(requireContext(), list.id)
            }
        }
    }

    private fun showCreateTaskDialog(listId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_task, null)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etTaskTitle)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etTaskDescription)
        val btnSetReminder = dialogView.findViewById<View>(R.id.btnSetReminder)

        var reminderTime: Long? = null

        btnSetReminder?.setOnClickListener {
            showDateTimePicker { selectedTime ->
                reminderTime = selectedTime
                Toast.makeText(context, R.string.reminder_set, Toast.LENGTH_SHORT).show()
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_task)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = etTitle.text.toString().trim()
                val description = etDescription.text.toString().trim()

                if (title.isNotEmpty()) {
                    viewModel.createTask(
                        listId = listId,
                        title = title,
                        description = description.ifEmpty { null },
                        reminderTime = reminderTime
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDateTimePicker(onDateTimeSelected: (Long) -> Unit) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecione a data")
            .build()

        datePicker.addOnPositiveButtonClickListener { dateMillis ->
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(9)
                .setMinute(0)
                .setTitleText("Selecione a hora")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(Calendar.HOUR_OF_DAY, timePicker.hour)
                    set(Calendar.MINUTE, timePicker.minute)
                    set(Calendar.SECOND, 0)
                }
                onDateTimeSelected(calendar.timeInMillis)
            }

            timePicker.show(parentFragmentManager, "time_picker")
        }

        datePicker.show(parentFragmentManager, "date_picker")
    }

    private fun showDeleteTaskDialog(task: TaskEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(R.string.confirm_delete_task)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTask(task)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
