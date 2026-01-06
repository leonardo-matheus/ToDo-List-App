package com.example.todo.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.databinding.FragmentSettingsBinding
import com.example.todo.ui.auth.AuthActivity
import com.example.todo.ui.lists.ListsViewModel
import com.example.todo.util.ThemeManager
import com.example.todo.util.ViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ListsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as TodoApp).repository)
    }
    
    private lateinit var themeManager: ThemeManager
    private lateinit var app: TodoApp

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        app = requireActivity().application as TodoApp
        themeManager = app.themeManager

        setupUserInfo()
        setupDarkModeSwitch()
        setupClickListeners()
    }

    private fun setupUserInfo() {
        lifecycleScope.launch {
            app.repository.getUserInfo().collect { user ->
                user?.let {
                    binding.tvUsername.text = it.username
                    binding.tvEmail.text = it.email
                }
            }
        }
    }
    
    private fun setupDarkModeSwitch() {
        lifecycleScope.launch {
            val currentMode = themeManager.themeMode.first()
            binding.switchDarkMode.isChecked = themeManager.isDarkModeEnabled(currentMode)
        }
        
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                val newMode = if (isChecked) ThemeManager.MODE_DARK else ThemeManager.MODE_LIGHT
                themeManager.setThemeMode(newMode)
            }
        }
    }

    private fun setupClickListeners() {
        binding.itemUsername.setOnClickListener {
            showChangeNameDialog()
        }
        
        binding.itemEmail.setOnClickListener {
            showChangeEmailDialog()
        }
        
        binding.itemPassword.setOnClickListener {
            showChangePasswordDialog()
        }
        
        binding.itemSync.setOnClickListener {
            viewModel.sync()
            Toast.makeText(context, R.string.sync_success, Toast.LENGTH_SHORT).show()
        }

        binding.itemLogout.setOnClickListener {
            showLogoutDialog()
        }
    }
    
    private fun showChangeNameDialog() {
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.new_name)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 32, 48, 0)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText(binding.tvUsername.text)
        }
        inputLayout.addView(editText)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.change_name)
            .setView(inputLayout)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = editText.text?.toString()?.trim() ?: ""
                if (newName.isNotEmpty()) {
                    updateUsername(newName)
                } else {
                    Toast.makeText(context, R.string.field_required, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showChangeEmailDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val emailLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.new_email)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val emailEdit = TextInputEditText(emailLayout.context).apply {
            setText(binding.tvEmail.text)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        emailLayout.addView(emailEdit)
        container.addView(emailLayout)
        
        val passwordLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.current_password)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = 32
        }
        val passwordEdit = TextInputEditText(passwordLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        passwordLayout.addView(passwordEdit)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 32 }
        container.addView(passwordLayout, params)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.change_email)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newEmail = emailEdit.text?.toString()?.trim() ?: ""
                val password = passwordEdit.text?.toString() ?: ""
                
                when {
                    newEmail.isEmpty() -> Toast.makeText(context, R.string.field_required, Toast.LENGTH_SHORT).show()
                    password.isEmpty() -> Toast.makeText(context, R.string.password_required, Toast.LENGTH_SHORT).show()
                    else -> updateEmail(newEmail, password)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showChangePasswordDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val currentLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.current_password)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val currentEdit = TextInputEditText(currentLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        currentLayout.addView(currentEdit)
        container.addView(currentLayout)
        
        val newLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.new_password)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val newEdit = TextInputEditText(newLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        newLayout.addView(newEdit)
        val params1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 32 }
        container.addView(newLayout, params1)
        
        val confirmLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.confirm_new_password)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val confirmEdit = TextInputEditText(confirmLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        confirmLayout.addView(confirmEdit)
        val params2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 32 }
        container.addView(confirmLayout, params2)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.change_password)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val current = currentEdit.text?.toString() ?: ""
                val new = newEdit.text?.toString() ?: ""
                val confirm = confirmEdit.text?.toString() ?: ""
                
                when {
                    current.isEmpty() || new.isEmpty() || confirm.isEmpty() -> 
                        Toast.makeText(context, R.string.field_required, Toast.LENGTH_SHORT).show()
                    new != confirm -> 
                        Toast.makeText(context, R.string.passwords_dont_match, Toast.LENGTH_SHORT).show()
                    else -> updatePassword(current, new, confirm)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun updateUsername(name: String) {
        lifecycleScope.launch {
            val result = app.repository.updateUsername(name)
            result.fold(
                onSuccess = { newName ->
                    binding.tvUsername.text = newName
                    Toast.makeText(context, R.string.name_updated, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(context, e.message ?: getString(R.string.error_updating), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    
    private fun updateEmail(email: String, password: String) {
        lifecycleScope.launch {
            val result = app.repository.updateEmail(email, password)
            result.fold(
                onSuccess = { newEmail ->
                    binding.tvEmail.text = newEmail
                    Toast.makeText(context, R.string.email_updated, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(context, e.message ?: getString(R.string.error_updating), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    
    private fun updatePassword(current: String, new: String, confirm: String) {
        lifecycleScope.launch {
            val result = app.repository.updatePassword(current, new, confirm)
            result.fold(
                onSuccess = {
                    Toast.makeText(context, R.string.password_updated, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(context, e.message ?: getString(R.string.error_updating), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout)
            .setMessage(R.string.confirm_logout)
            .setPositiveButton(R.string.logout) { _, _ ->
                performLogout()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            app.repository.logout()
            
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
