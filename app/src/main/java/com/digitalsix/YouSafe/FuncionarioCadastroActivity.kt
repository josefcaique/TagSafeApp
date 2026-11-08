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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
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
import com.digitalsix.YouSafe.face.FaceCamera
import com.digitalsix.YouSafe.face.FaceRules
import com.digitalsix.YouSafe.registration.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.max

class FuncionarioCadastroActivity : AppCompatActivity() {

    private val cadastroFacial get() = checkFacial.isChecked
    private lateinit var checkNfc: android.widget.CheckBox
    private lateinit var checkFacial: android.widget.CheckBox
    private lateinit var cadastroStatus: TextView
    private lateinit var fotoStatus: TextView
    private lateinit var buttonFoto: MaterialButton
    private lateinit var draftStore: RegistrationDraftStore
    private var draft = RegistrationDraft()
    private var restaurando = true
    private var carregando = false
    private var mensagemErro: String? = null
    private var capturaPara: String? = null
    private val cadastroPendente get() = draft.employeePublicId != null || draft.creationUncertain
    private val capturaFacial = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(FaceEnrollmentActivity.EXTRA_PHOTO_NAME)
        val photo = RegistrationPhotos.find(this, name)
        val accepted = result.data?.getStringExtra(FaceEnrollmentActivity.EXTRA_CONSENT_AT)
        if (!::checkFacial.isInitialized) { photo?.delete(); return@registerForActivityResult }
        if (result.resultCode == RESULT_OK && photo != null && !accepted.isNullOrBlank() &&
            cadastroFacial && capturaPara == identidadeAtual()) {
            RegistrationPhotos.find(this, draft.photoName)?.takeIf { it != photo }?.delete()
            draft = draft.copy(photoName = name, consentAt = accepted)
            mensagemErro = null
            guardarRascunho()
        } else photo?.delete()
        capturaPara = null
        atualizarFormulario()
    }

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

        val owner = sessionManager.getUsuario()?.let { it.publicId ?: it.email ?: it.id.toString() }.orEmpty()
        draftStore = RegistrationDraftStore(this, owner)
        capturaPara = savedInstanceState?.getString("capture_identity")
        inicializarViews()
        configurarToolbar()
        configurarInsetsTeclado()
        configurarScrollParaFoco()
        configurarTela()
        configurarBotoes()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        restaurarRascunho()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (carregando) Toast.makeText(this@FuncionarioCadastroActivity, R.string.registration_wait, Toast.LENGTH_SHORT).show()
                else { guardarRascunho(); finish() }
            }
        })
    }

    private fun inicializarViews() {
        checkNfc = findViewById(R.id.checkAdicionarNfc)
        checkFacial = findViewById(R.id.checkCadastroFacial)
        cadastroStatus = findViewById(R.id.textViewCadastroStatus)
        fotoStatus = findViewById(R.id.textViewFotoCadastro)
        buttonFoto = findViewById(R.id.buttonFotoCadastro)
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
            unidades.getOrPut(id) {
                UnidadeCadastro(
                    id = id,
                    publicId = usuario.unidadePublicId?.takeIf { it.isNotBlank() }
                )
            }
        }

        usuario.unidadesIds.orEmpty()
            .filter { it > 0 }
            .forEachIndexed { index, id ->
                unidades.getOrPut(id) {
                    UnidadeCadastro(
                        id = id,
                        publicId = usuario.unidadesPublicIds.orEmpty().getOrNull(index)
                            ?.takeIf { it.isNotBlank() }
                    )
                }
            }

        return unidades.values.toList()
    }

    private fun selecionarUnidade(unidade: UnidadeCadastro) {
        if (!restaurando && unidadeSelecionada?.id != unidade.id) invalidarFoto()
        unidadeSelecionada = unidade
        textViewUnidadeCadastro.text = "Cadastro vinculado à ${unidade.nomeExibicao}."
        layoutUnidadeFuncionario.error = null
        if (!restaurando) { guardarRascunho(); atualizarFormulario() }
    }

    private fun configurarBotoes() {
        checkNfc.setOnCheckedChangeListener { _, checked ->
            if (restaurando) return@setOnCheckedChangeListener
            mensagemErro = null
            if (checked) ativarNFC() else { editTextNfcFuncionario.setText(""); desativarNFC() }
            guardarRascunho(); atualizarFormulario()
        }
        checkFacial.setOnCheckedChangeListener { _, checked ->
            if (restaurando) return@setOnCheckedChangeListener
            mensagemErro = null
            if (!checked) invalidarFoto()
            guardarRascunho(); atualizarFormulario()
            if (checked && !fotoPronta()) abrirCapturaFacial()
        }
        buttonFoto.setOnClickListener { abrirCapturaFacial() }
        buttonLimparNfc.setOnClickListener { editTextNfcFuncionario.setText("") }
        buttonCadastrarFuncionario.setOnClickListener { cadastrarFuncionario() }
        listOf(editTextNomeFuncionario, editTextCpfFuncionario).forEach { field ->
            field.doAfterTextChanged {
                if (!restaurando && !carregando && !cadastroPendente) {
                    invalidarFoto(); mensagemErro = null; guardarRascunho(); atualizarFormulario()
                }
            }
        }
        listOf(editTextNfcFuncionario, editTextSetorDescricao, editTextSetorNivel).forEach { field ->
            field.doAfterTextChanged {
                if (!restaurando && !carregando && !cadastroPendente) {
                    mensagemErro = null; guardarRascunho(); atualizarFormulario()
                }
            }
        }
    }

    private fun identidadeAtual() = listOf(
        editTextNomeFuncionario.text?.toString()?.trim(),
        editTextCpfFuncionario.text?.toString()?.filter { it.isDigit() }, unidadeSelecionada?.id
    ).joinToString("|")

    private fun abrirCapturaFacial() {
        if (carregando || !cadastroFacial) return
        capturaPara = identidadeAtual()
        guardarRascunho()
        capturaFacial.launch(Intent(this, FaceEnrollmentActivity::class.java)
            .putExtra("funcionario_nome", editTextNomeFuncionario.text?.toString()?.trim()))
    }

    private fun fotoPronta() = draft.consentAt?.isNotBlank() == true &&
        RegistrationPhotos.find(this, draft.photoName) != null

    private fun invalidarFoto() {
        RegistrationPhotos.find(this, draft.photoName)?.delete()
        draft = draft.copy(photoName = null, consentAt = null)
    }

    private fun guardarRascunho(): Boolean {
        if (restaurando || !::draftStore.isInitialized) return true
        draft = draft.copy(
            name = editTextNomeFuncionario.text?.toString().orEmpty(),
            cpf = editTextCpfFuncionario.text?.toString().orEmpty(),
            nfc = editTextNfcFuncionario.text?.toString().orEmpty(),
            sector = editTextSetorDescricao.text?.toString().orEmpty(),
            level = editTextSetorNivel.text?.toString().orEmpty(), unitId = unidadeSelecionada?.id,
            useNfc = checkNfc.isChecked, useFace = cadastroFacial
        )
        return runCatching { draftStore.save(draft); true }.getOrElse {
            mensagemErro = getString(R.string.registration_storage_failed); false
        }
    }

    private fun restaurarRascunho() {
        draftStore.load()?.let { saved ->
            draft = saved
            editTextNomeFuncionario.setText(saved.name); editTextCpfFuncionario.setText(saved.cpf)
            editTextNfcFuncionario.setText(saved.nfc); editTextSetorDescricao.setText(saved.sector)
            editTextSetorNivel.setText(saved.level)
            checkNfc.isChecked = saved.useNfc; checkFacial.isChecked = saved.useFace
            val unit = unidadesCadastro.find { it.id == saved.unitId }
            unidadeSelecionada = unit
            if (unit != null) selecionarUnidade(unit)
            spinnerUnidadeFuncionario.setText(unit?.nomeExibicao.orEmpty(), false)
        }
        restaurando = false
        atualizarFormulario()
    }

    private fun atualizarFormulario() {
        if (!::buttonCadastrarFuncionario.isInitialized) return
        val locked = carregando || cadastroPendente
        checkNfc.isEnabled = !locked; checkFacial.isEnabled = !locked
        spinnerUnidadeFuncionario.isEnabled = !locked && unidadesCadastro.isNotEmpty()
        listOf(editTextNomeFuncionario, editTextCpfFuncionario, editTextNfcFuncionario,
            editTextSetorDescricao, editTextSetorNivel).forEach { it.isEnabled = !locked }
        buttonLimparNfc.isEnabled = !locked
        buttonFoto.isEnabled = !carregando && !draft.creationUncertain
        findViewById<View>(R.id.cardNfcCadastro).visibility = if (checkNfc.isChecked) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutFotoCadastro).visibility = if (cadastroFacial) View.VISIBLE else View.GONE
        fotoStatus.setText(if (fotoPronta()) R.string.registration_photo_saved else R.string.registration_photo_required)
        buttonFoto.setText(if (fotoPronta()) R.string.registration_retake_face else R.string.registration_capture_face)
        val ready = RegistrationRules.ready(checkNfc.isChecked, editTextNfcFuncionario.text?.toString(), cadastroFacial, fotoPronta())
        buttonCadastrarFuncionario.isEnabled = !carregando && unidadeSelecionada != null && (draft.creationUncertain || ready)
        buttonCadastrarFuncionario.setText(when {
            carregando -> R.string.registration_wait
            draft.creationUncertain -> R.string.registration_check
            draft.employeePublicId != null -> R.string.registration_finish_face
            else -> R.string.registration_submit
        })
        cadastroStatus.text = mensagemErro ?: getString(when {
            carregando -> R.string.registration_wait
            draft.creationUncertain -> R.string.registration_uncertain
            draft.employeePublicId != null -> R.string.registration_pending_face
            !checkNfc.isChecked && !cadastroFacial -> R.string.registration_choose_method
            checkNfc.isChecked && editTextNfcFuncionario.text.isNullOrBlank() -> R.string.registration_nfc_required
            cadastroFacial && !fotoPronta() -> R.string.registration_photo_required
            else -> R.string.registration_ready
        })
        atualizarStatusNfc()
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
        editTextNfcFuncionario.onFocusChangeListener = focusListener
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
            tag?.takeIf { checkNfc.isChecked && !carregando && !cadastroPendente }?.let {
                val nfcId = it.id.joinToString("") { byte -> "%02X".format(byte) }
                editTextNfcFuncionario.setText(nfcId)
                textViewNfcStatus.text = "Crachá lido: $nfcId"
                Toast.makeText(this, "Crachá lido com sucesso.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ativarNFC() {
        if (!::checkNfc.isInitialized || !checkNfc.isChecked || carregando || cadastroPendente ||
            !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
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
        if (!::textViewNfcStatus.isInitialized) return
        val nfc = editTextNfcFuncionario.text?.toString().orEmpty()
        textViewNfcStatus.text = when {
            nfc.isNotBlank() -> "NFC informado: $nfc"
            nfcAdapter == null -> "Informe o código do crachá no campo abaixo."
            nfcAdapter?.isEnabled == false -> "Informe o código ou ative o NFC para ler o crachá."
            else -> "Informe o código ou aproxime o crachá do celular."
        }
    }

    private fun cadastrarFuncionario() {
        if (carregando) return
        if (draft.creationUncertain) { verificarCadastroInterrompido(); return }
        val token = sessionManager.getTokenWithBearer() ?: return
        val unidade = unidadeSelecionada ?: return
        val nome = editTextNomeFuncionario.text?.toString()?.trim().orEmpty()
        val cpf = editTextCpfFuncionario.text?.toString()?.filter { it.isDigit() }.orEmpty()
        if (!validarCampos(nome, cpf)) return
        if (!RegistrationRules.ready(checkNfc.isChecked, editTextNfcFuncionario.text?.toString(), cadastroFacial, fotoPronta())) {
            mensagemErro = getString(R.string.registration_complete_methods); atualizarFormulario(); return
        }
        if (!FaceRules.isPublicId(unidade.publicId)) {
            layoutUnidadeFuncionario.error = "Unidade sem UUID público. Atualize os dados antes de cadastrar."; return
        }
        if (!guardarRascunho()) { atualizarFormulario(); return }
        val request = JsonObject().apply {
            addProperty("nome", nome); addProperty("cpf", cpf)
            addProperty("nfc", if (checkNfc.isChecked) editTextNfcFuncionario.text?.toString()?.trim() else null)
            addProperty("unidade_id", unidade.publicId); addProperty("ativo", true)
            editTextSetorDescricao.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("setor_descricao", it) }
            editTextSetorNivel.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("setor_nivel", it) }
        }
        val includeFace = cadastroFacial
        val photo = RegistrationPhotos.find(this, draft.photoName)
        mensagemErro = null; definirCarregando(true)
        lifecycleScope.launch {
            try {
                // Validate the saved photo before creating any employee on the API.
                val part = if (includeFace) withContext(Dispatchers.IO) { FaceCamera.multipart(requireNotNull(photo)) } else null
                val enrollment = FuncionarioRegistration(RetrofitInstance.api, RetrofitInstance.faceApi).submit(
                    token, request, includeFace, part, draft.consentAt, draft.employeePublicId,
                    beforeCreate = {
                        draft = draft.copy(creationUncertain = true)
                        check(guardarRascunho()) { getString(R.string.registration_storage_failed) }
                    },
                    createRejected = { draft = draft.copy(creationUncertain = false); guardarRascunho() },
                    employeeCreated = { id ->
                        draft = draft.copy(employeePublicId = id, creationUncertain = false)
                        check(guardarRascunho()) { getString(R.string.registration_storage_failed) }
                    }
                )
                enrollment?.let { id ->
                    getSharedPreferences("face_enrollment_records", MODE_PRIVATE).edit()
                        .putString(id, draft.employeePublicId).apply()
                }
                limparFormulario()
                Toast.makeText(this@FuncionarioCadastroActivity, R.string.registration_success, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mensagemErro = when {
                    e is RegistrationFailure || e is IllegalArgumentException -> e.message
                    draft.creationUncertain -> getString(R.string.registration_uncertain)
                    draft.employeePublicId != null -> getString(R.string.registration_pending_face)
                    else -> "Não foi possível concluir o cadastro. Verifique a conexão."
                }
            } finally { definirCarregando(false) }
        }
    }

    private fun verificarCadastroInterrompido() {
        val token = sessionManager.getTokenWithBearer() ?: return
        val unit = unidadeSelecionada?.publicId?.takeIf(FaceRules::isPublicId) ?: return
        val cpf = editTextCpfFuncionario.text?.toString()?.filter { it.isDigit() }.orEmpty()
        mensagemErro = null; definirCarregando(true)
        lifecycleScope.launch {
            var continuar = false
            try {
                val response = RetrofitInstance.api.buscarFuncionariosCadastro(token, unit)
                check(response.isSuccessful && response.body() != null)
                val matches = response.body().orEmpty().filter { it.cpf?.filter(Char::isDigit) == cpf }
                when {
                    matches.size == 1 && RegistrationRules.matchesRecovery(matches[0], draft.name, cpf,
                        if (checkNfc.isChecked) draft.nfc else null) -> {
                        draft = draft.copy(employeePublicId = matches[0].publicId, creationUncertain = false)
                        if (cadastroFacial) { check(guardarRascunho()); continuar = true }
                        else { limparFormulario(); Toast.makeText(this@FuncionarioCadastroActivity, R.string.registration_success, Toast.LENGTH_LONG).show() }
                    }
                    matches.isEmpty() -> {
                        draft = draft.copy(creationUncertain = false); guardarRascunho()
                        mensagemErro = getString(R.string.registration_not_found)
                    }
                    else -> mensagemErro = getString(R.string.registration_check_failed)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mensagemErro = getString(R.string.registration_check_failed)
            } finally { definirCarregando(false) }
            if (continuar) cadastrarFuncionario()
        }
    }

    private fun validarCampos(nome: String, cpf: String): Boolean {
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
            else -> true
        }
    }

    private fun definirCarregando(value: Boolean) {
        carregando = value
        progressBarCadastroFuncionario.visibility = if (value) View.VISIBLE else View.GONE
        if (value) desativarNFC() else ativarNFC()
        atualizarFormulario()
    }

    private fun limparFormulario() {
        restaurando = true
        invalidarFoto(); draftStore.clear(); draft = RegistrationDraft()
        editTextNomeFuncionario.setText(""); editTextCpfFuncionario.setText("")
        editTextNfcFuncionario.setText(""); editTextSetorDescricao.setText(""); editTextSetorNivel.setText("")
        checkNfc.isChecked = false; checkFacial.isChecked = false
        mensagemErro = null; restaurando = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::draftStore.isInitialized) guardarRascunho()
        outState.putString("capture_identity", capturaPara)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        if (!::draftStore.isInitialized) { super.onRestoreInstanceState(savedInstanceState); return }
        restaurando = true
        super.onRestoreInstanceState(savedInstanceState)
        restaurando = false
        atualizarFormulario()
    }

    private fun confirmarLogout() {
        if (carregando) return
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja realmente sair?")
            .setPositiveButton("Sair") { _, _ ->
                if (!cadastroPendente) { invalidarFoto(); draftStore.clear() }
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
