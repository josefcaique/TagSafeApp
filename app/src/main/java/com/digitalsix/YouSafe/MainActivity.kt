package com.digitalsix.YouSafe

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import kotlin.math.max
import com.digitalsix.YouSafe.network.ParticipanteSessao
import com.digitalsix.YouSafe.network.RetrofitInstance
import com.digitalsix.YouSafe.network.modulos.moduloResponse
import com.digitalsix.YouSafe.repository.MainRepository
import com.digitalsix.YouSafe.utils.AtividadeEmProgresso
import com.digitalsix.YouSafe.utils.SessionManager
import com.digitalsix.YouSafe.viewmodel.AcaoState
import com.digitalsix.YouSafe.viewmodel.MainViewModel
import com.digitalsix.YouSafe.viewmodel.MainViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var sessionManager: SessionManager
    private var nfcAdapter: NfcAdapter? = null

    private lateinit var mainScrollView: ScrollView
    private lateinit var statusBarScrim: View

    // Views — Estado 0: Sessões abertas
    private lateinit var layoutSessoes: MaterialCardView
    private lateinit var textViewResumoSessoes: TextView
    private lateinit var layoutListaSessoes: LinearLayout
    private lateinit var buttonNovaAtividade: MaterialButton
    private lateinit var buttonVoltarSessoesCriacao: MaterialButton

    // Views — Estado 1: Criação de Aula
    private lateinit var toolbar: MaterialToolbar
    private lateinit var textViewBemVindo: TextView
    private lateinit var layoutCriacaoAula: MaterialCardView
    private lateinit var spinnerEmpresa: AutoCompleteTextView
    private lateinit var spinnerUnidade: AutoCompleteTextView
    private lateinit var layoutSpinnerUnidade: TextInputLayout
    private lateinit var spinnerModule: AutoCompleteTextView
    private lateinit var layoutSpinnerModule: TextInputLayout
    private lateinit var layoutTreinamentoFields: LinearLayout
    private lateinit var editTextNomeTreinamento: AutoCompleteTextView
    private lateinit var textViewDescricaoLabel: TextView
    private lateinit var layoutDescricaoAula: TextInputLayout
    private lateinit var editTextDescricaoAula: TextInputEditText
    private lateinit var buttonIniciarAula: MaterialButton

    // Views — Estado 2: Leitura NFC
    private lateinit var layoutLeituraNFC: MaterialCardView
    private lateinit var textViewDescricaoAtual: TextView
    private lateinit var textViewUnidadeAtual: TextView
    private lateinit var textViewListaNFCs: TextView
    private lateinit var textViewContador: TextView
    private lateinit var buttonConfirmarAula: MaterialButton
    private lateinit var buttonAbortarAula: MaterialButton
    private lateinit var buttonVoltarSessoesNFC: MaterialButton
    private lateinit var textViewDuracaoAula: TextView

    // Estado interno
    private var empresaSelecionada: EmpresaComUnidades? = null
    private var unidadeUuidSelecionada: String? = null
    private var moduloSelecionado: moduloResponse? = null
    private var tipoModuloAtual: TipoModulo = TipoModulo.DESCONHECIDO
    private var sessaoUuid: String? = null
    private val participantes = mutableListOf<ParticipanteSessao>()
    private val nfcsRegistrados = mutableListOf<String>()
    private var aulaInicioMs: Long = 0L
    private var empresasPopuladas = false
    private var nfcPendente: String? = null
    private val filaNfcsPendentes = ArrayDeque<String>()
    private var exibirLogoutNoMenu: Boolean = true

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        RetrofitInstance.initialize(applicationContext)
        configurarBarraStatus()

        sessionManager = SessionManager(this)
        val repository = MainRepository(RetrofitInstance.api)
        val factory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        inicializarViews()
        configurarTeclado()
        configurarToolbar()
        configurarDropdowns()
        configurarBotoes()
        observarViewModel()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val usuario = sessionManager.getUsuario()
        val token = sessionManager.getTokenWithBearer()
        if (usuario != null && token != null) {
            textViewBemVindo.text = "Olá, ${usuario.nome ?: "usuário"}!"
            val instrutorUuid = usuario.instrutorUuid ?: run {
                Toast.makeText(this, "Usuário não possui perfil de instrutor.", Toast.LENGTH_SHORT).show()
                encerrarSessaoEIrParaLogin()
                return
            }
            viewModel.carregarEmpresasEUnidades(
                token = token,
                instrutorId = instrutorUuid,
                unidadesDoLogin = usuario.unidadesAtendidas.orEmpty()
            )
        } else {
            encerrarSessaoEIrParaLogin()
            return
        }

        mostrarTelaSessoes()
    }

    // ==========================================
    // INICIALIZAÇÃO
    // ==========================================

    private fun inicializarViews() {
        mainScrollView = findViewById(R.id.mainScrollView)
        statusBarScrim = findViewById(R.id.statusBarScrim)
        toolbar = findViewById(R.id.toolbar)
        textViewBemVindo = findViewById(R.id.textViewBemVindo)
        layoutSessoes = findViewById(R.id.layoutSessoes)
        textViewResumoSessoes = findViewById(R.id.textViewResumoSessoes)
        layoutListaSessoes = findViewById(R.id.layoutListaSessoes)
        buttonNovaAtividade = findViewById(R.id.buttonNovaAtividade)
        buttonVoltarSessoesCriacao = findViewById(R.id.buttonVoltarSessoesCriacao)
        layoutCriacaoAula = findViewById(R.id.layoutCriacaoAula)
        spinnerEmpresa = findViewById(R.id.spinnerEmpresa)
        spinnerUnidade = findViewById(R.id.spinnerUnidade)
        layoutSpinnerUnidade = findViewById(R.id.layoutSpinnerUnidade)
        spinnerModule = findViewById(R.id.spinnerModule)
        layoutSpinnerModule = findViewById(R.id.layoutSpinnerModule)
        layoutTreinamentoFields = findViewById(R.id.layoutTreinamentoFields)
        editTextNomeTreinamento = findViewById(R.id.editTextNomeTreinamento)
        textViewDescricaoLabel = findViewById(R.id.textViewDescricaoLabel)
        layoutDescricaoAula = findViewById(R.id.layoutDescricaoAula)
        editTextDescricaoAula = findViewById(R.id.editTextDescricaoAula)
        buttonIniciarAula = findViewById(R.id.buttonIniciarAula)
        layoutLeituraNFC = findViewById(R.id.layoutLeituraNFC)
        textViewDescricaoAtual = findViewById(R.id.textViewDescricaoAtual)
        textViewUnidadeAtual = findViewById(R.id.textViewUnidadeAtual)
        textViewListaNFCs = findViewById(R.id.textViewListaNFCs)
        textViewContador = findViewById(R.id.textViewContador)
        buttonConfirmarAula = findViewById(R.id.buttonConfirmarAula)
        buttonAbortarAula = findViewById(R.id.buttonAbortarAula)
        buttonVoltarSessoesNFC = findViewById(R.id.buttonVoltarSessoesNFC)
        textViewDuracaoAula = findViewById(R.id.textViewDuracaoAula)
    }

    private fun configurarToolbar() {
        setSupportActionBar(toolbar)
    }

    private fun configurarBarraStatus() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun configurarTeclado() {
        ViewCompat.setOnApplyWindowInsetsListener(mainScrollView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarScrim.layoutParams = statusBarScrim.layoutParams.apply {
                height = systemBarsInsets.top
            }
            val extraTop = (8 * resources.displayMetrics.density).toInt()
            val topPadding = systemBarsInsets.top + extraTop
            val bottomPadding = max(imeInsets.bottom, systemBarsInsets.bottom)
            view.updatePadding(top = topPadding, bottom = bottomPadding)
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                currentFocus?.let { focused ->
                    view.post { garantirVisibilidadeDoFoco(focused) }
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(mainScrollView)

        val scrollParaFoco = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.post { garantirVisibilidadeDoFoco(view) }
            }
        }
        editTextNomeTreinamento.onFocusChangeListener = scrollParaFoco
        editTextDescricaoAula.onFocusChangeListener = scrollParaFoco
    }

    private fun garantirVisibilidadeDoFoco(target: View) {
        if (!isDescendenteDe(target, mainScrollView)) return

        val rect = Rect()
        target.getDrawingRect(rect)
        mainScrollView.offsetDescendantRectToMyCoords(target, rect)

        val extra = (24 * resources.displayMetrics.density).toInt()
        val visibleTop = mainScrollView.scrollY + mainScrollView.paddingTop
        val visibleBottom = mainScrollView.scrollY + mainScrollView.height - mainScrollView.paddingBottom

        when {
            rect.bottom + extra > visibleBottom ->
                mainScrollView.smoothScrollBy(0, rect.bottom + extra - visibleBottom)
            rect.top - extra < visibleTop ->
                mainScrollView.smoothScrollBy(0, rect.top - extra - visibleTop)
        }
    }

    private fun isDescendenteDe(child: View, parent: View): Boolean {
        var current: View? = child
        while (current != null && current != parent) {
            current = current.parent as? View
        }
        return current == parent
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_logout)?.isVisible = exibirLogoutNoMenu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                confirmarLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
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

    // ==========================================
    // DROPDOWNS
    // ==========================================

    private fun configurarDropdowns() {
        spinnerEmpresa.setOnClickListener { spinnerEmpresa.showDropDown() }
        spinnerEmpresa.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> if (hasFocus) spinnerEmpresa.showDropDown() }

        layoutSpinnerUnidade.setOnClickListener { spinnerUnidade.showDropDown() }
        spinnerUnidade.setOnClickListener { spinnerUnidade.showDropDown() }
        spinnerUnidade.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> if (hasFocus) spinnerUnidade.showDropDown() }

        layoutSpinnerModule.setOnClickListener { spinnerModule.showDropDown() }
        spinnerModule.setOnClickListener { spinnerModule.showDropDown() }
        spinnerModule.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> if (hasFocus) spinnerModule.showDropDown() }

        spinnerEmpresa.setOnItemClickListener { parent, _, position, _ ->
            val empresasMap = viewModel.uiState.value?.empresasMap ?: return@setOnItemClickListener
            val empresaNomeSelecionada = parent.getItemAtPosition(position)?.toString()
            val empresa = empresaNomeSelecionada?.let { empresasMap[it] }
                ?: empresasMap.values.elementAtOrNull(position)
                ?: return@setOnItemClickListener

            empresaSelecionada = empresa
            resetarSelecaoUnidade()
            resetarSelecaoModulo()
            popularUnidades(empresa)
        }

        spinnerUnidade.setOnItemClickListener { _, _, position, _ ->
            val unidades = empresaSelecionada?.unidades ?: return@setOnItemClickListener
            if (position < unidades.size) {
                val unidade = unidades[position]
                unidadeUuidSelecionada = unidade.publicId
                resetarSelecaoModulo()
                carregarModulos(unidade.publicId)
            }
        }

        spinnerModule.setOnItemClickListener { _, _, position, _ ->
            val modulos = viewModel.uiState.value?.modulos ?: return@setOnItemClickListener
            if (position < modulos.size) {
                moduloSelecionado = modulos[position]
                tipoModuloAtual = detectarTipoModulo(modulos[position])
                atualizarCamposPorTipoModulo()
            }
        }
    }

    private fun popularUnidades(empresa: EmpresaComUnidades) {
        val nomes = empresa.unidades.map { it.unidadeNome }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nomes)
        spinnerUnidade.setAdapter(adapter)
        layoutSpinnerUnidade.isEnabled = true
        spinnerUnidade.isEnabled = true
        spinnerUnidade.setText("", false)
    }

    private fun carregarModulos(unidadeUuid: String) {
        val token = sessionManager.getTokenWithBearer() ?: return
        viewModel.carregarTreinamentos(token, unidadeUuid)

        val unidade = empresaSelecionada?.unidades?.find { it.publicId == unidadeUuid }
        unidade?.let {
            viewModel.definirModulos(it.modulos)
            layoutSpinnerModule.isEnabled = true
            spinnerModule.isEnabled = true
        }
    }

    private fun resetarSelecaoUnidade() {
        unidadeUuidSelecionada = null
        spinnerUnidade.setText("", false)
        spinnerUnidade.setAdapter(null)
        layoutSpinnerUnidade.isEnabled = false
        spinnerUnidade.isEnabled = false
    }

    private fun resetarSelecaoModulo() {
        moduloSelecionado = null
        tipoModuloAtual = TipoModulo.DESCONHECIDO
        spinnerModule.setText("", false)
        spinnerModule.setAdapter(null)
        editTextNomeTreinamento.setText("", false)
        editTextDescricaoAula.setText("")
        layoutSpinnerModule.isEnabled = false
        spinnerModule.isEnabled = false
        layoutTreinamentoFields.visibility = View.GONE
        textViewDescricaoLabel.visibility = View.GONE
        layoutDescricaoAula.visibility = View.GONE
        viewModel.limparModulos()
        viewModel.limparTreinamentos()
    }

    private fun detectarTipoModulo(modulo: moduloResponse): TipoModulo {
        val abrev = modulo.abreviacao.orEmpty().uppercase()
        val nome = modulo.nome.orEmpty().uppercase()
        return when {
            abrev.contains("GL") || nome.contains("GINASTICA") || nome.contains("LABORAL") -> TipoModulo.GINASTICA_LABORAL
            abrev.contains("TR") || nome.contains("TREINAMENTO") -> TipoModulo.TREINAMENTO
            else -> TipoModulo.DESCONHECIDO
        }
    }

    private fun atualizarCamposPorTipoModulo() {
        when (tipoModuloAtual) {
            TipoModulo.GINASTICA_LABORAL -> {
                layoutTreinamentoFields.visibility = View.GONE
                textViewDescricaoLabel.visibility = View.VISIBLE
                layoutDescricaoAula.visibility = View.VISIBLE
            }
            TipoModulo.TREINAMENTO -> {
                layoutTreinamentoFields.visibility = View.VISIBLE
                textViewDescricaoLabel.visibility = View.GONE
                layoutDescricaoAula.visibility = View.GONE
                val token = sessionManager.getTokenWithBearer() ?: return
                val unidadeUuid = unidadeUuidSelecionada ?: return
                viewModel.carregarTreinamentosSeNecessario(token, unidadeUuid)
            }
            TipoModulo.DESCONHECIDO -> {
                layoutTreinamentoFields.visibility = View.GONE
                textViewDescricaoLabel.visibility = View.GONE
                layoutDescricaoAula.visibility = View.GONE
            }
        }
    }

    // ==========================================
    // OBSERVERS
    // ==========================================

    private fun observarViewModel() {
        viewModel.uiState.observe(this) { state ->
            if (state.empresasMap.isNotEmpty() && !empresasPopuladas) {
                val nomes = state.empresasMap.keys.toList()
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nomes)
                spinnerEmpresa.setAdapter(adapter)
                empresasPopuladas = true
            }

            if (state.modulos.isNotEmpty()) {
                val nomes = state.modulos.map { it.nome.orEmpty() }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nomes)
                spinnerModule.setAdapter(adapter)
            }

            if (state.treinamentos.isNotEmpty()) {
                val nomes = state.treinamentos.map { it.nome }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nomes)
                editTextNomeTreinamento.setAdapter(adapter)
            }

            state.mensagemErro?.let { erro ->
                Toast.makeText(this, erro, Toast.LENGTH_LONG).show()
                viewModel.limparErro()
            }
        }

        viewModel.criarSessaoState.observe(this) { state ->
            when (state) {
                is AcaoState.Loading -> {
                    buttonIniciarAula.isEnabled = false
                    buttonIniciarAula.text = "Iniciando..."
                }
                is AcaoState.Sucesso -> {
                    val uuid = state.sessaoId ?: return@observe
                    sessaoUuid = uuid
                    aulaInicioMs = System.currentTimeMillis()
                    sessionManager.salvarAulaEmProgresso(uuid)
                    sessionManager.salvarInicioAulaEmProgresso(aulaInicioMs)
                    unidadeUuidSelecionada?.let { sessionManager.salvarUnidadeEmProgresso(it) }
                    sessionManager.salvarModuloEmProgresso(moduloSelecionado?.nome)
                    sessionManager.salvarAtividadeEmProgresso(
                        AtividadeEmProgresso(
                            uuid = uuid,
                            inicioMs = aulaInicioMs,
                            unidadeUuid = unidadeUuidSelecionada,
                            unidadeNome = obterNomeUnidadeAtual(),
                            moduloNome = moduloSelecionado?.nome
                        )
                    )
                    mostrarEstadoNFC(limparParticipantes = true)
                    viewModel.consumirCriarSessaoEstado()
                }
                is AcaoState.Erro -> {
                    buttonIniciarAula.isEnabled = true
                    buttonIniciarAula.text = "Iniciar atividade"
                    Toast.makeText(this, state.mensagem, Toast.LENGTH_LONG).show()
                    viewModel.consumirCriarSessaoEstado()
                }
                is AcaoState.Idle -> {
                    buttonIniciarAula.isEnabled = true
                    buttonIniciarAula.text = "Iniciar atividade"
                }
            }
        }

        viewModel.confirmarSessaoState.observe(this) { state ->
            when (state) {
                is AcaoState.Sucesso -> {
                    val uuidFinalizada = sessaoUuid
                    limparAulaEmProgresso(uuidFinalizada)
                    Toast.makeText(this, "Atividade confirmada com sucesso!", Toast.LENGTH_SHORT).show()
                    mostrarTelaSessoes()
                    viewModel.consumirConfirmarSessaoEstado()
                }
                is AcaoState.Erro -> {
                    Toast.makeText(this, state.mensagem, Toast.LENGTH_LONG).show()
                    viewModel.consumirConfirmarSessaoEstado()
                }
                else -> {}
            }
        }

        viewModel.abortarSessaoState.observe(this) { state ->
            when (state) {
                is AcaoState.Sucesso -> {
                    val uuidAbortada = sessaoUuid
                    limparAulaEmProgresso(uuidAbortada)
                    Toast.makeText(this, "Atividade abortada.", Toast.LENGTH_SHORT).show()
                    mostrarTelaSessoes()
                    viewModel.consumirAbortarSessaoEstado()
                }
                is AcaoState.Erro -> {
                    Toast.makeText(this, state.mensagem, Toast.LENGTH_LONG).show()
                    viewModel.consumirAbortarSessaoEstado()
                }
                else -> {}
            }
        }

        viewModel.validarNFCState.observe(this) { state ->
            when (state) {
                is AcaoState.Sucesso -> {
                    val nfc = nfcPendente ?: return@observe
                    val funcionario = viewModel.funcionarioValidado.value
                    val unidadeUuid = unidadeUuidSelecionada ?: return@observe
                    val participante = ParticipanteSessao(
                        nfc = nfc,
                        unidadeId = unidadeUuid,
                        funcionarioId = funcionario?.publicId,
                        nome = funcionario?.nome,
                        horarioRegistro = obterTimestampUtc()
                    )
                    participantes.add(participante)
                    nfcsRegistrados.add(nfc)
                    sessaoUuid?.let { sessionManager.atualizarParticipantesAtividade(it, participantes.toList()) }
                    nfcPendente = null
                    atualizarListaNFCs()
                    viewModel.consumirValidarNFCEstado()
                    validarProximoNfcDaFila()
                }
                is AcaoState.Erro -> {
                    nfcPendente = null
                    Toast.makeText(this, "Crachá não reconhecido: ${state.mensagem}", Toast.LENGTH_LONG).show()
                    viewModel.consumirValidarNFCEstado()
                    validarProximoNfcDaFila()
                }
                else -> {}
            }
        }
    }

    // ==========================================
    // BOTÕES
    // ==========================================

    private fun configurarBotoes() {
        buttonNovaAtividade.setOnClickListener { mostrarEstadoCriacao() }
        buttonVoltarSessoesCriacao.setOnClickListener { mostrarTelaSessoes() }
        buttonVoltarSessoesNFC.setOnClickListener { mostrarTelaSessoes() }
        buttonIniciarAula.setOnClickListener { iniciarAula() }
        buttonConfirmarAula.setOnClickListener { confirmarAula() }
        buttonAbortarAula.setOnClickListener { abortarAula() }
    }

    private fun iniciarAula() {
        val token = sessionManager.getTokenWithBearer() ?: run {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show()
            irParaLogin()
            return
        }
        val usuario = sessionManager.getUsuario() ?: return
        val instrutorUuid = usuario.instrutorUuid ?: run {
            Toast.makeText(this, "Usuário não possui perfil de instrutor.", Toast.LENGTH_SHORT).show()
            encerrarSessaoEIrParaLogin()
            return
        }
        val unidadeUuid = unidadeUuidSelecionada ?: run {
            Toast.makeText(this, "Selecione uma unidade.", Toast.LENGTH_SHORT).show()
            return
        }
        val modulo = moduloSelecionado ?: run {
            Toast.makeText(this, "Selecione um módulo.", Toast.LENGTH_SHORT).show()
            return
        }

        val descricao: String
        val nomeTreinamento: String
        val treinamentoUuid: String?

        when (tipoModuloAtual) {
            TipoModulo.GINASTICA_LABORAL -> {
                descricao = editTextDescricaoAula.text?.toString()?.trim() ?: ""
                if (descricao.isBlank()) {
                    Toast.makeText(this, "Digite uma descrição para a aula.", Toast.LENGTH_SHORT).show()
                    return
                }
                nomeTreinamento = ""
                treinamentoUuid = null
            }
            TipoModulo.TREINAMENTO -> {
                val nomeDigitado = editTextNomeTreinamento.text?.toString()?.trim() ?: ""
                if (nomeDigitado.isBlank()) {
                    Toast.makeText(this, "Digite ou selecione o nome do treinamento.", Toast.LENGTH_SHORT).show()
                    return
                }
                val existente = viewModel.uiState.value?.treinamentos
                    ?.find { it.nome.equals(nomeDigitado, ignoreCase = true) }
                treinamentoUuid = existente?.publicId
                nomeTreinamento = nomeDigitado
                descricao = nomeDigitado
            }
            TipoModulo.DESCONHECIDO -> {
                Toast.makeText(this, "Tipo de módulo não reconhecido.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        viewModel.criarSessao(
            token = token,
            tipoModulo = tipoModuloAtual,
            nomeTreinamento = nomeTreinamento,
            descricao = descricao,
            unidadeId = unidadeUuid,
            instrutorId = instrutorUuid,
            moduloId = modulo.publicId ?: run {
                Toast.makeText(this, "Módulo sem UUID.", Toast.LENGTH_SHORT).show()
                return
            },
            treinamentoSelecionadoId = treinamentoUuid
        )
    }

    private fun confirmarAula() {
        val token = sessionManager.getTokenWithBearer() ?: return
        val uuid = sessaoUuid ?: return
        viewModel.confirmarSessao(token, uuid, obterTimestampUtc(), participantes.toList())
    }

    private fun abortarAula() {
        AlertDialog.Builder(this)
            .setTitle("Abortar atividade")
            .setMessage("Deseja abortar a atividade? Os dados registrados serão descartados.")
            .setPositiveButton("Abortar") { _, _ ->
                val token = sessionManager.getTokenWithBearer() ?: return@setPositiveButton
                val uuid = sessaoUuid ?: return@setPositiveButton
                viewModel.abortarSessao(token, uuid, obterTimestampUtc(), participantes.toList())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ==========================================
    // ESTADOS DE TELA
    // ==========================================

    private fun mostrarTelaSessoes() {
        salvarAtividadeAtual()
        layoutLeituraNFC.visibility = View.GONE
        layoutCriacaoAula.visibility = View.GONE
        layoutSessoes.visibility = View.VISIBLE
        atualizarVisibilidadeLogout(true)
        desativarNFC()
        pararTimer()
        atualizarListaSessoes()
    }

    private fun atualizarListaSessoes() {
        val atividades = sessionManager.getAtividadesEmProgresso()
        layoutListaSessoes.removeAllViews()

        textViewResumoSessoes.text = if (atividades.isEmpty()) {
            "Nenhuma sessão aberta no momento."
        } else {
            "${atividades.size} sessão(ões) em andamento."
        }

        atividades.forEach { atividade ->
            layoutListaSessoes.addView(criarCardSessao(atividade))
        }
    }

    private fun criarCardSessao(atividade: AtividadeEmProgresso): View {
        val density = resources.displayMetrics.density
        val card = MaterialCardView(this).apply {
            radius = 16f * density
            cardElevation = 0f
            setCardBackgroundColor(getColor(R.color.surface_variant))
            strokeWidth = (1 * density).toInt()
            strokeColor = getColor(R.color.divider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )
        }

        val titulo = TextView(this).apply {
            text = atividade.moduloNome?.takeIf { it.isNotBlank() } ?: "Atividade em andamento"
            setTextColor(getColor(R.color.text_primary))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val unidade = TextView(this).apply {
            text = "Unidade: ${atividade.unidadeNome?.takeIf { it.isNotBlank() } ?: "não informada"}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val inicio = TextView(this).apply {
            text = "Início: ${formatarDataLocal(atividade.inicioMs)}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val participantes = TextView(this).apply {
            text = "Participantes registrados: ${atividade.participantes.size}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val continuar = MaterialButton(this).apply {
            text = "Continuar"
            setTextColor(getColor(R.color.white))
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.turquoise))
            cornerRadius = (12 * density).toInt()
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (52 * density).toInt()
            ).apply {
                topMargin = (12 * density).toInt()
            }
            setOnClickListener { abrirAtividade(atividade) }
        }

        content.addView(titulo)
        content.addView(unidade)
        content.addView(inicio)
        content.addView(participantes)
        content.addView(continuar)
        card.addView(content)
        return card
    }

    private fun abrirAtividade(atividade: AtividadeEmProgresso) {
        sessaoUuid = atividade.uuid
        aulaInicioMs = atividade.inicioMs
        unidadeUuidSelecionada = atividade.unidadeUuid
        empresaSelecionada = null
        moduloSelecionado = null
        tipoModuloAtual = TipoModulo.DESCONHECIDO
        participantes.clear()
        participantes.addAll(atividade.participantes)
        nfcsRegistrados.clear()
        nfcsRegistrados.addAll(atividade.participantes.map { it.nfc })
        mostrarEstadoNFC(limparParticipantes = false, atividade = atividade)
    }

    private fun mostrarEstadoNFC(
        limparParticipantes: Boolean,
        atividade: AtividadeEmProgresso? = null
    ) {
        layoutSessoes.visibility = View.GONE
        layoutCriacaoAula.visibility = View.GONE
        layoutLeituraNFC.visibility = View.VISIBLE
        atualizarVisibilidadeLogout(false)
        if (limparParticipantes) {
            participantes.clear()
            nfcsRegistrados.clear()
        }
        filaNfcsPendentes.clear()
        nfcPendente = null
        atualizarListaNFCs()

        val unidade = empresaSelecionada?.unidades?.find { it.publicId == unidadeUuidSelecionada }
        textViewDescricaoAtual.text = atividade?.moduloNome
            ?: moduloSelecionado?.nome
            ?: sessionManager.getModuloEmProgresso()
            ?: ""
        textViewUnidadeAtual.text = "Unidade: ${unidade?.unidadeNome ?: atividade?.unidadeNome ?: ""}"

        ativarNFC()
        iniciarTimer()
    }

    private fun mostrarEstadoCriacao() {
        layoutSessoes.visibility = View.GONE
        layoutLeituraNFC.visibility = View.GONE
        layoutCriacaoAula.visibility = View.VISIBLE
        atualizarVisibilidadeLogout(true)
        desativarNFC()
        pararTimer()
        limparSelecaoNovaAtividade()
        sessaoUuid = null
        participantes.clear()
        nfcsRegistrados.clear()
        filaNfcsPendentes.clear()
        nfcPendente = null
    }

    private fun limparSelecaoNovaAtividade() {
        empresaSelecionada = null
        spinnerEmpresa.setText("", false)
        resetarSelecaoUnidade()
        resetarSelecaoModulo()
    }

    private fun obterNomeUnidadeAtual(): String? {
        return empresaSelecionada?.unidades
            ?.firstOrNull { it.publicId == unidadeUuidSelecionada }
            ?.unidadeNome
    }

    private fun salvarAtividadeAtual() {
        val uuid = sessaoUuid ?: return
        val inicioMs = aulaInicioMs.takeIf { it > 0 } ?: return
        val existente = sessionManager.getAtividadeEmProgresso(uuid)
        sessionManager.salvarAtividadeEmProgresso(
            AtividadeEmProgresso(
                uuid = uuid,
                inicioMs = inicioMs,
                unidadeUuid = unidadeUuidSelecionada ?: existente?.unidadeUuid,
                unidadeNome = obterNomeUnidadeAtual() ?: existente?.unidadeNome,
                moduloNome = moduloSelecionado?.nome ?: existente?.moduloNome ?: sessionManager.getModuloEmProgresso(),
                participantes = participantes.toList()
            )
        )
    }

    private fun atualizarVisibilidadeLogout(exibir: Boolean) {
        if (exibirLogoutNoMenu == exibir) return
        exibirLogoutNoMenu = exibir
        invalidateOptionsMenu()
    }

    // ==========================================
    // NFC
    // ==========================================

    override fun onResume() {
        super.onResume()
        if (layoutLeituraNFC.visibility == View.VISIBLE) {
            ativarNFC()
        }
    }

    override fun onPause() {
        super.onPause()
        salvarAtividadeAtual()
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
                if (layoutLeituraNFC.visibility == View.VISIBLE) {
                    registrarNFC(nfcId)
                }
            }
        }
    }

    private fun registrarNFC(nfc: String) {
        if (nfcsRegistrados.contains(nfc) || nfcPendente == nfc || filaNfcsPendentes.contains(nfc)) {
            Toast.makeText(this, "Crachá já registrado", Toast.LENGTH_SHORT).show()
            return
        }

        if (nfcPendente != null) {
            filaNfcsPendentes.addLast(nfc)
            return
        }

        iniciarValidacaoNfc(nfc)
    }

    private fun iniciarValidacaoNfc(nfc: String) {
        val token = sessionManager.getTokenWithBearer() ?: return
        val unidadeUuid = unidadeUuidSelecionada ?: return
        nfcPendente = nfc
        viewModel.validarNFC(token, nfc, unidadeUuid)
    }

    private fun validarProximoNfcDaFila() {
        if (nfcPendente != null) return
        val proximo = filaNfcsPendentes.pollFirst() ?: return
        iniciarValidacaoNfc(proximo)
    }

    private fun atualizarListaNFCs() {
        if (participantes.isEmpty()) {
            textViewListaNFCs.text = "Nenhum crachá lido ainda."
        } else {
            textViewListaNFCs.text = participantes.joinToString("\n") { p ->
                val nome = p.nome?.takeIf { it.isNotBlank() }
                if (nome != null) "$nome - ${p.nfc}"
                else "Não cadastrado - Desconhecido (${p.nfc})"
            }
        }
        textViewContador.text = "Participantes registrados: ${participantes.size}"
    }

    private fun ativarNFC() {
        val adapter = nfcAdapter ?: return
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        try {
            adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        } catch (e: Exception) {
            Log.w("NFC", "Erro ao ativar NFC: ${e.message}")
        }
    }

    private fun desativarNFC() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: Exception) {
            Log.w("NFC", "Erro ao desativar NFC: ${e.message}")
        }
    }

    // ==========================================
    // TIMER
    // ==========================================

    private fun iniciarTimer() {
        pararTimer()
        val inicio = aulaInicioMs.takeIf { it > 0 } ?: System.currentTimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - inicio
                val h = elapsed / 3600000
                val m = (elapsed % 3600000) / 60000
                val s = (elapsed % 60000) / 1000
                textViewDuracaoAula.text = "Duração: %02d:%02d:%02d".format(h, m, s)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerRunnable = runnable
        timerHandler.post(runnable)
    }

    private fun pararTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private fun limparAulaEmProgresso(uuid: String?) {
        if (uuid != null) {
            sessionManager.removerAtividadeEmProgresso(uuid)
        }
        sessionManager.limparAulaEmProgresso()
        sessionManager.limparUnidadeEmProgresso()
        sessionManager.limparModuloEmProgresso()
        sessaoUuid = null
        aulaInicioMs = 0L
        participantes.clear()
        nfcsRegistrados.clear()
        filaNfcsPendentes.clear()
        nfcPendente = null
    }

    private fun obterTimestampUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    private fun formatarDataLocal(timestampMs: Long): String {
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return format.format(Date(timestampMs))
    }

    private fun irParaLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun encerrarSessaoEIrParaLogin() {
        sessionManager.clearSession()
        irParaLogin()
    }
}
