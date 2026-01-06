package com.example.todo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.todo.ui.auth.AuthActivity
import com.example.todo.ui.lists.ListsActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as TodoApp
        
        lifecycleScope.launch {
            val isLoggedIn = app.repository.isLoggedIn()
            
            val intent = if (isLoggedIn) {
                Intent(this@MainActivity, ListsActivity::class.java)
            } else {
                Intent(this@MainActivity, AuthActivity::class.java)
            }
            
            startActivity(intent)
            finish()
        }
    }
}
