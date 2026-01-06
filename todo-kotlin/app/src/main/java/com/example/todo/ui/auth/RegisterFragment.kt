package com.example.todo.ui.auth

import android.content.Intent
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
import com.example.todo.databinding.FragmentRegisterBinding
import com.example.todo.ui.lists.ListsActivity
import com.example.todo.util.ViewModelFactory

class RegisterFragment : Fragment() {
    
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
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
            when (result) {
                is AuthResult.Loading -> {
                    binding.btnRegister.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }
                is AuthResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                    navigateToLists()
                }
                is AuthResult.RequiresVerification -> {
                    binding.btnRegister.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    navigateToVerify(result.email)
                }
                is AuthResult.Error -> {
                    binding.btnRegister.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            
            if (username.isEmpty()) {
                binding.etUsername.error = "Digite seu nome"
                return@setOnClickListener
            }
            
            if (email.isEmpty()) {
                binding.etEmail.error = "Digite seu email"
                return@setOnClickListener
            }
            
            if (password.isEmpty() || password.length < 6) {
                binding.etPassword.error = "Senha deve ter no mínimo 6 caracteres"
                return@setOnClickListener
            }
            
            viewModel.register(username, email, password)
        }
        
        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }
    }
    
    private fun navigateToLists() {
        startActivity(Intent(requireContext(), ListsActivity::class.java))
        requireActivity().finish()
    }
    
    private fun navigateToVerify(email: String) {
        val bundle = Bundle().apply { putString("email", email) }
        findNavController().navigate(R.id.action_registerFragment_to_verifyFragment, bundle)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
