package com.example.todo.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.databinding.FragmentHomeBinding
import com.example.todo.notification.PersistentNotificationService
import com.example.todo.notification.ReminderScheduler
import com.example.todo.ui.lists.ListsViewModel
import com.example.todo.util.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ListsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as TodoApp).repository)
    }

    private lateinit var foldersAdapter: FoldersAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        // Auto-sync ao iniciar
        viewModel.sync()
    }

    private fun setupRecyclerView() {
        foldersAdapter = FoldersAdapter(
            onFolderClick = { list ->
                // Navigate to list detail
                (activity as? OnFolderSelectedListener)?.onFolderSelected(list)
            },
            onFolderLongClick = { list ->
                showListOptionsDialog(list)
            },
            onAddClick = {
                showCreateListDialog()
            }
        )

        binding.rvFolders.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = foldersAdapter
        }
    }

    private fun setupObservers() {
        viewModel.lists.observe(viewLifecycleOwner) { lists ->
            foldersAdapter.submitList(lists.map { it.list })
            binding.swipeRefresh.isRefreshing = false
            
            // Verificar listas e agendar lembretes/notificações
            lists.forEach { listWithCount ->
                val list = listWithCount.list
                
                // Iniciar notificação persistente se necessário
                if (list.isPersistent) {
                    PersistentNotificationService.startService(
                        requireContext(),
                        list.id,
                        list.name,
                        list.color
                    )
                }
                
                // Agendar lembrete da lista se existir
                if (!list.reminder.isNullOrBlank()) {
                    ReminderScheduler.scheduleListReminder(requireContext(), list)
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabAdd.setOnClickListener {
            showCreateListDialog()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.sync()
        }
    }

    private fun showCreateListDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_list, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etListName)
        val btnSetReminder = dialogView.findViewById<MaterialButton>(R.id.btnSetReminder)
        val tvReminderTime = dialogView.findViewById<TextView>(R.id.tvReminderTime)
        val cbPersistent = dialogView.findViewById<CheckBox>(R.id.cbPersistent)

        var selectedColor = "#42A5F5"
        var reminderTime: Long? = null
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        
        val colorViews = listOf(
            dialogView.findViewById<View>(R.id.color1),
            dialogView.findViewById<View>(R.id.color2),
            dialogView.findViewById<View>(R.id.color3),
            dialogView.findViewById<View>(R.id.color4),
            dialogView.findViewById<View>(R.id.color5),
            dialogView.findViewById<View>(R.id.color6),
            dialogView.findViewById<View>(R.id.color7),
            dialogView.findViewById<View>(R.id.color8)
        )

        val colors = listOf(
            "#42A5F5", "#EF5350", "#66BB6A", "#FFCA28",
            "#AB47BC", "#EC407A", "#26C6DA", "#9CCC65"
        )

        // Aplicar cores aos círculos
        colorViews.forEachIndexed { index, view ->
            view?.let {
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                drawable.setColor(Color.parseColor(colors[index]))
                it.background = drawable
            }
        }
        
        // Marcar primeira cor como selecionada
        colorViews[0]?.alpha = 1f
        colorViews.drop(1).forEach { it?.alpha = 0.5f }

        colorViews.forEachIndexed { index, view ->
            view?.setOnClickListener {
                selectedColor = colors[index]
                colorViews.forEach { it?.alpha = 0.5f }
                view.alpha = 1f
            }
        }
        
        // Configurar botão de lembrete
        btnSetReminder?.setOnClickListener {
            showDateTimePicker { selectedTime ->
                reminderTime = selectedTime
                tvReminderTime?.text = dateFormatter.format(Date(selectedTime))
                tvReminderTime?.visibility = View.VISIBLE
                btnSetReminder.text = "Alterar"
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_list)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = etName.text.toString().trim()
                val isPersistent = cbPersistent?.isChecked ?: false
                if (name.isNotEmpty()) {
                    viewModel.createList(name, selectedColor, reminderTime?.toString(), isPersistent)
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

    private fun showListOptionsDialog(list: TodoListEntity) {
        val options = arrayOf("Editar", "Apagar")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(list.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditListDialog(list)
                    1 -> showDeleteListDialog(list)
                }
            }
            .show()
    }

    private fun showDeleteListDialog(list: TodoListEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Apagar lista")
            .setMessage("Tem certeza que deseja apagar a lista \"${list.name}\"? Todas as tarefas serão excluídas.")
            .setPositiveButton("Apagar") { _, _ ->
                viewModel.deleteList(list)
                Toast.makeText(context, "Lista apagada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditListDialog(list: TodoListEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_list, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etListName)
        val cbPersistent = dialogView.findViewById<CheckBox>(R.id.cbPersistent)
        val btnSetReminder = dialogView.findViewById<MaterialButton>(R.id.btnSetReminder)
        val tvReminderTime = dialogView.findViewById<TextView>(R.id.tvReminderTime)
        
        etName.setText(list.name)
        cbPersistent?.isChecked = list.isPersistent
        
        var reminderTime: Long? = list.reminder?.toLongOrNull()
        if (reminderTime != null) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            tvReminderTime?.text = sdf.format(Date(reminderTime))
            tvReminderTime?.visibility = View.VISIBLE
        }

        var selectedColor = list.color
        val colorViews = listOf(
            dialogView.findViewById<View>(R.id.color1),
            dialogView.findViewById<View>(R.id.color2),
            dialogView.findViewById<View>(R.id.color3),
            dialogView.findViewById<View>(R.id.color4),
            dialogView.findViewById<View>(R.id.color5),
            dialogView.findViewById<View>(R.id.color6),
            dialogView.findViewById<View>(R.id.color7),
            dialogView.findViewById<View>(R.id.color8)
        )

        val colors = listOf(
            "#42A5F5", "#EF5350", "#66BB6A", "#FFCA28",
            "#AB47BC", "#EC407A", "#26C6DA", "#9CCC65"
        )

        // Aplicar cores aos círculos
        colorViews.forEachIndexed { index, view ->
            view?.let {
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                drawable.setColor(Color.parseColor(colors[index]))
                it.background = drawable
                
                // Marcar cor atual como selecionada
                if (colors[index].equals(list.color, ignoreCase = true)) {
                    it.alpha = 1f
                } else {
                    it.alpha = 0.5f
                }
            }
        }

        colorViews.forEachIndexed { index, view ->
            view?.setOnClickListener {
                selectedColor = colors[index]
                colorViews.forEach { it?.alpha = 0.5f }
                view.alpha = 1f
            }
        }
        
        // Configurar seletor de lembrete
        btnSetReminder?.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecionar data")
                .setSelection(reminderTime ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()
            
            datePicker.addOnPositiveButtonClickListener { dateMillis ->
                val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(9)
                    .setMinute(0)
                    .setTitleText("Selecionar hora")
                    .build()
                
                timePicker.addOnPositiveButtonClickListener {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = dateMillis
                        set(Calendar.HOUR_OF_DAY, timePicker.hour)
                        set(Calendar.MINUTE, timePicker.minute)
                        set(Calendar.SECOND, 0)
                    }
                    reminderTime = calendar.timeInMillis
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    tvReminderTime?.text = sdf.format(calendar.time)
                    tvReminderTime?.visibility = View.VISIBLE
                }
                
                timePicker.show(parentFragmentManager, "time_picker")
            }
            
            datePicker.show(parentFragmentManager, "date_picker")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar lista")
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = etName.text.toString().trim()
                val isPersistent = cbPersistent?.isChecked ?: false
                val wasPersistent = list.isPersistent
                
                if (name.isNotEmpty()) {
                    viewModel.updateList(list.id, name, selectedColor, reminderTime?.toString(), isPersistent)
                    
                    // Gerenciar notificação persistente
                    if (isPersistent && !wasPersistent) {
                        // Ativou modo persistente
                        PersistentNotificationService.startService(
                            requireContext(),
                            list.id,
                            name,
                            selectedColor
                        )
                    } else if (!isPersistent && wasPersistent) {
                        // Desativou modo persistente
                        PersistentNotificationService.stopService(requireContext(), list.id)
                    } else if (isPersistent) {
                        // Atualizou lista que já era persistente
                        PersistentNotificationService.updateNotification(requireContext(), list.id)
                    }
                    
                    // Agendar lembrete se definido
                    if (reminderTime != null) {
                        val updatedList = list.copy(name = name, color = selectedColor, reminder = reminderTime.toString())
                        ReminderScheduler.scheduleListReminder(requireContext(), updatedList)
                    }
                    
                    Toast.makeText(context, "Lista atualizada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface OnFolderSelectedListener {
        fun onFolderSelected(list: TodoListEntity)
    }
}
