package com.digitalsix.YouSafe

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.digitalsix.YouSafe.network.ResetPasswordRequest
import com.digitalsix.YouSafe.network.RetrofitInstance
import com.digitalsix.YouSafe.utils.SessionManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Activity para redefinição de senha no primeiro acesso
 *
 * Fluxo:
 * 1. Usuário faz login
 * 2. Se primeiro_acesso == true, é redirecionado para esta tela
 * 3. Define nova senha
 * 4. Senha é atualizada via POST /auth/resetSenha/:uuid
 * 5. Redireciona para MainActivity
 */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var inputNovaSenha: TextInputEditText
    private lateinit var inputConfirmarSenha: TextInputEditText
    private lateinit var buttonConfirmar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        sessionManager = SessionManager(this)

        // Verificar se está logado
        if (!sessionManager.isLoggedIn()) {
            irParaLogin()
            return
        }

        // Verificar se realmente é primeiro acesso
        val usuario = sessionManager.getUsuario()
        if (usuario?.primeiroAcesso != true) {
            // Não é primeiro acesso, ir direto para Main
            irParaMain()
            return
        }

        bindViews()
        setupClickListeners()
    }

    private fun bindViews() {
        inputNovaSenha = findViewById(R.id.inputNovaSenha)
        inputConfirmarSenha = findViewById(R.id.inputConfirmarSenha)
        buttonConfirmar = findViewById(R.id.buttonConfirmar)
    }

    private fun setupClickListeners() {
        buttonConfirmar.setOnClickListener {
            val novaSenha = inputNovaSenha.text.toString().trim()
            val confirmarSenha = inputConfirmarSenha.text.toString().trim()

            if (validarSenhas(novaSenha, confirmarSenha)) {
                resetarSenha(novaSenha)
            }
        }
    }

    private fun validarSenhas(senha: String, confirmacao: String): Boolean {
        when {
            senha.isEmpty() -> {
                Toast.makeText(this, "Digite a nova senha", Toast.LENGTH_SHORT).show()
                return false
            }
            senha.length < 8 -> {
                Toast.makeText(this, "Senha deve ter no mínimo 8 caracteres", Toast.LENGTH_SHORT).show()
                return false
            }
            !senha.matches(Regex(".*[A-Z].*")) -> {
                Toast.makeText(this, "Senha deve conter pelo menos uma letra maiúscula", Toast.LENGTH_SHORT).show()
                return false
            }
            !senha.matches(Regex(".*[a-z].*")) -> {
                Toast.makeText(this, "Senha deve conter pelo menos uma letra minúscula", Toast.LENGTH_SHORT).show()
                return false
            }
            !senha.matches(Regex(".*\\d.*")) -> {
                Toast.makeText(this, "Senha deve conter pelo menos um número", Toast.LENGTH_SHORT).show()
                return false
            }
            !senha.matches(Regex(".*[$*&@#!%^()].*")) -> {
                Toast.makeText(this, "Senha deve conter pelo menos um caractere especial ($*&@#!%^())", Toast.LENGTH_SHORT).show()
                return false
            }
            senha != confirmacao -> {
                Toast.makeText(this, "As senhas não coincidem", Toast.LENGTH_SHORT).show()
                return false
            }
            else -> return true
        }
    }

    private fun resetarSenha(senha: String) {
        val usuario = sessionManager.getUsuario() ?: return
        val token = sessionManager.getTokenWithBearer() ?: return
        val usuarioUuid = usuario.publicId ?: run {
            Toast.makeText(this, "Usuário sem UUID para redefinir senha.", Toast.LENGTH_LONG).show()
            return
        }

        buttonConfirmar.isEnabled = false
        buttonConfirmar.text = "Aguarde..."

        lifecycleScope.launch {
            try {
                val request = ResetPasswordRequest(
                    senha = senha
                )

                val response = RetrofitInstance.api.resetPassword(
                    token = token,
                    userUuid = usuarioUuid,
                    request = request
                )

                if (response.isSuccessful) {
                    sessionManager.clearSession()
                    Toast.makeText(
                        this@ResetPasswordActivity,
                        "✅ Senha atualizada! Faça login novamente.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Atualizar flag de primeiro acesso no SessionManager
                    sessionManager.atualizarPrimeiroAcesso(false)

                    // Ir para MainActivity
                    irParaLogin()

                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(
                        this@ResetPasswordActivity,
                        "Erro: ${errorBody ?: "Falha ao atualizar senha"}",
                        Toast.LENGTH_LONG
                    ).show()
                    buttonConfirmar.isEnabled = true
                    buttonConfirmar.text = "Confirmar"
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ResetPasswordActivity,
                    "Erro de conexão: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                buttonConfirmar.isEnabled = true
                buttonConfirmar.text = "Confirmar"
            }
        }
    }

    private fun irParaMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun irParaLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
