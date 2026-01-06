package com.example.todo.ui.listdetail

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.todo.R
import com.example.todo.data.local.entity.TaskEntity
import kotlin.math.abs

class DragSwipeCallback(
    private val context: Context,
    private val adapter: ModernTasksAdapter,
    private val onDelete: (TaskEntity) -> Unit,
    private val onMove: (Int, Int) -> Unit
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,  // Drag directions
    ItemTouchHelper.LEFT  // Swipe direction - apenas esquerda
) {

    private val deleteBackground = ColorDrawable(Color.parseColor("#E62222"))
    private val deleteIconPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val dividerPaint = Paint().apply {
        color = Color.parseColor("#C41B1B")
        strokeWidth = 2f
    }
    
    private var swipeThreshold = 0.5f
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPos = viewHolder.adapterPosition
        val toPos = target.adapterPosition
        onMove(fromPos, toPos)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            val task = adapter.currentList[position]
            onDelete(task)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.height
        
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // Swipe para esquerda (dX negativo)
            if (dX < 0) {
                val swipeWidth = abs(dX)
                val maxSwipeWidth = 150f * context.resources.displayMetrics.density
                
                // Limitar a largura máxima do slide
                val limitedDx = if (swipeWidth > maxSwipeWidth) -maxSwipeWidth else dX
                
                // Desenhar fundo vermelho
                deleteBackground.setBounds(
                    itemView.right + limitedDx.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                deleteBackground.draw(c)
                
                // Desenhar divisor vertical
                val buttonWidth = abs(limitedDx)
                if (buttonWidth > 40 * context.resources.displayMetrics.density) {
                    val dividerX = itemView.right - 50 * context.resources.displayMetrics.density
                    c.drawLine(
                        dividerX,
                        itemView.top + 10f,
                        dividerX,
                        itemView.bottom - 10f,
                        dividerPaint
                    )
                }
                
                // Desenhar X branco
                val iconSize = 18 * context.resources.displayMetrics.density
                val iconCenterX = itemView.right - 25 * context.resources.displayMetrics.density
                val iconCenterY = itemView.top + itemHeight / 2f
                
                if (buttonWidth > 30 * context.resources.displayMetrics.density) {
                    // Desenhar X
                    c.drawLine(
                        iconCenterX - iconSize / 2,
                        iconCenterY - iconSize / 2,
                        iconCenterX + iconSize / 2,
                        iconCenterY + iconSize / 2,
                        deleteIconPaint
                    )
                    c.drawLine(
                        iconCenterX + iconSize / 2,
                        iconCenterY - iconSize / 2,
                        iconCenterX - iconSize / 2,
                        iconCenterY + iconSize / 2,
                        deleteIconPaint
                    )
                }
                
                // Aplicar translação ao item
                itemView.translationX = limitedDx
            }
        } else {
            // Drag (mover)
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
    
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.translationX = 0f
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = swipeThreshold
    
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 2
    
    override fun isLongPressDragEnabled(): Boolean = true
    
    override fun isItemViewSwipeEnabled(): Boolean = true
}
