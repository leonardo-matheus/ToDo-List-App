package com.example.todo.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.databinding.FragmentResetPasswordBinding
import com.example.todo.util.ViewModelFactory

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AuthViewModel
    private var email: String = ""
    private var resetToken: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as TodoApp
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(app.repository)
        )[AuthViewModel::class.java]

        email = arguments?.getString("email") ?: ""
        resetToken = arguments?.getString("resetToken") ?: ""
        
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnResetPassword.setOnClickListener {
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            
            var hasError = false
            
            if (password.isEmpty()) {
                binding.tilPassword.error = "Digite a nova senha"
                hasError = true
            } else if (password.length < 6) {
                binding.tilPassword.error = "A senha deve ter pelo menos 6 caracteres"
                hasError = true
            } else {
                binding.tilPassword.error = null
            }
            
            if (confirmPassword.isEmpty()) {
                binding.tilConfirmPassword.error = "Confirme a nova senha"
                hasError = true
            } else if (password != confirmPassword) {
                binding.tilConfirmPassword.error = "As senhas não coincidem"
                hasError = true
            } else {
                binding.tilConfirmPassword.error = null
            }
            
            if (hasError) return@setOnClickListener
            
            viewModel.resetPassword(email, resetToken, password, confirmPassword)
        }

        binding.tvBack.setOnClickListener {
            // Voltar para o login
            findNavController().navigate(R.id.action_resetPassword_to_login)
        }
    }

    private fun observeViewModel() {
        viewModel.forgotPasswordResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ForgotPasswordResult.Loading -> {
                    binding.btnResetPassword.isEnabled = false
                    binding.btnResetPassword.text = ""
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ForgotPasswordResult.PasswordReset -> {
                    binding.btnResetPassword.isEnabled = true
                    binding.btnResetPassword.text = "Redefinir Senha"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Senha redefinida com sucesso!", Toast.LENGTH_LONG).show()
                    findNavController().navigate(R.id.action_resetPassword_to_login)
                }
                is ForgotPasswordResult.Error -> {
                    binding.btnResetPassword.isEnabled = true
                    binding.btnResetPassword.text = "Redefinir Senha"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    binding.btnResetPassword.isEnabled = true
                    binding.btnResetPassword.text = "Redefinir Senha"
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
