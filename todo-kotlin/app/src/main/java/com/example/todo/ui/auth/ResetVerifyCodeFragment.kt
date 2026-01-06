package com.example.todo.ui.auth

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
import com.example.todo.databinding.FragmentResetVerifyCodeBinding
import com.example.todo.util.ViewModelFactory

class ResetVerifyCodeFragment : Fragment() {

    private var _binding: FragmentResetVerifyCodeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AuthViewModel
    private var email: String = ""
    private var hasShownResendToast = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetVerifyCodeBinding.inflate(inflater, container, false)
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
        Log.d("ResetVerifyCode", "DEBUG email recebido: '$email'")
        
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            
            if (code.isEmpty()) {
                binding.tilCode.error = "Digite o código"
                return@setOnClickListener
            }
            
            if (code.length != 6) {
                binding.tilCode.error = "O código deve ter 6 dígitos"
                return@setOnClickListener
            }
            
            binding.tilCode.error = null
            Log.d("ResetVerifyCode", "DEBUG chamando verifyResetCode: email='$email', code='$code'")
            viewModel.verifyResetCode(email, code)
        }

        binding.tvResend.setOnClickListener {
            hasShownResendToast = true
            viewModel.forgotPassword(email)
            Toast.makeText(context, "Reenviando código...", Toast.LENGTH_SHORT).show()
        }

        binding.tvBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        viewModel.forgotPasswordResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ForgotPasswordResult.Loading -> {
                    binding.btnVerify.isEnabled = false
                    binding.btnVerify.text = ""
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ForgotPasswordResult.CodeSent -> {
                    binding.btnVerify.isEnabled = true
                    binding.btnVerify.text = "Verificar"
                    binding.progressBar.visibility = View.GONE
                    if (hasShownResendToast) {
                        Toast.makeText(context, "Código reenviado para seu e-mail", Toast.LENGTH_SHORT).show()
                        hasShownResendToast = false
                    }
                }
                is ForgotPasswordResult.CodeVerified -> {
                    binding.btnVerify.isEnabled = true
                    binding.btnVerify.text = "Verificar"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Código verificado!", Toast.LENGTH_SHORT).show()
                    val bundle = Bundle().apply {
                        putString("email", email)
                        putString("resetToken", result.resetToken)
                    }
                    findNavController().navigate(R.id.action_resetVerifyCode_to_resetPassword, bundle)
                }
                is ForgotPasswordResult.PasswordReset -> {
                    // Não usado aqui
                }
                is ForgotPasswordResult.Error -> {
                    binding.btnVerify.isEnabled = true
                    binding.btnVerify.text = "Verificar"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                is ForgotPasswordResult.Idle -> {
                    binding.btnVerify.isEnabled = true
                    binding.btnVerify.text = "Verificar"
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
