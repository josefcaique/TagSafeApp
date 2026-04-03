package com.digitalsix.YouSafe

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.digitalsix.YouSafe.network.ForgotPasswordRequest
import com.digitalsix.YouSafe.network.RetrofitInstance
import kotlinx.coroutines.launch
import org.json.JSONObject

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var editTextEmail: EditText
    private lateinit var buttonSend: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewBack: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        editTextEmail = findViewById(R.id.editTextEmailForgot)
        buttonSend = findViewById(R.id.buttonSendReset)
        progressBar = findViewById(R.id.progressBarForgot)
        textViewBack = findViewById(R.id.textViewBackToLogin)

        buttonSend.setOnClickListener {
            val email = editTextEmail.text.toString().trim()
            if (validarEmail(email)) {
                enviarRecuperacao(email)
            }
        }

        textViewBack.setOnClickListener {
            finish()
        }
    }

    private fun validarEmail(email: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "Por favor, insira seu e-mail", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "E-mail inválido", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun enviarRecuperacao(email: String) {
        progressBar.visibility = View.VISIBLE
        buttonSend.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.forgotPassword(ForgotPasswordRequest(email))
                if (response.isSuccessful) {
                    val msg = response.body()?.message ?: "Se o e-mail existir, enviaremos o link de redefinição."
                    Toast.makeText(this@ForgotPasswordActivity, msg, Toast.LENGTH_LONG).show()
                    // Opcional: voltar para o login após sucesso
                    // finish()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = extrairMensagemErro(errorBody) ?: "Erro ao solicitar recuperação"
                    Toast.makeText(this@ForgotPasswordActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ForgotPasswordActivity, "Erro de conexão: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                buttonSend.isEnabled = true
            }
        }
    }

    private fun extrairMensagemErro(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(errorBody)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
