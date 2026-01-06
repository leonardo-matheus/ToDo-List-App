package com.example.todo.ui.lists

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.databinding.ItemListBinding

class ListsAdapter(
    private val onItemClick: (TodoListEntity) -> Unit,
    private val onDeleteClick: (TodoListEntity) -> Unit
) : ListAdapter<ListsAdapter.ListWithCount, ListsAdapter.ListViewHolder>(ListDiffCallback()) {
    
    data class ListWithCount(
        val list: TodoListEntity,
        val taskCount: Int = 0,
        val completedCount: Int = 0
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding = ItemListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ListViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ListViewHolder(
        private val binding: ItemListBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: ListWithCount) {
            binding.apply {
                tvListName.text = item.list.name
                tvTaskCount.text = "${item.completedCount}/${item.taskCount} tarefas"
                
                try {
                    viewColor.setBackgroundColor(Color.parseColor(item.list.color))
                } catch (e: Exception) {
                    viewColor.setBackgroundColor(Color.parseColor("#3B82F6"))
                }
                
                root.setOnClickListener { onItemClick(item.list) }
                btnDelete.setOnClickListener { onDeleteClick(item.list) }
            }
        }
    }
    
    class ListDiffCallback : DiffUtil.ItemCallback<ListWithCount>() {
        override fun areItemsTheSame(oldItem: ListWithCount, newItem: ListWithCount): Boolean {
            return oldItem.list.id == newItem.list.id
        }
        
        override fun areContentsTheSame(oldItem: ListWithCount, newItem: ListWithCount): Boolean {
            return oldItem == newItem
        }
    }
}
