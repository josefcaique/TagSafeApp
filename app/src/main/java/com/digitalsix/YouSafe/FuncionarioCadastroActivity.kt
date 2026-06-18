package com.digitalsix.YouSafe

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Rect
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.digitalsix.YouSafe.network.RetrofitInstance
import com.digitalsix.YouSafe.network.Usuario
import com.digitalsix.YouSafe.utils.SessionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.max

class FuncionarioCadastroActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var nfcAdapter: NfcAdapter? = null
    private var unidadeSelecionada: UnidadeCadastro? = null
    private val unidadesCadastro = mutableListOf<UnidadeCadastro>()

    private lateinit var toolbar: MaterialToolbar
    private lateinit var funcionarioCadastroScrollView: ScrollView
    private lateinit var textViewBemVindoCadastro: TextView
    private lateinit var textViewUnidadeCadastro: TextView
    private lateinit var layoutUnidadeFuncionario: TextInputLayout
    private lateinit var spinnerUnidadeFuncionario: AutoCompleteTextView
    private lateinit var textViewNfcStatus: TextView
    private lateinit var editTextNomeFuncionario: TextInputEditText
    private lateinit var editTextCpfFuncionario: TextInputEditText
    private lateinit var editTextNfcFuncionario: TextInputEditText
    private lateinit var editTextSetorDescricao: TextInputEditText
    private lateinit var editTextSetorNivel: TextInputEditText
    private lateinit var buttonLimparNfc: MaterialButton
    private lateinit var buttonCadastrarFuncionario: MaterialButton
    private lateinit var progressBarCadastroFuncionario: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_funcionario_cadastro)

        RetrofitInstance.initialize(applicationContext)
        sessionManager = SessionManager(this)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        if (!sessionManager.isLoggedIn()) {
            irParaLogin()
            return
        }

        inicializarViews()
        configurarToolbar()
        configurarInsetsTeclado()
        configurarScrollParaFoco()
        configurarTela()
        configurarBotoes()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        atualizarStatusNfc()
    }

    private fun inicializarViews() {
        funcionarioCadastroScrollView = findViewById(R.id.funcionarioCadastroScrollView)
        toolbar = findViewById(R.id.toolbarCadastroFuncionario)
        textViewBemVindoCadastro = findViewById(R.id.textViewBemVindoCadastro)
        textViewUnidadeCadastro = findViewById(R.id.textViewUnidadeCadastro)
        layoutUnidadeFuncionario = findViewById(R.id.layoutUnidadeFuncionario)
        spinnerUnidadeFuncionario = findViewById(R.id.spinnerUnidadeFuncionario)
        textViewNfcStatus = findViewById(R.id.textViewNfcStatus)
        editTextNomeFuncionario = findViewById(R.id.editTextNomeFuncionario)
        editTextCpfFuncionario = findViewById(R.id.editTextCpfFuncionario)
        editTextNfcFuncionario = findViewById(R.id.editTextNfcFuncionario)
        editTextSetorDescricao = findViewById(R.id.editTextSetorDescricao)
        editTextSetorNivel = findViewById(R.id.editTextSetorNivel)
        buttonLimparNfc = findViewById(R.id.buttonLimparNfc)
        buttonCadastrarFuncionario = findViewById(R.id.buttonCadastrarFuncionario)
        progressBarCadastroFuncionario = findViewById(R.id.progressBarCadastroFuncionario)
    }

    private fun configurarToolbar() {
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    confirmarLogout()
                    true
                }
                else -> false
            }
        }
    }

    private fun configurarTela() {
        val usuario = sessionManager.getUsuario()
        val nomeUsuario = usuario?.nome ?: "usuário"
        textViewBemVindoCadastro.text = "Olá, $nomeUsuario!"
        configurarUnidades(usuario)
    }

    private fun configurarUnidades(usuario: Usuario?) {
        unidadesCadastro.clear()
        unidadesCadastro.addAll(montarUnidadesCadastro(usuario))

        if (unidadesCadastro.isEmpty()) {
            textViewUnidadeCadastro.text = "Nenhuma unidade vinculada ao usuário."
            layoutUnidadeFuncionario.isEnabled = false
            spinnerUnidadeFuncionario.isEnabled = false
            buttonCadastrarFuncionario.isEnabled = false
            return
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            unidadesCadastro.map { it.nomeExibicao }
        )
        spinnerUnidadeFuncionario.setAdapter(adapter)
        spinnerUnidadeFuncionario.setOnItemClickListener { _, _, position, _ ->
            selecionarUnidade(unidadesCadastro[position])
        }

        selecionarUnidade(unidadesCadastro.first())
        spinnerUnidadeFuncionario.setText(unidadeSelecionada?.nomeExibicao.orEmpty(), false)
    }

    private fun montarUnidadesCadastro(usuario: Usuario?): List<UnidadeCadastro> {
        if (usuario == null) return emptyList()

        val unidades = linkedMapOf<Int, UnidadeCadastro>()
        val unidadesDoLogin = usuario.unidades.orEmpty()

        unidadesDoLogin
            .filter { (it.id ?: 0) > 0 }
            .forEach { unidade ->
                val id = unidade.id ?: return@forEach
                unidades[id] = UnidadeCadastro(
                    id = id,
                    publicId = unidade.publicId?.takeIf { it.isNotBlank() },
                    nome = unidade.nome?.takeIf { it.isNotBlank() },
                    empresa = unidade.empresa?.takeIf { it.isNotBlank() }
                )
            }

        usuario.unidadeId?.takeIf { it > 0 }?.let { id ->
            unidades.putIfAbsent(
                id,
                UnidadeCadastro(
                    id = id,
                    publicId = usuario.unidadePublicId?.takeIf { it.isNotBlank() }
                )
            )
        }

        usuario.unidadesIds.orEmpty()
            .filter { it > 0 }
            .forEachIndexed { index, id ->
                unidades.putIfAbsent(
                    id,
                    UnidadeCadastro(
                        id = id,
                        publicId = usuario.unidadesPublicIds.orEmpty().getOrNull(index)
                            ?.takeIf { it.isNotBlank() }
                    )
                )
            }

        return unidades.values.toList()
    }

    private fun selecionarUnidade(unidade: UnidadeCadastro) {
        unidadeSelecionada = unidade
        textViewUnidadeCadastro.text = "Cadastro vinculado à ${unidade.nomeExibicao}."
        layoutUnidadeFuncionario.error = null
        buttonCadastrarFuncionario.isEnabled = true
    }

    private fun configurarBotoes() {
        buttonLimparNfc.setOnClickListener {
            editTextNfcFuncionario.setText("")
            atualizarStatusNfc()
        }

        buttonCadastrarFuncionario.setOnClickListener {
            cadastrarFuncionario()
        }
    }

    private fun configurarInsetsTeclado() {
        ViewCompat.setOnApplyWindowInsetsListener(funcionarioCadastroScrollView) { view, insets ->
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
        ViewCompat.requestApplyInsets(funcionarioCadastroScrollView)
    }

    private fun configurarScrollParaFoco() {
        val focusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.post { garantirVisibilidadeDoFoco(view) }
            }
        }
        spinnerUnidadeFuncionario.onFocusChangeListener = focusListener
        editTextNomeFuncionario.onFocusChangeListener = focusListener
        editTextCpfFuncionario.onFocusChangeListener = focusListener
        editTextSetorDescricao.onFocusChangeListener = focusListener
        editTextSetorNivel.onFocusChangeListener = focusListener
    }

    private fun garantirVisibilidadeDoFoco(target: View) {
        if (!isDescendenteDe(target, funcionarioCadastroScrollView)) return

        val rect = Rect()
        target.getDrawingRect(rect)
        funcionarioCadastroScrollView.offsetDescendantRectToMyCoords(target, rect)

        val extra = (16 * resources.displayMetrics.density).toInt()
        val visibleTop = funcionarioCadastroScrollView.scrollY + funcionarioCadastroScrollView.paddingTop
        val visibleBottom = funcionarioCadastroScrollView.scrollY +
            funcionarioCadastroScrollView.height -
            funcionarioCadastroScrollView.paddingBottom

        when {
            rect.bottom + extra > visibleBottom ->
                funcionarioCadastroScrollView.smoothScrollBy(
                    0,
                    rect.bottom + extra - visibleBottom
                )
            rect.top - extra < visibleTop ->
                funcionarioCadastroScrollView.smoothScrollBy(
                    0,
                    rect.top - extra - visibleTop
                )
        }
    }

    private fun isDescendenteDe(child: View, parent: View): Boolean {
        var current: View? = child
        while (current != null && current != parent) {
            current = current.parent as? View
        }
        return current == parent
    }

    override fun onResume() {
        super.onResume()
        ativarNFC()
        atualizarStatusNfc()
    }

    override fun onPause() {
        super.onPause()
        desativarNFC()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        if (action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val nfcId = it.id.joinToString("") { byte -> "%02X".format(byte) }
                editTextNfcFuncionario.setText(nfcId)
                textViewNfcStatus.text = "Crachá lido: $nfcId"
                Toast.makeText(this, "Crachá lido com sucesso.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ativarNFC() {
        val adapter = nfcAdapter ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun desativarNFC() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (_: Exception) {
        }
    }

    private fun atualizarStatusNfc() {
        val nfc = editTextNfcFuncionario.text?.toString().orEmpty()
        textViewNfcStatus.text = when {
            nfc.isNotBlank() -> "Crachá lido: $nfc"
            nfcAdapter == null -> "Este aparelho não possui NFC."
            nfcAdapter?.isEnabled == false -> "Ative o NFC do aparelho para ler o crachá."
            else -> "Aproxime o crachá do celular para preencher o NFC."
        }
    }

    private fun cadastrarFuncionario() {
        val token = sessionManager.getTokenWithBearer()
        val unidade = unidadeSelecionada
        if (token == null || unidade == null) {
            layoutUnidadeFuncionario.error = "Selecione uma unidade"
            return
        }

        val nome = editTextNomeFuncionario.text?.toString()?.trim().orEmpty()
        val cpf = editTextCpfFuncionario.text?.toString()?.filter { it.isDigit() }.orEmpty()
        val nfc = editTextNfcFuncionario.text?.toString()?.trim().orEmpty()
        val setorDescricao = editTextSetorDescricao.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val setorNivel = editTextSetorNivel.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

        if (!validarCampos(nome, cpf, nfc)) return

        val request = JsonObject().apply {
            addProperty("nome", nome)
            addProperty("nfc", nfc)
            addProperty("cpf", cpf)
            addProperty("unidade_id", unidade.publicId ?: unidade.id.toString())
            addProperty("ativo", true)
            setorDescricao?.let { addProperty("setor_descricao", it) }
            setorNivel?.let { addProperty("setor_nivel", it) }
        }

        definirCarregando(true)
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.criarFuncionario(token, request)
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@FuncionarioCadastroActivity,
                        "Funcionário cadastrado com sucesso.",
                        Toast.LENGTH_LONG
                    ).show()
                    limparFormulario()
                } else {
                    val mensagem = extrairMensagemErro(response.errorBody()?.string())
                        ?: "Erro ao cadastrar funcionário (${response.code()})."
                    Toast.makeText(this@FuncionarioCadastroActivity, mensagem, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@FuncionarioCadastroActivity,
                    "Erro de conexão: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                definirCarregando(false)
            }
        }
    }

    private fun validarCampos(nome: String, cpf: String, nfc: String): Boolean {
        return when {
            nome.isBlank() -> {
                editTextNomeFuncionario.error = "Informe o nome"
                false
            }
            cpf.isBlank() -> {
                editTextCpfFuncionario.error = "Informe o CPF"
                false
            }
            cpf.length != 11 -> {
                editTextCpfFuncionario.error = "CPF deve ter 11 dígitos"
                false
            }
            nfc.isBlank() -> {
                Toast.makeText(this, "Leia o crachá NFC antes de cadastrar.", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun definirCarregando(carregando: Boolean) {
        progressBarCadastroFuncionario.visibility = if (carregando) View.VISIBLE else View.GONE
        buttonCadastrarFuncionario.isEnabled = !carregando && unidadeSelecionada != null
        buttonCadastrarFuncionario.text = if (carregando) "Cadastrando..." else "Cadastrar funcionário"
    }

    private fun limparFormulario() {
        editTextNomeFuncionario.setText("")
        editTextCpfFuncionario.setText("")
        editTextNfcFuncionario.setText("")
        editTextSetorDescricao.setText("")
        editTextSetorNivel.setText("")
        atualizarStatusNfc()
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
            errorBody.takeIf { it.length <= 160 }
        }
    }

    private fun confirmarLogout() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja realmente sair?")
            .setPositiveButton("Sair") { _, _ ->
                sessionManager.clearSession()
                irParaLogin()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun irParaLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private data class UnidadeCadastro(
        val id: Int,
        val publicId: String? = null,
        val nome: String? = null,
        val empresa: String? = null
    ) {
        val nomeExibicao: String
            get() = listOfNotNull(nome, empresa)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" - ")
                ?: "Unidade $id"
    }
}
