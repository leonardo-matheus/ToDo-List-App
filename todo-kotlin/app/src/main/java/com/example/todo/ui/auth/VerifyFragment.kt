package com.example.todo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.todo.TodoApp
import com.example.todo.databinding.FragmentVerifyBinding
import com.example.todo.ui.lists.ListsActivity
import com.example.todo.util.ViewModelFactory

class VerifyFragment : Fragment() {
    
    private var _binding: FragmentVerifyBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: AuthViewModel
    private var email: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerifyBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val app = requireActivity().application as TodoApp
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(app.repository)
        )[AuthViewModel::class.java]
        
        email = arguments?.getString("email") ?: viewModel.pendingEmail
        
        binding.tvSubtitle.text = "Digite o código de 6 dígitos enviado para\n$email"
        
        setupObservers()
        setupListeners()
    }
    
    private fun setupObservers() {
        viewModel.verifyResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthResult.Loading -> {
                    binding.btnVerify.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }
                is AuthResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Conta ativada com sucesso!", Toast.LENGTH_SHORT).show()
                    navigateToLists()
                }
                is AuthResult.Error -> {
                    binding.btnVerify.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
    
    private fun setupListeners() {
        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            
            if (code.length != 6) {
                binding.etCode.error = "Digite o código de 6 dígitos"
                return@setOnClickListener
            }
            
            viewModel.verifyEmail(email, code)
        }
        
        binding.tvResend.setOnClickListener {
            viewModel.resendCode(email)
            Toast.makeText(requireContext(), "Novo código enviado!", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun navigateToLists() {
        startActivity(Intent(requireContext(), ListsActivity::class.java))
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
