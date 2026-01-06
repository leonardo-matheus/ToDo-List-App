package com.example.todo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.todo.R
import com.example.todo.TodoApp
import com.example.todo.databinding.FragmentLoginBinding
import com.example.todo.ui.lists.ListsActivity
import com.example.todo.ui.main.MainActivityNew
import com.example.todo.util.ViewModelFactory

class LoginFragment : Fragment() {
    
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d("LoginFragment", "onViewCreated")
        
        val app = requireActivity().application as TodoApp
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(app.repository)
        )[AuthViewModel::class.java]
        
        setupObservers()
        setupListeners()
    }
    
    private fun setupObservers() {
        viewModel.authResult.observe(viewLifecycleOwner) { result ->
            Log.d("LoginFragment", "authResult: $result")
            when (result) {
                is AuthResult.Loading -> {
                    binding.btnLogin.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }
                is AuthResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Login realizado!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                }
                is AuthResult.RequiresVerification -> {
                    binding.btnLogin.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    navigateToVerify(result.email)
                }
                is AuthResult.Error -> {
                    binding.btnLogin.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun setupListeners() {
        Log.d("LoginFragment", "setupListeners - configurando botao")
        binding.btnLogin.setOnClickListener {
            Log.d("LoginFragment", "BOTAO LOGIN CLICADO!")
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            
            Log.d("LoginFragment", "Email: $email, Password length: ${password.length}")
            
            if (email.isEmpty()) {
                binding.etEmail.error = "Digite seu email"
                return@setOnClickListener
            }
            
            if (password.isEmpty()) {
                binding.etPassword.error = "Digite sua senha"
                return@setOnClickListener
            }
            
            Log.d("LoginFragment", "Chamando viewModel.login()")
            viewModel.login(email, password)
        }
        
        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
        
        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            viewModel.resetForgotPasswordState()
            val bundle = Bundle().apply {
                putString("email", email)
            }
            findNavController().navigate(R.id.action_loginFragment_to_forgotPassword, bundle)
        }
    }
    
    private fun navigateToMain() {
        startActivity(Intent(requireContext(), MainActivityNew::class.java))
        requireActivity().finish()
    }
    
    private fun navigateToVerify(email: String) {
        val bundle = Bundle().apply { putString("email", email) }
        findNavController().navigate(R.id.action_loginFragment_to_verifyFragment, bundle)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
