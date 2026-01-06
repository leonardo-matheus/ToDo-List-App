package com.example.todo.ui.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todo.data.model.AuthData
import com.example.todo.data.repository.TodoRepository
import kotlinx.coroutines.launch

sealed class AuthResult {
    data class Success(val authData: AuthData) : AuthResult()
    data class Error(val message: String) : AuthResult()
    data class RequiresVerification(val email: String, val message: String) : AuthResult()
    object Loading : AuthResult()
}

sealed class ForgotPasswordResult {
    object Idle : ForgotPasswordResult()
    object Loading : ForgotPasswordResult()
    object CodeSent : ForgotPasswordResult()
    data class CodeVerified(val resetToken: String) : ForgotPasswordResult()
    object PasswordReset : ForgotPasswordResult()
    data class Error(val message: String) : ForgotPasswordResult()
}

class AuthViewModel(private val repository: TodoRepository) : ViewModel() {

    private val _authResult = MutableLiveData<AuthResult>()
    val authResult: LiveData<AuthResult> = _authResult
    
    private val _verifyResult = MutableLiveData<AuthResult>()
    val verifyResult: LiveData<AuthResult> = _verifyResult
    
    private val _forgotPasswordResult = MutableLiveData<ForgotPasswordResult>(ForgotPasswordResult.Idle)
    val forgotPasswordResult: LiveData<ForgotPasswordResult> = _forgotPasswordResult
    
    var pendingEmail: String = ""

    fun login(email: String, password: String) {
        Log.d("AuthViewModel", "Tentando login com email: $email")
        viewModelScope.launch {
            _authResult.value = AuthResult.Loading
            val result = repository.login(email, password)
            Log.d("AuthViewModel", "Resultado do login: $result")
            _authResult.value = when {
                result.isSuccess -> {
                    Log.d("AuthViewModel", "Login bem sucedido!")
                    AuthResult.Success(result.getOrNull()!!)
                }
                result.exceptionOrNull() is TodoRepository.VerificationRequiredException -> {
                    val ex = result.exceptionOrNull() as TodoRepository.VerificationRequiredException
                    pendingEmail = ex.email
                    AuthResult.RequiresVerification(ex.email, ex.message ?: "Verificação necessária")
                }
                else -> {
                    Log.e("AuthViewModel", "Erro no login: ${result.exceptionOrNull()?.message}")
                    AuthResult.Error(result.exceptionOrNull()?.message ?: "Erro no login")
                }
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        Log.d("AuthViewModel", "Tentando registro com email: $email")
        viewModelScope.launch {
            _authResult.value = AuthResult.Loading
            val result = repository.register(username, email, password)
            Log.d("AuthViewModel", "Resultado do registro: $result")
            _authResult.value = when {
                result.isSuccess -> {
                    AuthResult.Success(result.getOrNull()!!)
                }
                result.exceptionOrNull() is TodoRepository.VerificationRequiredException -> {
                    val ex = result.exceptionOrNull() as TodoRepository.VerificationRequiredException
                    pendingEmail = ex.email
                    AuthResult.RequiresVerification(ex.email, ex.message ?: "Código enviado")
                }
                else -> {
                    AuthResult.Error(result.exceptionOrNull()?.message ?: "Erro no registro")
                }
            }
        }
    }
    
    fun verifyEmail(email: String, code: String) {
        viewModelScope.launch {
            _verifyResult.value = AuthResult.Loading
            val result = repository.verifyEmail(email, code)
            _verifyResult.value = if (result.isSuccess) {
                AuthResult.Success(result.getOrNull()!!)
            } else {
                AuthResult.Error(result.exceptionOrNull()?.message ?: "Código inválido")
            }
        }
    }
    
    fun resendCode(email: String) {
        viewModelScope.launch {
            val result = repository.resendCode(email)
            // Não precisa atualizar UI, só toast
        }
    }
    
    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _forgotPasswordResult.value = ForgotPasswordResult.Loading
            val result = repository.forgotPassword(email)
            _forgotPasswordResult.value = if (result.isSuccess) {
                ForgotPasswordResult.CodeSent
            } else {
                ForgotPasswordResult.Error(result.exceptionOrNull()?.message ?: "Erro ao enviar código")
            }
        }
    }
    
    fun verifyResetCode(email: String, code: String) {
        viewModelScope.launch {
            _forgotPasswordResult.value = ForgotPasswordResult.Loading
            val result = repository.verifyResetCode(email, code)
            _forgotPasswordResult.value = if (result.isSuccess) {
                ForgotPasswordResult.CodeVerified(result.getOrNull()!!)
            } else {
                ForgotPasswordResult.Error(result.exceptionOrNull()?.message ?: "Código inválido")
            }
        }
    }
    
    fun resetPassword(email: String, resetToken: String, newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            _forgotPasswordResult.value = ForgotPasswordResult.Loading
            val result = repository.resetPassword(email, resetToken, newPassword, confirmPassword)
            _forgotPasswordResult.value = if (result.isSuccess) {
                ForgotPasswordResult.PasswordReset
            } else {
                ForgotPasswordResult.Error(result.exceptionOrNull()?.message ?: "Erro ao redefinir senha")
            }
        }
    }
    
    fun resetForgotPasswordState() {
        _forgotPasswordResult.value = ForgotPasswordResult.Idle
    }
    
    suspend fun isLoggedIn(): Boolean = repository.isLoggedIn()
}
