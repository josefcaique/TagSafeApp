package com.digitalsix.YouSafe

import android.content.Intent
import android.os.Bundle
import android.graphics.Rect
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.digitalsix.YouSafe.network.LoginRequest
import com.digitalsix.YouSafe.network.RetrofitInstance
import com.digitalsix.YouSafe.utils.SessionManager
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlin.math.max

class LoginActivity : AppCompatActivity() {

    private lateinit var editTextEmail: EditText
    private lateinit var editTextSenha: EditText
    private lateinit var buttonLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var sessionManager: SessionManager
    private lateinit var loginScrollView: ScrollView
    private lateinit var textViewLoginError: TextView
    private lateinit var textViewForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        // Se já estiver logado, verificar primeiro acesso
        if (sessionManager.isLoggedIn()) {
            verificarPrimeiroAcesso()
            return
        }

        // Inicializar views
        loginScrollView = findViewById(R.id.loginScrollView)
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextSenha = findViewById(R.id.editTextSenha)
        buttonLogin = findViewById(R.id.buttonLogin)
        progressBar = findViewById(R.id.progressBar)
        textViewLoginError = findViewById(R.id.textViewLoginError)
        textViewForgotPassword = findViewById(R.id.textViewForgotPassword)
        limparErroLogin()

        configurarInsetsTeclado()
        configurarScrollParaFoco()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        buttonLogin.setOnClickListener {
            limparErroLogin()
            val email = editTextEmail.text.toString().trim()
            val senha = editTextSenha.text.toString().trim()

            if (validarCampos(email, senha)) {
                fazerLogin(email, senha)
            }
        }

        textViewForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    private fun validarCampos(email: String, senha: String): Boolean {
        when {
            email.isEmpty() -> {
                Toast.makeText(this, "Digite o email", Toast.LENGTH_SHORT).show()
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show()
                return false
            }
            senha.isEmpty() -> {
                Toast.makeText(this, "Digite a senha", Toast.LENGTH_SHORT).show()
                return false
            }
            else -> return true
        }
    }

    private fun fazerLogin(email: String, senha: String) {
        progressBar.visibility = View.VISIBLE
        buttonLogin.isEnabled = false
        buttonLogin.text = "Entrando..."
        limparErroLogin()

        lifecycleScope.launch {
            try {
                val request = LoginRequest(email = email, senha = senha)
                val response = RetrofitInstance.api.login(request)

                if (response.isSuccessful) {
                    val loginResponse = response.body()

                    if (loginResponse != null) {
                        limparErroLogin()
                        // Salvar sessão
                        sessionManager.saveSession(
                            token = loginResponse.token,
                            usuario = loginResponse.usuario,
                            refreshToken = loginResponse.refreshToken,
                            refreshExpiresAt = loginResponse.refreshExpiresAt
                        )

                        Toast.makeText(
                            this@LoginActivity,
                            "Login realizado com sucesso!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Verificar se é primeiro acesso
                        verificarPrimeiroAcesso()
                    } else {
                        mostrarErro("Resposta vazia do servidor")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val mensagem = extrairMensagemErro(errorBody)
                        ?: "Email ou senha inválidos"
                    mostrarErro(mensagem)
                }
            } catch (e: Exception) {
                mostrarErro("Erro de conexão: ${e.localizedMessage}")
            } finally {
                progressBar.visibility = View.GONE
                buttonLogin.isEnabled = true
                buttonLogin.text = "Entrar"
            }
        }
    }

    private fun mostrarErro(mensagem: String) {
        textViewLoginError.text = mensagem
        textViewLoginError.visibility = View.VISIBLE
    }

    private fun limparErroLogin() {
        textViewLoginError.text = ""
        textViewLoginError.visibility = View.GONE
    }

    private fun extrairMensagemErro(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(errorBody)
            when {
                json.has("message") -> json.optString("message")
                json.has("mensagem") -> json.optString("mensagem")
                json.has("error") -> json.optString("error")
                else -> null
            }?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun configurarInsetsTeclado() {
        ViewCompat.setOnApplyWindowInsetsListener(loginScrollView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomPadding = max(imeInsets.bottom, systemBarsInsets.bottom)
            view.updatePadding(bottom = bottomPadding)
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                currentFocus?.let { focused ->
                    view.post { garantirVisibilidadeDoFoco(focused) }
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(loginScrollView)
    }

    private fun configurarScrollParaFoco() {
        val focusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.post { garantirVisibilidadeDoFoco(view) }
            }
        }
        editTextEmail.onFocusChangeListener = focusListener
        editTextSenha.onFocusChangeListener = focusListener
    }

    private fun garantirVisibilidadeDoFoco(target: View) {
        if (!isDescendenteDe(target, loginScrollView)) return

        val rect = Rect()
        target.getDrawingRect(rect)
        loginScrollView.offsetDescendantRectToMyCoords(target, rect)

        val extra = (16 * resources.displayMetrics.density).toInt()
        val visibleTop = loginScrollView.scrollY + loginScrollView.paddingTop
        val visibleBottom = loginScrollView.scrollY + loginScrollView.height - loginScrollView.paddingBottom

        when {
            rect.bottom + extra > visibleBottom ->
                loginScrollView.smoothScrollBy(0, rect.bottom + extra - visibleBottom)
            rect.top - extra < visibleTop ->
                loginScrollView.smoothScrollBy(0, rect.top - extra - visibleTop)
        }
    }

    private fun isDescendenteDe(child: View, parent: View): Boolean {
        var current: View? = child
        while (current != null && current != parent) {
            current = current.parent as? View
        }
        return current == parent
    }

    /**
     * Verificar se é primeiro acesso e redirecionar adequadamente
     */
    private fun verificarPrimeiroAcesso() {
        val usuario = sessionManager.getUsuario()

        if (usuario != null && usuario.primeiroAcesso) {
            // É primeiro acesso - ir para tela de reset de senha
            irParaResetPassword()
        } else {
            // Não é primeiro acesso - ir para MainActivity
            irParaMainActivity()
        }
    }

    private fun irParaResetPassword() {
        val intent = Intent(this, ResetPasswordActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun irParaMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
