package com.example.todo.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.todo.R
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.databinding.ItemAddFolderBinding
import com.example.todo.databinding.ItemFolderGridBinding

class FoldersAdapter(
    private val onFolderClick: (TodoListEntity) -> Unit,
    private val onFolderLongClick: (TodoListEntity) -> Unit,
    private val onAddClick: () -> Unit
) : ListAdapter<TodoListEntity, RecyclerView.ViewHolder>(FolderDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_ADD = 0
        private const val VIEW_TYPE_FOLDER = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_ADD else VIEW_TYPE_FOLDER
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1 // +1 for "Add" button
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ADD -> {
                val binding = ItemAddFolderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AddViewHolder(binding)
            }
            else -> {
                val binding = ItemFolderGridBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                FolderViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AddViewHolder -> holder.bind()
            is FolderViewHolder -> {
                val item = getItem(position - 1) // -1 because position 0 is "Add" button
                holder.bind(item)
            }
        }
    }

    inner class AddViewHolder(
        private val binding: ItemAddFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.setOnClickListener {
                onAddClick()
            }
        }
    }

    inner class FolderViewHolder(
        private val binding: ItemFolderGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TodoListEntity) {
            binding.apply {
                tvName.text = item.name
                tvCount.text = "Listas"

                // Set card background color (lighter version)
                val color = try {
                    Color.parseColor(item.color)
                } catch (e: Exception) {
                    Color.parseColor("#4A90A4")
                }

                val lightColor = lightenColor(color, 0.85f)
                (root as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(lightColor)

                // Set icon tint
                ivIcon.setColorFilter(color)

                root.setOnClickListener {
                    onFolderClick(item)
                }

                root.setOnLongClickListener {
                    onFolderLongClick(item)
                    true
                }
            }
        }

        private fun lightenColor(color: Int, factor: Float): Int {
            val red = ((Color.red(color) * (1 - factor) + 255 * factor)).toInt()
            val green = ((Color.green(color) * (1 - factor) + 255 * factor)).toInt()
            val blue = ((Color.blue(color) * (1 - factor) + 255 * factor)).toInt()
            return Color.rgb(red, green, blue)
        }
    }
}

class FolderDiffCallback : DiffUtil.ItemCallback<TodoListEntity>() {
    override fun areItemsTheSame(oldItem: TodoListEntity, newItem: TodoListEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: TodoListEntity, newItem: TodoListEntity): Boolean {
        return oldItem == newItem
    }
}
