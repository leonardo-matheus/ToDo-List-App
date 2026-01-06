package com.example.todo.ui.lists

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.databinding.ActivityListsBinding
import com.example.todo.databinding.DialogCreateListBinding
import com.example.todo.ui.auth.AuthActivity
import com.example.todo.ui.tasks.TasksActivity
import com.example.todo.util.ViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityListsBinding
    private lateinit var viewModel: ListsViewModel
    private lateinit var adapter: ListsAdapter
    
    private val colors = listOf(
        "#3B82F6", "#EF4444", "#10B981", "#F59E0B", 
        "#8B5CF6", "#EC4899", "#06B6D4", "#84CC16"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val app = application as TodoApp
        viewModel = ViewModelProvider(
            this,
            ViewModelFactory(app.repository)
        )[ListsViewModel::class.java]
        
        setupRecyclerView()
        setupObservers()
        setupListeners()
        
        // Sincronizar ao iniciar
        viewModel.sync()
    }
    
    private fun setupRecyclerView() {
        adapter = ListsAdapter(
            onItemClick = { list ->
                val intent = Intent(this, TasksActivity::class.java).apply {
                    putExtra(TasksActivity.EXTRA_LIST_ID, list.id)
                    putExtra(TasksActivity.EXTRA_LIST_NAME, list.name)
                    putExtra(TasksActivity.EXTRA_LIST_COLOR, list.color)
                }
                startActivity(intent)
            },
            onDeleteClick = { list ->
                showDeleteConfirmation(list)
            }
        )
        
        binding.rvLists.apply {
            layoutManager = LinearLayoutManager(this@ListsActivity)
            adapter = this@ListsActivity.adapter
        }
    }
    
    private fun setupObservers() {
        viewModel.lists.observe(this) { lists ->
            adapter.submitList(lists)
            binding.tvEmpty.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }
        
        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        viewModel.syncStatus.observe(this) { status ->
            when (status) {
                is ListsViewModel.SyncStatus.Syncing -> {
                    binding.swipeRefresh.isRefreshing = true
                }
                is ListsViewModel.SyncStatus.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                }
                is ListsViewModel.SyncStatus.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this, status.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }
    
    private fun setupListeners() {
        binding.fabAddList.setOnClickListener {
            showCreateListDialog()
        }
        
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.sync()
        }
        
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_logout -> {
                    logout()
                    true
                }
                R.id.action_sync -> {
                    viewModel.sync()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun showCreateListDialog(existingList: com.example.todo.data.local.entity.TodoListEntity? = null) {
        val dialogBinding = DialogCreateListBinding.inflate(layoutInflater)
        var selectedColor = existingList?.color ?: colors[0]
        
        existingList?.let {
            dialogBinding.etListName.setText(it.name)
        }
        
        // Setup color picker
        val colorViews = listOf(
            dialogBinding.color1, dialogBinding.color2, dialogBinding.color3, dialogBinding.color4,
            dialogBinding.color5, dialogBinding.color6, dialogBinding.color7, dialogBinding.color8
        )
        
        colorViews.forEachIndexed { index, view ->
            view.setBackgroundColor(android.graphics.Color.parseColor(colors[index]))
            view.setOnClickListener {
                selectedColor = colors[index]
                colorViews.forEach { v -> v.alpha = 0.5f }
                view.alpha = 1f
            }
            if (colors[index] == selectedColor) {
                view.alpha = 1f
            } else {
                view.alpha = 0.5f
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (existingList == null) "Nova Lista" else "Editar Lista")
            .setView(dialogBinding.root)
            .setPositiveButton("Salvar") { _, _ ->
                val name = dialogBinding.etListName.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (existingList == null) {
                        viewModel.createList(name, selectedColor)
                    } else {
                        viewModel.updateList(existingList.id, name, selectedColor)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showDeleteConfirmation(list: com.example.todo.data.local.entity.TodoListEntity) {
        AlertDialog.Builder(this)
            .setTitle("Deletar Lista")
            .setMessage("Tem certeza que deseja deletar a lista '${list.name}' e todas as suas tarefas?")
            .setPositiveButton("Deletar") { _, _ ->
                viewModel.deleteList(list)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Tem certeza que deseja sair?")
            .setPositiveButton("Sair") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    (application as TodoApp).repository.logout()
                    startActivity(Intent(this@ListsActivity, AuthActivity::class.java))
                    finish()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
