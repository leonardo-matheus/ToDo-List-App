package com.example.todo.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.data.local.entity.TodoListEntity
import com.example.todo.databinding.ActivityMainNewBinding
import com.example.todo.ui.auth.AuthActivity
import com.example.todo.ui.calendar.CalendarFragment
import com.example.todo.ui.home.HomeFragment
import com.example.todo.ui.listdetail.ListDetailFragment
import com.example.todo.ui.settings.SettingsFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivityNew : AppCompatActivity(), HomeFragment.OnFolderSelectedListener {

    private lateinit var binding: ActivityMainNewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if logged in
        lifecycleScope.launch {
            val app = application as TodoApp
            val token = app.repository.getToken().first()

            if (token.isNullOrEmpty()) {
                startActivity(Intent(this@MainActivityNew, AuthActivity::class.java))
                finish()
                return@launch
            }

            setupUI()
        }
    }

    private fun setupUI() {
        binding = ActivityMainNewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()

        // Show home fragment by default
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_calendar -> {
                    loadFragment(CalendarFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onFolderSelected(list: TodoListEntity) {
        val fragment = ListDetailFragment.newInstance(list)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
