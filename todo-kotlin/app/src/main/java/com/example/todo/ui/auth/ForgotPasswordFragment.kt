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
import com.example.todo.databinding.FragmentForgotPasswordBinding
import com.example.todo.util.ViewModelFactory

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as TodoApp
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(app.repository)
        )[AuthViewModel::class.java]

        // Recuperar email passado como argumento
        arguments?.getString("email")?.let { email ->
            binding.etEmail.setText(email)
        }

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnSendCode.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            
            if (email.isEmpty()) {
                binding.tilEmail.error = "Digite seu e-mail"
                return@setOnClickListener
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "E-mail inválido"
                return@setOnClickListener
            }
            
            binding.tilEmail.error = null
            viewModel.forgotPassword(email)
        }

        binding.tvBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        viewModel.forgotPasswordResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ForgotPasswordResult.Loading -> {
                    binding.btnSendCode.isEnabled = false
                    binding.btnSendCode.text = ""
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ForgotPasswordResult.CodeSent -> {
                    binding.btnSendCode.isEnabled = true
                    binding.btnSendCode.text = "Enviar Código"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Código enviado para seu e-mail", Toast.LENGTH_SHORT).show()
                    val email = binding.etEmail.text.toString().trim()
                    val bundle = Bundle().apply {
                        putString("email", email)
                    }
                    // Resetar estado antes de navegar
                    viewModel.resetForgotPasswordState()
                    findNavController().navigate(R.id.action_forgotPassword_to_resetVerifyCode, bundle)
                }
                is ForgotPasswordResult.CodeVerified -> {
                    // Não usado aqui
                }
                is ForgotPasswordResult.PasswordReset -> {
                    // Não usado aqui
                }
                is ForgotPasswordResult.Error -> {
                    binding.btnSendCode.isEnabled = true
                    binding.btnSendCode.text = "Enviar Código"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                is ForgotPasswordResult.Idle -> {
                    binding.btnSendCode.isEnabled = true
                    binding.btnSendCode.text = "Enviar Código"
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
