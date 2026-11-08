package com.digitalsix.YouSafe

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
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
import android.widget.RadioButton
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
import androidx.lifecycle.lifecycleScope
import kotlin.math.max
import com.digitalsix.YouSafe.network.AbortarAulaRequest
import com.digitalsix.YouSafe.network.ColetaMobileRequest
import com.digitalsix.YouSafe.network.ConfirmarSessaoRequest
import com.digitalsix.YouSafe.network.ParticipanteSessao
import com.digitalsix.YouSafe.network.RetrofitInstance
import com.digitalsix.YouSafe.network.SincronizarSessaoRequest
import com.digitalsix.YouSafe.network.modulos.moduloResponse
import com.digitalsix.YouSafe.repository.ApiResult
import com.digitalsix.YouSafe.repository.MainRepository
import com.digitalsix.YouSafe.utils.AcaoSessaoPendente
import com.digitalsix.YouSafe.utils.AtividadeEmProgresso
import com.digitalsix.YouSafe.utils.NetworkMonitor
import com.digitalsix.YouSafe.utils.SessaoOfflinePendente
import com.digitalsix.YouSafe.utils.SessionManager
import com.digitalsix.YouSafe.viewmodel.AcaoState
import com.digitalsix.YouSafe.viewmodel.MainUiState
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
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private companion object {
        const val ACAO_CONFIRMAR = "confirmar"
        const val ACAO_ABORTAR = "abortar"
    }

    private var metodoPresenca = "nfc"
    private lateinit var faceAttendance: com.digitalsix.YouSafe.face.FaceAttendanceController
    private var facialEmEnvio = false
    private lateinit var viewModel: MainViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: MainRepository
    private lateinit var networkMonitor: NetworkMonitor
    private var nfcAdapter: NfcAdapter? = null
    private var isOnline: Boolean = true
    private var sincronizandoPendentes: Boolean = false

    private lateinit var mainScrollView: ScrollView
    private lateinit var statusBarScrim: View

    // Views — Estado 0: Sessões abertas
    private lateinit var layoutSessoes: MaterialCardView
    private lateinit var textViewResumoSessoes: TextView
    private lateinit var layoutListaSessoes: LinearLayout
    private lateinit var buttonNovaAtividade: MaterialButton
    private lateinit var buttonVoltarSessoesCriacao: MaterialButton
    private lateinit var layoutPendenciasSincronizacao: MaterialCardView
    private lateinit var textViewResumoPendencias: TextView
    private lateinit var layoutListaPendencias: LinearLayout
    private lateinit var buttonSincronizarPendencias: MaterialButton

    // Views — Estado 1: Criação de Aula
    private lateinit var toolbar: MaterialToolbar
    private lateinit var textViewBemVindo: TextView
    private lateinit var textViewStatusConexao: TextView
    private lateinit var layoutCriacaoAula: MaterialCardView
    private lateinit var spinnerEmpresa: AutoCompleteTextView
    private lateinit var spinnerUnidade: AutoCompleteTextView
    private lateinit var layoutSpinnerUnidade: TextInputLayout
    private lateinit var spinnerModule: AutoCompleteTextView
    private lateinit var layoutSpinnerModule: TextInputLayout
    private lateinit var layoutTreinamentoFields: LinearLayout
    private lateinit var textViewNomeTreinamentoLabel: TextView
    private lateinit var editTextNomeTreinamento: AutoCompleteTextView
    private lateinit var cardTipoBatidaContainer: MaterialCardView
    private lateinit var cardBatidaUnica: MaterialCardView
    private lateinit var cardBatidaDupla: MaterialCardView
    private lateinit var radioBatidaUnica: RadioButton
    private lateinit var radioBatidaDupla: RadioButton
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
    private var quantidadeApontamentosAtual: Int = 1
    private var apontamentoAtual: Int = 1
    private var empresasPopuladas = false
    private var catalogosPreCarregados = false
    private var confirmacaoEmEnvioUuid: String? = null
    private var abortarEmEnvioUuid: String? = null
    private var iniciarTreinamentoEmEnvioUuid: String? = null
    private var nfcPendente: String? = null
    private val filaNfcsPendentes = ArrayDeque<String>()
    private var exibirLogoutNoMenu: Boolean = true
    private var treinamentoUuidSelecionadoEmCriacao: String? = null
    private var ginasticaLaboralUuidSelecionadaEmCriacao: String? = null

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        RetrofitInstance.initialize(applicationContext)
        configurarBarraStatus()

        sessionManager = SessionManager(this)
        isOnline = NetworkMonitor.isOnline(this)
        if (!sessionManager.isAccessTokenValid() && (isOnline || !sessionManager.isLoggedIn())) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show()
            encerrarSessaoEIrParaLogin()
            return
        }

        repository = MainRepository(RetrofitInstance.api)
        val factory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        inicializarViews()
        faceAttendance = com.digitalsix.YouSafe.face.FaceAttendanceController(
            this, findViewById(R.id.buttonCaptureFace),
            textViewListaNFCs, textViewContador, findViewById(R.id.textFacialStatus)
        ) { busy ->
            facialEmEnvio = busy
            buttonConfirmarAula.isEnabled = !busy
            buttonAbortarAula.isEnabled = !busy
            buttonVoltarSessoesNFC.isEnabled = !busy
        }
        faceAttendance.restore(savedInstanceState)
        configurarTeclado()
        configurarToolbar()
        configurarDropdowns()
        configurarBotoes()
        configurarTipoBatida()
        observarViewModel()
        configurarMonitorConectividade()

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

        val restored = savedInstanceState?.getString("active_face_session")
            ?.let { sessionManager.getAtividadeEmProgresso(it) }
        if (restored != null) abrirAtividade(restored) else mostrarTelaSessoes()
    }

    // ==========================================
    // INICIALIZAÇÃO
    // ==========================================

    private fun inicializarViews() {
        mainScrollView = findViewById(R.id.mainScrollView)
        statusBarScrim = findViewById(R.id.statusBarScrim)
        toolbar = findViewById(R.id.toolbar)
        textViewBemVindo = findViewById(R.id.textViewBemVindo)
        textViewStatusConexao = findViewById(R.id.textViewStatusConexao)
        layoutSessoes = findViewById(R.id.layoutSessoes)
        textViewResumoSessoes = findViewById(R.id.textViewResumoSessoes)
        layoutListaSessoes = findViewById(R.id.layoutListaSessoes)
        buttonNovaAtividade = findViewById(R.id.buttonNovaAtividade)
        buttonVoltarSessoesCriacao = findViewById(R.id.buttonVoltarSessoesCriacao)
        layoutPendenciasSincronizacao = findViewById(R.id.layoutPendenciasSincronizacao)
        textViewResumoPendencias = findViewById(R.id.textViewResumoPendencias)
        layoutListaPendencias = findViewById(R.id.layoutListaPendencias)
        buttonSincronizarPendencias = findViewById(R.id.buttonSincronizarPendencias)
        layoutCriacaoAula = findViewById(R.id.layoutCriacaoAula)
        spinnerEmpresa = findViewById(R.id.spinnerEmpresa)
        spinnerUnidade = findViewById(R.id.spinnerUnidade)
        layoutSpinnerUnidade = findViewById(R.id.layoutSpinnerUnidade)
        spinnerModule = findViewById(R.id.spinnerModule)
        layoutSpinnerModule = findViewById(R.id.layoutSpinnerModule)
        layoutTreinamentoFields = findViewById(R.id.layoutTreinamentoFields)
        textViewNomeTreinamentoLabel = findViewById(R.id.textViewNomeTreinamentoLabel)
        editTextNomeTreinamento = findViewById(R.id.editTextNomeTreinamento)
        cardTipoBatidaContainer = findViewById(R.id.cardTipoBatidaContainer)
        cardBatidaUnica = findViewById(R.id.cardBatidaUnica)
        cardBatidaDupla = findViewById(R.id.cardBatidaDupla)
        radioBatidaUnica = findViewById(R.id.radioBatidaUnica)
        radioBatidaDupla = findViewById(R.id.radioBatidaDupla)
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

    private fun configurarMonitorConectividade() {
        networkMonitor = NetworkMonitor(this) { online ->
            runOnUiThread {
                val reconectou = !isOnline && online
                isOnline = online
                atualizarIndicadorConexao()
                if (reconectou || online) {
                    sincronizarSessoesPendentes()
                    if (!catalogosPreCarregados) {
                        val empresasMap = viewModel.uiState.value?.empresasMap.orEmpty()
                        if (empresasMap.isNotEmpty()) {
                            catalogosPreCarregados = true
                            precarregarCatalogosTreinamento(empresasMap)
                        }
                    }
                }
            }
        }
        networkMonitor.start()
        atualizarIndicadorConexao()
    }

    private fun atualizarIndicadorConexao() {
        if (!::textViewStatusConexao.isInitialized) return
        val pendentes = totalPendenciasDoUsuario()
        textViewStatusConexao.visibility =
            if (!isOnline || pendentes > 0 || sincronizandoPendentes) View.VISIBLE else View.GONE
        textViewStatusConexao.text = when {
            !isOnline && pendentes > 0 ->
                "Modo offline • $pendentes atividade(s) aguardando sincronização"
            !isOnline -> "Modo offline • leituras serão salvas no aparelho"
            sincronizandoPendentes -> "Sincronizando atividades pendentes..."
            pendentes > 0 -> "$pendentes atividade(s) aguardando sincronização"
            else -> ""
        }
        atualizarCardPendencias()
    }

    private fun sincronizarSessoesPendentes() {
        if (!isOnline || sincronizandoPendentes) return
        val token = sessionManager.getTokenWithBearer() ?: return
        val sessoesPendentes = sessionManager.getSessoesOfflinePendentesDoUsuario()
        val acoesPendentes = sessionManager.getAcoesSessaoPendentesDoUsuario()
        if (sessoesPendentes.isEmpty() && acoesPendentes.isEmpty()) {
            atualizarIndicadorConexao()
            return
        }

        sincronizandoPendentes = true
        atualizarIndicadorConexao()
        atualizarCardPendencias()
        lifecycleScope.launch {
            var sincronizadas = 0
            var erroSincronizacao: String? = null
            for (pendente in sessoesPendentes) {
                when (val result = repository.sincronizarSessao(token, pendente.request)) {
                    is ApiResult.Success -> {
                        sessionManager.removerSessaoOfflinePendente(pendente.localId)
                        sincronizadas++
                    }
                    is ApiResult.Error -> {
                        erroSincronizacao = "Não foi possível sincronizar uma atividade: ${result.message}"
                        break
                    }
                }
            }
            for (acao in sessionManager.getAcoesSessaoPendentesDoUsuario()) {
                if (erroSincronizacao != null) break
                val result = when (acao.tipo) {
                    ACAO_CONFIRMAR -> {
                        val request = acao.confirmarRequest ?: continue
                        repository.confirmarSessao(token, acao.sessaoUuid, request)
                    }
                    ACAO_ABORTAR -> {
                        val request = acao.abortarRequest ?: continue
                        repository.abortarSessao(token, acao.sessaoUuid, request)
                    }
                    else -> continue
                }

                when (result) {
                    is ApiResult.Success -> {
                        sessionManager.removerAcaoSessaoPendente(acao.localId)
                        sincronizadas++
                    }
                    is ApiResult.Error -> {
                        erroSincronizacao = "Não foi possível sincronizar uma atividade: ${result.message}"
                        break
                    }
                }
            }
            sincronizandoPendentes = false
            atualizarIndicadorConexao()
            atualizarListaSessoes()
            if (sincronizadas > 0) {
                Toast.makeText(
                    this@MainActivity,
                    "$sincronizadas atividade(s) sincronizada(s).",
                    Toast.LENGTH_SHORT
                ).show()
            }
            erroSincronizacao?.let {
                Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sincronizarPendenciasManualmente() {
        if (!isOnline) {
            Toast.makeText(this, "Sem internet para sincronizar agora.", Toast.LENGTH_SHORT).show()
            atualizarCardPendencias()
            return
        }
        if (totalPendenciasDoUsuario() == 0) {
            Toast.makeText(this, "Nenhuma atividade pendente.", Toast.LENGTH_SHORT).show()
            atualizarCardPendencias()
            return
        }
        sincronizarSessoesPendentes()
    }

    private fun totalPendenciasDoUsuario(): Int {
        return sessionManager.getSessoesOfflinePendentesDoUsuario().size +
            sessionManager.getAcoesSessaoPendentesDoUsuario().size
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

        editTextNomeTreinamento.setOnClickListener { editTextNomeTreinamento.showDropDown() }
        editTextNomeTreinamento.onFocusChangeListener =
            View.OnFocusChangeListener { _, hasFocus -> if (hasFocus) editTextNomeTreinamento.showDropDown() }

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
        val treinamentosCache = sessionManager.getTreinamentosCache(unidadeUuid)
        viewModel.definirTreinamentos(treinamentosCache)
        val ginasticasCache = sessionManager.getGinasticasLaboraisCache(unidadeUuid)
        viewModel.definirGinasticasLaborais(ginasticasCache)
        if (isOnline) {
            val token = sessionManager.getTokenWithBearer() ?: return
            viewModel.carregarTreinamentos(token, unidadeUuid)
            viewModel.carregarGinasticasLaborais(token, unidadeUuid)
        }

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
        treinamentoUuidSelecionadoEmCriacao = null
        ginasticaLaboralUuidSelecionadaEmCriacao = null
        selecionarTipoBatida(1)
        layoutSpinnerModule.isEnabled = false
        spinnerModule.isEnabled = false
        layoutTreinamentoFields.visibility = View.GONE
        textViewDescricaoLabel.visibility = View.GONE
        layoutDescricaoAula.visibility = View.GONE
        viewModel.limparModulos()
        viewModel.limparTreinamentos()
        viewModel.limparGinasticasLaborais()
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
                quantidadeApontamentosAtual = 1
                apontamentoAtual = 1
                textViewNomeTreinamentoLabel.text = "Ginástica laboral"
                editTextNomeTreinamento.hint = null
                cardTipoBatidaContainer.visibility = View.GONE
                layoutTreinamentoFields.visibility = View.VISIBLE
                textViewDescricaoLabel.visibility = View.GONE
                layoutDescricaoAula.visibility = View.GONE
                val unidadeUuid = unidadeUuidSelecionada
                val cache = sessionManager.getGinasticasLaboraisCache(unidadeUuid)
                if (cache.isNotEmpty()) {
                    viewModel.definirGinasticasLaborais(cache)
                }
                if (isOnline && unidadeUuid != null) {
                    val token = sessionManager.getTokenWithBearer() ?: return
                    viewModel.carregarGinasticasLaboraisSeNecessario(token, unidadeUuid)
                }
            }
            TipoModulo.TREINAMENTO -> {
                textViewNomeTreinamentoLabel.text = "Nome do treinamento"
                editTextNomeTreinamento.hint = null
                cardTipoBatidaContainer.visibility = View.VISIBLE
                layoutTreinamentoFields.visibility = View.VISIBLE
                textViewDescricaoLabel.visibility = View.GONE
                layoutDescricaoAula.visibility = View.GONE
                val unidadeUuid = unidadeUuidSelecionada ?: return
                val cache = sessionManager.getTreinamentosCache(unidadeUuid)
                if (cache.isNotEmpty()) {
                    viewModel.definirTreinamentos(cache)
                }
                if (isOnline) {
                    val token = sessionManager.getTokenWithBearer() ?: return
                    viewModel.carregarTreinamentosSeNecessario(token, unidadeUuid)
                }
            }
            TipoModulo.DESCONHECIDO -> {
                quantidadeApontamentosAtual = 1
                apontamentoAtual = 1
                layoutTreinamentoFields.visibility = View.GONE
                textViewDescricaoLabel.visibility = View.GONE
                layoutDescricaoAula.visibility = View.GONE
            }
        }
        atualizarAdapterCatalogoAtividade(viewModel.uiState.value ?: return)
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
            if (state.empresasMap.isNotEmpty() && isOnline && !catalogosPreCarregados) {
                catalogosPreCarregados = true
                precarregarCatalogosTreinamento(state.empresasMap)
            }

            if (state.modulos.isNotEmpty()) {
                val nomes = state.modulos.map { it.nome.orEmpty() }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nomes)
                spinnerModule.setAdapter(adapter)
            }

            if (state.treinamentos.isNotEmpty()) {
                unidadeUuidSelecionada?.let {
                    sessionManager.salvarTreinamentosCache(it, state.treinamentos)
                }
            }
            if (state.ginasticasLaborais.isNotEmpty()) {
                unidadeUuidSelecionada?.let {
                    sessionManager.salvarGinasticasLaboraisCache(it, state.ginasticasLaborais)
                }
            }
            atualizarAdapterCatalogoAtividade(state)

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
                    findViewById<View>(R.id.radioPresenceNfc).isEnabled = false
                    findViewById<View>(R.id.radioPresenceFace).isEnabled = false
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
                            metodoPresenca = metodoPresenca,
                            inicioMs = aulaInicioMs,
                            unidadeUuid = unidadeUuidSelecionada,
                            unidadeNome = obterNomeUnidadeAtual(),
                            moduloNome = moduloSelecionado?.nome,
                            observacoes = obterDescricaoAtividadeAtual(),
                            quantidadeApontamentos = quantidadeApontamentosAtual,
                            apontamentoAtual = apontamentoAtual,
                            treinamentoUuid = treinamentoUuidSelecionadoEmCriacao,
                            ginasticaLaboralUuid = ginasticaLaboralUuidSelecionadaEmCriacao
                        )
                    )
                    mostrarEstadoNFC(limparParticipantes = true)
                    viewModel.consumirCriarSessaoEstado()
                }
                is AcaoState.Erro -> {
                    if (deveEnfileirarPorFalhaConexao(state.mensagem) && iniciarAulaOfflinePorFalhaConexao()) {
                        viewModel.consumirCriarSessaoEstado()
                        return@observe
                    }
                    buttonIniciarAula.isEnabled = true
                    buttonIniciarAula.text = "Iniciar atividade"
                    findViewById<View>(R.id.radioPresenceNfc).isEnabled = true
                    findViewById<View>(R.id.radioPresenceFace).isEnabled = true
                    Toast.makeText(this, state.mensagem, Toast.LENGTH_LONG).show()
                    viewModel.consumirCriarSessaoEstado()
                }
                is AcaoState.Idle -> {
                    buttonIniciarAula.isEnabled = true
                    buttonIniciarAula.text = "Iniciar atividade"
                    findViewById<View>(R.id.radioPresenceNfc).isEnabled = true
                    findViewById<View>(R.id.radioPresenceFace).isEnabled = true
                }
            }
        }

        viewModel.iniciarTreinamentoSessaoState.observe(this) { state ->
            atualizarBloqueioAcaoFacial(state)
            when (state) {
                is AcaoState.Loading -> {
                    buttonConfirmarAula.isEnabled = false
                    buttonConfirmarAula.text = "Iniciando treinamento..."
                }
                is AcaoState.Sucesso -> {
                    iniciarTreinamentoEmEnvioUuid = null
                    aulaInicioMs = System.currentTimeMillis()
                    apontamentoAtual = 2
                    participantes.clear()
                    nfcsRegistrados.clear()
                    filaNfcsPendentes.clear()
                    nfcPendente = null
                    sessaoUuid?.let { uuid ->
                        sessionManager.salvarInicioAulaEmProgresso(aulaInicioMs)
                        sessionManager.salvarAtividadeEmProgresso(
                            AtividadeEmProgresso(
                                uuid = uuid,
                                metodoPresenca = metodoPresenca,
                                inicioMs = aulaInicioMs,
                                unidadeUuid = unidadeUuidSelecionada,
                                unidadeNome = obterNomeUnidadeAtual(),
                                moduloNome = moduloSelecionado?.nome ?: sessionManager.getModuloEmProgresso(),
                                observacoes = sessionManager.getAtividadeEmProgresso(uuid)?.observacoes,
                                participantes = emptyList(),
                                quantidadeApontamentos = quantidadeApontamentosAtual,
                                apontamentoAtual = apontamentoAtual
                            )
                        )
                    }
                    Toast.makeText(this, "Treinamento iniciado. Faça a segunda batida.", Toast.LENGTH_SHORT).show()
                    mostrarEstadoNFC(limparParticipantes = true)
                    viewModel.consumirIniciarTreinamentoSessaoEstado()
                }
                is AcaoState.Erro -> {
                    val uuid = iniciarTreinamentoEmEnvioUuid
                    iniciarTreinamentoEmEnvioUuid = null
                    if (uuid != null && metodoPresenca != "facial" && deveEnfileirarPorFalhaConexao(state.mensagem)) {
                        isOnline = false
                        atualizarIndicadorConexao()
                        sessionManager.getAtividadeEmProgresso(uuid)?.let { atividade ->
                            iniciarTreinamentoOffline(atividade)
                            viewModel.consumirIniciarTreinamentoSessaoEstado()
                            return@observe
                        }
                    }
                    buttonConfirmarAula.isEnabled = true
                    atualizarTextoBotaoConfirmar()
                    Toast.makeText(this, state.mensagem, Toast.LENGTH_LONG).show()
                    viewModel.consumirIniciarTreinamentoSessaoEstado()
                }
                is AcaoState.Idle -> {
                    buttonConfirmarAula.isEnabled = true
                    atualizarTextoBotaoConfirmar()
                }
            }
        }

        viewModel.confirmarSessaoState.observe(this) { state ->
            atualizarBloqueioAcaoFacial(state)
            when (state) {
                is AcaoState.Sucesso -> {
                    val uuidFinalizada = sessaoUuid
                    confirmacaoEmEnvioUuid = null
                    limparAulaEmProgresso(uuidFinalizada)
                    Toast.makeText(this, "Atividade confirmada com sucesso!", Toast.LENGTH_SHORT).show()
                    mostrarTelaSessoes()
                    viewModel.consumirConfirmarSessaoEstado()
                }
                is AcaoState.Erro -> {
                    val uuid = confirmacaoEmEnvioUuid
                    confirmacaoEmEnvioUuid = null
                    if (uuid != null && deveEnfileirarPorFalhaConexao(state.mensagem)) {
                        salvarConfirmacaoOnlinePendente(uuid)
                        viewModel.consumirConfirmarSessaoEstado()
                        return@observe
                    }
                    Toast.makeText(this, state.mensagem, Toast.LENGTH_LONG).show()
                    viewModel.consumirConfirmarSessaoEstado()
                }
                else -> {}
            }
        }

        viewModel.abortarSessaoState.observe(this) { state ->
            atualizarBloqueioAcaoFacial(state)
            when (state) {
                is AcaoState.Sucesso -> {
                    val uuidAbortada = sessaoUuid
                    abortarEmEnvioUuid = null
                    limparAulaEmProgresso(uuidAbortada)
                    Toast.makeText(this, "Atividade abortada.", Toast.LENGTH_SHORT).show()
                    mostrarTelaSessoes()
                    viewModel.consumirAbortarSessaoEstado()
                }
                is AcaoState.Erro -> {
                    val uuid = abortarEmEnvioUuid
                    abortarEmEnvioUuid = null
                    if (uuid != null && deveEnfileirarPorFalhaConexao(state.mensagem)) {
                        salvarAbortarOnlinePendente(uuid)
                        viewModel.consumirAbortarSessaoEstado()
                        return@observe
                    }
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
                        horarioRegistro = obterTimestampUtc(),
                        numeroApontamento = apontamentoAtual
                    )
                    participantes.add(participante)
                    nfcsRegistrados.add(nfc)
                    salvarParticipantesAtividadeAtual()
                    nfcPendente = null
                    atualizarListaNFCs()
                    viewModel.consumirValidarNFCEstado()
                    validarProximoNfcDaFila()
                }
                is AcaoState.Erro -> {
                    val nfc = nfcPendente
                    if (nfc != null && deveRegistrarNfcOfflinePorFalhaConexao(state.mensagem)) {
                        registrarNfcSemValidacao(nfc)
                        viewModel.consumirValidarNFCEstado()
                        validarProximoNfcDaFila()
                        return@observe
                    }
                    nfcPendente = null
                    Toast.makeText(this, "Crachá não reconhecido: ${state.mensagem}", Toast.LENGTH_LONG).show()
                    viewModel.consumirValidarNFCEstado()
                    validarProximoNfcDaFila()
                }
                else -> {}
            }
        }
    }

    private fun atualizarAdapterCatalogoAtividade(state: MainUiState) {
        val nomes = when (tipoModuloAtual) {
            TipoModulo.GINASTICA_LABORAL -> state.ginasticasLaborais.map { it.nomeExibicao }
            TipoModulo.TREINAMENTO -> state.treinamentos.map { it.nome }
            TipoModulo.DESCONHECIDO -> emptyList()
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nomes)
        editTextNomeTreinamento.setAdapter(adapter)
    }

    private fun precarregarCatalogosTreinamento(empresasMap: Map<String, EmpresaComUnidades>) {
        val token = sessionManager.getTokenWithBearer() ?: return
        val unidades = empresasMap.values.flatMap { it.unidades }.distinctBy { it.publicId }
        lifecycleScope.launch {
            for (unidade in unidades) {
                when (val result = repository.getTreinamentos(token, unidade.publicId)) {
                    is ApiResult.Success ->
                        sessionManager.salvarTreinamentosCache(unidade.publicId, result.data)
                    is ApiResult.Error -> Unit
                }
                when (val result = repository.getGinasticasLaborais(token, unidade.publicId)) {
                    is ApiResult.Success ->
                        sessionManager.salvarGinasticasLaboraisCache(unidade.publicId, result.data)
                    is ApiResult.Error -> Unit
                }
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
        buttonSincronizarPendencias.setOnClickListener { sincronizarPendenciasManualmente() }
        buttonConfirmarAula.setOnClickListener {
            if (deveIniciarTreinamentoComBatidaAtual()) iniciarTreinamentoComBatidas() else confirmarAula()
        }
        buttonAbortarAula.setOnClickListener { abortarAula() }
    }

    private fun configurarTipoBatida() {
        cardBatidaUnica.setOnClickListener { selecionarTipoBatida(1) }
        cardBatidaDupla.setOnClickListener { selecionarTipoBatida(2) }
        selecionarTipoBatida(1)
    }

    private fun selecionarTipoBatida(quantidadeApontamentos: Int) {
        if (!::radioBatidaUnica.isInitialized || !::radioBatidaDupla.isInitialized) return
        val usarBatidaDupla = quantidadeApontamentos == 2
        radioBatidaUnica.isChecked = !usarBatidaDupla
        radioBatidaDupla.isChecked = usarBatidaDupla
        atualizarVisualTipoBatida()
    }

    private fun atualizarVisualTipoBatida() {
        if (!::cardBatidaUnica.isInitialized || !::cardBatidaDupla.isInitialized) return
        val density = resources.displayMetrics.density
        val selectedStroke = (2 * density).toInt()
        val defaultStroke = (1 * density).toInt()
        val isUnicaSelecionada = radioBatidaUnica.isChecked

        cardBatidaUnica.setCardBackgroundColor(getColor(if (isUnicaSelecionada) R.color.turquoise_light else R.color.surface))
        cardBatidaUnica.strokeColor = getColor(if (isUnicaSelecionada) R.color.turquoise else R.color.divider)
        cardBatidaUnica.strokeWidth = if (isUnicaSelecionada) selectedStroke else defaultStroke

        cardBatidaDupla.setCardBackgroundColor(getColor(if (!isUnicaSelecionada) R.color.turquoise_light else R.color.surface))
        cardBatidaDupla.strokeColor = getColor(if (!isUnicaSelecionada) R.color.turquoise else R.color.divider)
        cardBatidaDupla.strokeWidth = if (!isUnicaSelecionada) selectedStroke else defaultStroke
    }

    private fun iniciarAula() {
        metodoPresenca = if (findViewById<RadioButton>(R.id.radioPresenceFace).isChecked) "facial" else "nfc"
        if (metodoPresenca == "facial") isOnline = NetworkMonitor.isOnline(this)
        if (metodoPresenca == "facial" && !isOnline) {
            Toast.makeText(this, "A presença facial precisa de conexão com a internet.", Toast.LENGTH_LONG).show()
            return
        }
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
        val ginasticaLaboralUuid: String?

        when (tipoModuloAtual) {
            TipoModulo.GINASTICA_LABORAL -> {
                val nomeSelecionado = editTextNomeTreinamento.text?.toString()?.trim() ?: ""
                if (nomeSelecionado.isBlank()) {
                    Toast.makeText(this, "Selecione uma ginástica laboral.", Toast.LENGTH_SHORT).show()
                    return
                }
                val existente = viewModel.uiState.value?.ginasticasLaborais
                    ?.find { it.nomeExibicao.equals(nomeSelecionado, ignoreCase = true) }
                ginasticaLaboralUuid = existente?.identificador
                descricao = existente?.nomeExibicao ?: nomeSelecionado
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
                ginasticaLaboralUuid = null
            }
            TipoModulo.DESCONHECIDO -> {
                Toast.makeText(this, "Tipo de módulo não reconhecido.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        quantidadeApontamentosAtual = if (tipoModuloAtual == TipoModulo.TREINAMENTO) obterQuantidadeApontamentosSelecionada() else 1
        apontamentoAtual = 1

        treinamentoUuidSelecionadoEmCriacao = treinamentoUuid
        ginasticaLaboralUuidSelecionadaEmCriacao = ginasticaLaboralUuid

        if (!isOnline) {
            if (treinamentoUuid.isNullOrBlank() && ginasticaLaboralUuid.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    "Offline, selecione uma atividade já existente no catálogo.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            iniciarAulaOffline(
                unidadeUuid = unidadeUuid,
                moduloUuid = modulo.publicId ?: run {
                    Toast.makeText(this, "Módulo sem UUID.", Toast.LENGTH_SHORT).show()
                    return
                },
                treinamentoUuid = treinamentoUuid,
                ginasticaLaboralUuid = ginasticaLaboralUuid,
                observacoes = descricao,
                quantidadeApontamentos = quantidadeApontamentosAtual
            )
            return
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
            treinamentoSelecionadoId = treinamentoUuid,
            ginasticaLaboralSelecionadaId = ginasticaLaboralUuid,
            quantidadeApontamentos = quantidadeApontamentosAtual
        )
    }

    private fun iniciarAulaOfflinePorFalhaConexao(): Boolean {
        if (metodoPresenca == "facial") return false
        val unidadeUuid = unidadeUuidSelecionada ?: return false
        val moduloUuid = moduloSelecionado?.publicId ?: return false
        val atividadeSelecionada =
            !treinamentoUuidSelecionadoEmCriacao.isNullOrBlank() ||
                !ginasticaLaboralUuidSelecionadaEmCriacao.isNullOrBlank()
        if (!atividadeSelecionada) {
            isOnline = false
            atualizarIndicadorConexao()
            Toast.makeText(
                this,
                "Sem conexão, selecione uma atividade já existente no catálogo.",
                Toast.LENGTH_LONG
            ).show()
            return true
        }

        isOnline = false
        atualizarIndicadorConexao()
        iniciarAulaOffline(
            unidadeUuid = unidadeUuid,
            moduloUuid = moduloUuid,
            treinamentoUuid = treinamentoUuidSelecionadoEmCriacao,
            ginasticaLaboralUuid = ginasticaLaboralUuidSelecionadaEmCriacao,
            observacoes = obterDescricaoAtividadeAtual().orEmpty(),
            quantidadeApontamentos = quantidadeApontamentosAtual
        )
        return true
    }

    private fun iniciarAulaOffline(
        unidadeUuid: String,
        moduloUuid: String,
        treinamentoUuid: String?,
        ginasticaLaboralUuid: String?,
        observacoes: String,
        quantidadeApontamentos: Int
    ) {
        val localId = "offline-${UUID.randomUUID()}"
        val inicioUtc = obterTimestampUtc()
        sessaoUuid = localId
        aulaInicioMs = System.currentTimeMillis()
        quantidadeApontamentosAtual = quantidadeApontamentos
        apontamentoAtual = 1

        sessionManager.salvarAulaEmProgresso(localId)
        sessionManager.salvarInicioAulaEmProgresso(aulaInicioMs)
        sessionManager.salvarUnidadeEmProgresso(unidadeUuid)
        sessionManager.salvarModuloEmProgresso(moduloSelecionado?.nome)
        sessionManager.salvarAtividadeEmProgresso(
            AtividadeEmProgresso(
                uuid = localId,
                inicioMs = aulaInicioMs,
                unidadeUuid = unidadeUuid,
                unidadeNome = obterNomeUnidadeAtual(),
                moduloNome = moduloSelecionado?.nome,
                quantidadeApontamentos = quantidadeApontamentos,
                apontamentoAtual = 1,
                offline = true,
                moduloUuid = moduloUuid,
                treinamentoUuid = treinamentoUuid,
                ginasticaLaboralUuid = ginasticaLaboralUuid,
                observacoes = observacoes,
                dataInicioUtc = inicioUtc,
                instrutorUuid = sessionManager.getUsuario()?.instrutorUuid
            )
        )

        Toast.makeText(this, "Atividade iniciada em modo offline.", Toast.LENGTH_SHORT).show()
        mostrarEstadoNFC(limparParticipantes = true)
    }

    private fun obterQuantidadeApontamentosSelecionada(): Int {
        return if (::radioBatidaDupla.isInitialized && radioBatidaDupla.isChecked) 2 else 1
    }

    private fun deveIniciarTreinamentoComBatidaAtual(): Boolean {
        return quantidadeApontamentosAtual == 2 && apontamentoAtual == 1
    }

    private fun atualizarBloqueioAcaoFacial(state: AcaoState) {
        if (metodoPresenca != "facial") return
        val enabled = state !is AcaoState.Loading && !facialEmEnvio
        findViewById<View>(R.id.buttonCaptureFace).isEnabled = enabled
        buttonConfirmarAula.isEnabled = enabled
        buttonAbortarAula.isEnabled = enabled
        buttonVoltarSessoesNFC.isEnabled = enabled
    }

    private fun atualizarTextoBotaoConfirmar() {
        if (facialEmEnvio) return
        if (!::buttonConfirmarAula.isInitialized) return
        buttonConfirmarAula.isEnabled = true
        buttonConfirmarAula.text = if (deveIniciarTreinamentoComBatidaAtual()) {
            "Iniciar treinamento"
        } else {
            "Confirmar atividade"
        }
    }

    private fun iniciarTreinamentoComBatidas() {
        if (facialEmEnvio) return
        if (metodoPresenca == "facial" && !NetworkMonitor.isOnline(this)) {
            Toast.makeText(this, "Conecte-se para iniciar o treinamento facial.", Toast.LENGTH_LONG).show()
            return
        }
        val uuid = sessaoUuid ?: return
        val atividade = sessionManager.getAtividadeEmProgresso(uuid) ?: return
        if (atividade.offline || !isOnline || !NetworkMonitor.isOnline(this)) {
            isOnline = false
            atualizarIndicadorConexao()
            iniciarTreinamentoOffline(atividade)
            return
        }
        val token = sessionManager.getTokenWithBearer() ?: return
        iniciarTreinamentoEmEnvioUuid = uuid
        viewModel.iniciarTreinamentoSessao(token, uuid, obterTimestampUtc(), participantes.toList())
    }

    private fun iniciarTreinamentoOffline(atividade: AtividadeEmProgresso) {
        salvarParticipantesAtividadeAtual()
        val atividadeAtualizada = sessionManager.getAtividadeEmProgresso(atividade.uuid) ?: atividade
        val inicioTreinamentoUtc = obterTimestampUtc()
        aulaInicioMs = System.currentTimeMillis()
        apontamentoAtual = 2
        participantes.clear()
        nfcsRegistrados.clear()
        filaNfcsPendentes.clear()
        nfcPendente = null

        sessionManager.salvarInicioAulaEmProgresso(aulaInicioMs)
        sessionManager.salvarAtividadeEmProgresso(
            atividadeAtualizada.copy(
                inicioMs = aulaInicioMs,
                apontamentoAtual = apontamentoAtual,
                dataInicioUtc = inicioTreinamentoUtc
            )
        )

        Toast.makeText(this, "Treinamento iniciado offline. Faça a segunda batida.", Toast.LENGTH_SHORT).show()
        mostrarEstadoNFC(limparParticipantes = true, atividade = atividadeAtualizada.copy(
            inicioMs = aulaInicioMs,
            apontamentoAtual = apontamentoAtual,
            dataInicioUtc = inicioTreinamentoUtc
        ))
    }

    private fun buildColetaMobileRequest(): ColetaMobileRequest {
        val appVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        return ColetaMobileRequest(
            appVersion = appVersion,
            platform = "android",
            platformVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            deviceModel = Build.MODEL ?: "unknown",
            deviceManufacturer = Build.MANUFACTURER ?: "unknown",
            deviceId = sessionManager.getOrCreateDeviceId()
        )
    }

    private fun confirmarAula() {
        if (facialEmEnvio) return
        val uuid = sessaoUuid ?: return
        val atividade = sessionManager.getAtividadeEmProgresso(uuid)
        if (atividade?.offline == true) {
            concluirAulaOffline(atividade)
            return
        }
        if (!isOnline || !NetworkMonitor.isOnline(this)) {
            isOnline = false
            atualizarIndicadorConexao()
            salvarConfirmacaoOnlinePendente(uuid)
            return
        }
        val participantesConfirmacao = obterParticipantesConfirmacaoOnline(uuid)
        val token = sessionManager.getTokenWithBearer() ?: return
        confirmacaoEmEnvioUuid = uuid
        viewModel.confirmarSessao(
            token,
            uuid,
            obterTimestampUtc(),
            participantesConfirmacao,
            buildColetaMobileRequest()
        )
    }

    private fun salvarConfirmacaoOnlinePendente(uuid: String) {
        val request = ConfirmarSessaoRequest(
            dataFim = obterTimestampUtc(),
            participantes = obterParticipantesConfirmacaoOnline(uuid),
            coletaMobile = buildColetaMobileRequest()
        )
        sessionManager.salvarAcaoSessaoPendente(
            AcaoSessaoPendente(
                localId = "${ACAO_CONFIRMAR}-$uuid",
                sessaoUuid = uuid,
                tipo = ACAO_CONFIRMAR,
                criadaEmMs = System.currentTimeMillis(),
                instrutorUuid = sessionManager.getUsuario()?.instrutorUuid,
                descricao = obterDescricaoAtividadePendente(uuid),
                moduloNome = obterModuloAtividadePendente(uuid),
                unidadeNome = obterUnidadeAtividadePendente(uuid),
                confirmarRequest = request
            )
        )
        limparAulaEmProgresso(uuid)
        Toast.makeText(this, "Confirmação salva para sincronização.", Toast.LENGTH_SHORT).show()
        mostrarTelaSessoes()
        atualizarIndicadorConexao()
        sincronizarSessoesPendentes()
    }

    private fun concluirAulaOffline(atividade: AtividadeEmProgresso) {
        salvarParticipantesAtividadeAtual()
        val participantesFinalizacao = obterParticipantesFinalizacaoOffline(atividade.uuid)
        val unidadeUuid = atividade.unidadeUuid
        val moduloUuid = atividade.moduloUuid
        val treinamentoUuid = atividade.treinamentoUuid
        val ginasticaLaboralUuid = atividade.ginasticaLaboralUuid
        val inicioUtc = sessionManager.getAtividadeEmProgresso(atividade.uuid)?.dataInicioUtc
            ?: atividade.dataInicioUtc
        if (unidadeUuid.isNullOrBlank() || moduloUuid.isNullOrBlank() ||
            (treinamentoUuid.isNullOrBlank() && ginasticaLaboralUuid.isNullOrBlank()) ||
            inicioUtc.isNullOrBlank()
        ) {
            Toast.makeText(this, "Dados locais incompletos. A atividade não foi encerrada.", Toast.LENGTH_LONG).show()
            return
        }

        val request = SincronizarSessaoRequest(
            unidadeId = unidadeUuid,
            moduloId = moduloUuid,
            treinamentoId = treinamentoUuid,
            ginasticaLaboralId = ginasticaLaboralUuid,
            dataInicio = inicioUtc,
            dataFim = obterTimestampUtc(),
            quantidadeApontamentos = atividade.quantidadeApontamentos,
            observacoes = atividade.observacoes.orEmpty(),
            participantes = participantesFinalizacao,
            coletaMobile = buildColetaMobileRequest()
        )
        sessionManager.salvarSessaoOfflinePendente(
            SessaoOfflinePendente(
                localId = atividade.uuid,
                criadaEmMs = System.currentTimeMillis(),
                instrutorUuid = sessionManager.getUsuario()?.instrutorUuid,
                descricao = atividade.observacoes,
                moduloNome = atividade.moduloNome,
                unidadeNome = atividade.unidadeNome,
                request = request
            )
        )

        limparAulaEmProgresso(atividade.uuid)
        Toast.makeText(
            this,
            if (isOnline) "Atividade salva. Sincronizando..." else "Atividade salva para sincronização.",
            Toast.LENGTH_SHORT
        ).show()
        mostrarTelaSessoes()
        atualizarIndicadorConexao()
        sincronizarSessoesPendentes()
    }

    private fun abortarAula() {
        AlertDialog.Builder(this)
            .setTitle("Abortar atividade")
            .setMessage("Deseja abortar a atividade? Os dados registrados serão descartados.")
            .setPositiveButton("Abortar") { _, _ ->
                val uuid = sessaoUuid ?: return@setPositiveButton
                val atividade = sessionManager.getAtividadeEmProgresso(uuid)
                if (atividade?.offline == true) {
                    limparAulaEmProgresso(uuid)
                    Toast.makeText(this, "Atividade offline descartada.", Toast.LENGTH_SHORT).show()
                    mostrarTelaSessoes()
                    return@setPositiveButton
                }
                if (!isOnline) {
                    salvarAbortarOnlinePendente(uuid)
                    return@setPositiveButton
                }
                val token = sessionManager.getTokenWithBearer() ?: return@setPositiveButton
                abortarEmEnvioUuid = uuid
                viewModel.abortarSessao(token, uuid, obterTimestampUtc(), participantes.toList())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun salvarAbortarOnlinePendente(uuid: String) {
        val request = AbortarAulaRequest(
            dataFim = obterTimestampUtc(),
            participantes = participantes.toList()
        )
        sessionManager.salvarAcaoSessaoPendente(
            AcaoSessaoPendente(
                localId = "${ACAO_ABORTAR}-$uuid",
                sessaoUuid = uuid,
                tipo = ACAO_ABORTAR,
                criadaEmMs = System.currentTimeMillis(),
                instrutorUuid = sessionManager.getUsuario()?.instrutorUuid,
                descricao = obterDescricaoAtividadePendente(uuid),
                moduloNome = obterModuloAtividadePendente(uuid),
                unidadeNome = obterUnidadeAtividadePendente(uuid),
                abortarRequest = request
            )
        )
        limparAulaEmProgresso(uuid)
        Toast.makeText(this, "Abortamento salvo para sincronização.", Toast.LENGTH_SHORT).show()
        mostrarTelaSessoes()
        atualizarIndicadorConexao()
        sincronizarSessoesPendentes()
    }

    // ==========================================
    // ESTADOS DE TELA
    // ==========================================

    private fun mostrarTelaSessoes() {
        faceAttendance.deactivate()
        salvarAtividadeAtual()
        layoutLeituraNFC.visibility = View.GONE
        layoutCriacaoAula.visibility = View.GONE
        layoutSessoes.visibility = View.VISIBLE
        layoutPendenciasSincronizacao.visibility = View.VISIBLE
        atualizarVisibilidadeLogout(true)
        desativarNFC()
        pararTimer()
        atualizarListaSessoes()
        atualizarCardPendencias()
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

    private fun atualizarCardPendencias() {
        if (!::layoutPendenciasSincronizacao.isInitialized) return
        val sessoesPendentes = sessionManager.getSessoesOfflinePendentesDoUsuario().size
        val acoesPendentes = sessionManager.getAcoesSessaoPendentesDoUsuario().size
        val totalPendentes = sessoesPendentes + acoesPendentes

        layoutPendenciasSincronizacao.visibility =
            if (totalPendentes > 0 && layoutSessoes.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        layoutListaPendencias.removeAllViews()

        if (totalPendentes == 0) {
            textViewResumoPendencias.text = "Nenhuma atividade pendente."
            buttonSincronizarPendencias.isEnabled = false
            buttonSincronizarPendencias.text = "Sincronizar agora"
            return
        }

        textViewResumoPendencias.text = buildString {
            append("$totalPendentes atividade(s) aguardando envio.")
            if (sessoesPendentes > 0) append("\n$sessoesPendentes criada(s) offline.")
            if (acoesPendentes > 0) append("\n$acoesPendentes confirmação(ões) ou abortamento(s) pendente(s).")
            if (!isOnline) append("\nReconecte para sincronizar.")
        }
        buttonSincronizarPendencias.isEnabled = isOnline && !sincronizandoPendentes
        buttonSincronizarPendencias.text =
            if (sincronizandoPendentes) "Sincronizando..." else "Sincronizar agora"

        listarDetalhesPendencias().forEach { detalhe ->
            layoutListaPendencias.addView(criarCardPendencia(detalhe))
        }
    }

    private fun criarCardPendencia(detalhe: DetalhePendencia): View {
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
            text = detalhe.tipo
            setTextColor(getColor(R.color.text_primary))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val descricao = TextView(this).apply {
            text = "Descrição: ${detalhe.descricao?.takeIf { it.isNotBlank() } ?: "não informada"}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val modulo = TextView(this).apply {
            text = "Módulo: ${detalhe.modulo?.takeIf { it.isNotBlank() } ?: "não informado"}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val unidade = TextView(this).apply {
            text = "Unidade: ${detalhe.unidade?.takeIf { it.isNotBlank() } ?: "não informada"}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val participantes = TextView(this).apply {
            text = "Participantes: ${detalhe.participantes}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }

        content.addView(titulo)
        content.addView(descricao)
        content.addView(modulo)
        content.addView(unidade)
        content.addView(participantes)
        card.addView(content)
        return card
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
            val nome = atividade.moduloNome?.takeIf { it.isNotBlank() } ?: "Atividade em andamento"
            text = if (atividade.offline) "$nome • offline" else nome
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
            text = if (atividade.metodoPresenca == "facial") {
                "Presença facial • consulte os registros ao abrir a sessão"
            } else if (atividade.quantidadeApontamentos == 2) {
                val primeiraBatida = contarParticipantesPorApontamento(atividade, 1)
                val segundaBatida = contarParticipantesPorApontamento(atividade, 2)
                "Batida atual: ${atividade.apontamentoAtual} de 2\n" +
                    "Participantes 1ª batida: $primeiraBatida\n" +
                    "Participantes 2ª batida: $segundaBatida"
            } else {
                "Participantes registrados: ${atividade.participantes.size}"
            }
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val descricao = TextView(this).apply {
            text = "Descrição: ${atividade.observacoes?.takeIf { it.isNotBlank() } ?: "não informada"}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        val modulo = TextView(this).apply {
            text = "Módulo: ${atividade.moduloNome?.takeIf { it.isNotBlank() } ?: "não informado"}"
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
        content.addView(descricao)
        content.addView(modulo)
        content.addView(unidade)
        content.addView(inicio)
        content.addView(participantes)
        content.addView(continuar)
        card.addView(content)
        return card
    }

    private fun abrirAtividade(atividade: AtividadeEmProgresso) {
        metodoPresenca = atividade.metodoPresenca ?: "nfc"
        sessaoUuid = atividade.uuid
        aulaInicioMs = atividade.inicioMs
        quantidadeApontamentosAtual = atividade.quantidadeApontamentos
        apontamentoAtual = atividade.apontamentoAtual
        unidadeUuidSelecionada = atividade.unidadeUuid
        empresaSelecionada = null
        moduloSelecionado = null
        tipoModuloAtual = TipoModulo.DESCONHECIDO
        participantes.clear()
        participantes.addAll(participantesDaBatidaAtual(atividade))
        nfcsRegistrados.clear()
        nfcsRegistrados.addAll(participantes.map { it.nfc })
        mostrarEstadoNFC(limparParticipantes = false, atividade = atividade)
    }

    private fun mostrarEstadoNFC(
        limparParticipantes: Boolean,
        atividade: AtividadeEmProgresso? = null
    ) {
        layoutSessoes.visibility = View.GONE
        layoutPendenciasSincronizacao.visibility = View.GONE
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
        atualizarTextoBotaoConfirmar()
        val facial = metodoPresenca == "facial"
        findViewById<View>(R.id.cardInstrucaoNfc).visibility = if (facial) View.GONE else View.VISIBLE
        findViewById<View>(R.id.layoutFacialAttendance).visibility = if (facial) View.VISIBLE else View.GONE
        if (facial) {
            desativarNFC()
            sessaoUuid?.let { faceAttendance.activate(it, apontamentoAtual) }
        } else faceAttendance.deactivate()

        val unidade = empresaSelecionada?.unidades?.find { it.publicId == unidadeUuidSelecionada }
        textViewDescricaoAtual.text = atividade?.moduloNome
            ?: moduloSelecionado?.nome
            ?: sessionManager.getModuloEmProgresso()
            ?: ""
        textViewUnidadeAtual.text = "Unidade: ${unidade?.unidadeNome ?: atividade?.unidadeNome ?: ""} • ${if (facial) "Facial" else "NFC"}"

        ativarNFC()
        iniciarTimer()
    }

    private fun mostrarEstadoCriacao() {
        faceAttendance.deactivate()
        layoutSessoes.visibility = View.GONE
        layoutPendenciasSincronizacao.visibility = View.GONE
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
        quantidadeApontamentosAtual = 1
        apontamentoAtual = 1
        selecionarTipoBatida(1)
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

    private fun obterNomeUnidadePorUuid(unidadeUuid: String?): String? {
        if (unidadeUuid.isNullOrBlank()) return null
        return viewModel.uiState.value?.empresasMap.orEmpty()
            .values
            .flatMap { it.unidades }
            .firstOrNull { it.publicId == unidadeUuid }
            ?.unidadeNome
            ?: empresaSelecionada?.unidades
                ?.firstOrNull { it.publicId == unidadeUuid }
                ?.unidadeNome
    }

    private fun obterNomeModuloPorUuid(moduloUuid: String?): String? {
        if (moduloUuid.isNullOrBlank()) return null
        return viewModel.uiState.value?.empresasMap.orEmpty()
            .values
            .flatMap { it.unidades }
            .flatMap { it.modulos }
            .firstOrNull { it.publicId == moduloUuid }
            ?.nome
            ?: moduloSelecionado?.takeIf { it.publicId == moduloUuid }?.nome
    }

    private fun obterDescricaoAtividadeAtual(): String? {
        return when (tipoModuloAtual) {
            TipoModulo.TREINAMENTO ->
                editTextNomeTreinamento.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            TipoModulo.GINASTICA_LABORAL ->
                editTextNomeTreinamento.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            TipoModulo.DESCONHECIDO -> null
        }
    }

    private fun obterDescricaoAtividadePendente(uuid: String): String? {
        return sessionManager.getAtividadeEmProgresso(uuid)?.observacoes
            ?: obterDescricaoAtividadeAtual()
    }

    private fun obterModuloAtividadePendente(uuid: String): String? {
        return sessionManager.getAtividadeEmProgresso(uuid)?.moduloNome
            ?: moduloSelecionado?.nome
            ?: sessionManager.getModuloEmProgresso()
    }

    private fun obterUnidadeAtividadePendente(uuid: String): String? {
        return sessionManager.getAtividadeEmProgresso(uuid)?.unidadeNome
            ?: obterNomeUnidadeAtual()
    }

    private fun listarDetalhesPendencias(): List<DetalhePendencia> {
        val sessoesOffline = sessionManager.getSessoesOfflinePendentesDoUsuario().map {
            DetalhePendencia(
                tipo = "Criada offline",
                descricao = it.descricao ?: it.request.observacoes,
                modulo = it.moduloNome?.takeIf { nome -> nome.isNotBlank() }
                    ?: obterNomeModuloPorUuid(it.request.moduloId),
                unidade = it.unidadeNome?.takeIf { nome -> nome.isNotBlank() }
                    ?: obterNomeUnidadePorUuid(it.request.unidadeId),
                participantes = it.request.participantes.size
            )
        }
        val acoes = sessionManager.getAcoesSessaoPendentesDoUsuario().map {
            val requestParticipantes = it.confirmarRequest?.participantes ?: it.abortarRequest?.participantes.orEmpty()
            DetalhePendencia(
                tipo = if (it.tipo == ACAO_ABORTAR) "Abortamento pendente" else "Confirmação pendente",
                descricao = it.descricao,
                modulo = it.moduloNome,
                unidade = it.unidadeNome,
                participantes = requestParticipantes.size
            )
        }

        return sessoesOffline + acoes
    }

    private fun participantesDaBatidaAtual(atividade: AtividadeEmProgresso): List<ParticipanteSessao> {
        return atividade.participantes.filter {
            (it.numeroApontamento ?: 1) == atividade.apontamentoAtual
        }
    }

    private fun contarParticipantesPorApontamento(
        atividade: AtividadeEmProgresso,
        apontamento: Int
    ): Int {
        return atividade.participantes.count {
            (it.numeroApontamento ?: 1) == apontamento
        }
    }

    private fun mergeParticipantesBatidaAtual(
        existentes: List<ParticipanteSessao>,
        atuais: List<ParticipanteSessao>
    ): List<ParticipanteSessao> {
        val atuaisComApontamento = atuais.map {
            if (it.numeroApontamento == apontamentoAtual) it else it.copy(numeroApontamento = apontamentoAtual)
        }
        return existentes
            .filterNot { (it.numeroApontamento ?: 1) == apontamentoAtual }
            .plus(atuaisComApontamento)
    }

    private fun salvarParticipantesAtividadeAtual() {
        val uuid = sessaoUuid ?: return
        val atividade = sessionManager.getAtividadeEmProgresso(uuid)
        val participantesPersistidos = if (quantidadeApontamentosAtual == 2) {
            mergeParticipantesBatidaAtual(atividade?.participantes.orEmpty(), participantes.toList())
        } else {
            participantes.toList()
        }
        sessionManager.atualizarParticipantesAtividade(uuid, participantesPersistidos)
    }

    private fun obterParticipantesFinalizacaoOffline(uuid: String): List<ParticipanteSessao> {
        val atividade = sessionManager.getAtividadeEmProgresso(uuid)
        return if (quantidadeApontamentosAtual == 2) {
            mergeParticipantesBatidaAtual(atividade?.participantes.orEmpty(), participantes.toList())
        } else {
            participantes.toList()
        }
    }

    private fun obterParticipantesConfirmacaoOnline(uuid: String): List<ParticipanteSessao> {
        salvarParticipantesAtividadeAtual()
        val atividade = sessionManager.getAtividadeEmProgresso(uuid)
        val treinamentoIniciadoLocalmente =
            atividade?.quantidadeApontamentos == 2 &&
                atividade.apontamentoAtual == 2 &&
                !atividade.dataInicioUtc.isNullOrBlank()

        return if (treinamentoIniciadoLocalmente) {
            mergeParticipantesBatidaAtual(atividade.participantes, participantes.toList())
        } else {
            participantes.toList()
        }
    }

    private fun salvarAtividadeAtual() {
        val uuid = sessaoUuid ?: return
        val inicioMs = aulaInicioMs.takeIf { it > 0 } ?: return
        val existente = sessionManager.getAtividadeEmProgresso(uuid)
        val participantesPersistidos = if (quantidadeApontamentosAtual == 2) {
            mergeParticipantesBatidaAtual(existente?.participantes.orEmpty(), participantes.toList())
        } else {
            participantes.toList()
        }
        sessionManager.salvarAtividadeEmProgresso(
            AtividadeEmProgresso(
                uuid = uuid,
                metodoPresenca = metodoPresenca,
                inicioMs = inicioMs,
                unidadeUuid = unidadeUuidSelecionada ?: existente?.unidadeUuid,
                unidadeNome = obterNomeUnidadeAtual() ?: existente?.unidadeNome,
                moduloNome = moduloSelecionado?.nome ?: existente?.moduloNome ?: sessionManager.getModuloEmProgresso(),
                participantes = participantesPersistidos,
                quantidadeApontamentos = quantidadeApontamentosAtual,
                apontamentoAtual = apontamentoAtual,
                offline = existente?.offline ?: false,
                moduloUuid = existente?.moduloUuid,
                treinamentoUuid = existente?.treinamentoUuid,
                ginasticaLaboralUuid = existente?.ginasticaLaboralUuid,
                observacoes = existente?.observacoes ?: obterDescricaoAtividadeAtual(),
                dataInicioUtc = existente?.dataInicioUtc,
                instrutorUuid = existente?.instrutorUuid
            )
        )
    }

    private data class DetalhePendencia(
        val tipo: String,
        val descricao: String?,
        val modulo: String?,
        val unidade: String?,
        val participantes: Int
    )

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::faceAttendance.isInitialized) faceAttendance.save(outState)
        if (metodoPresenca == "facial" && ::layoutLeituraNFC.isInitialized && layoutLeituraNFC.visibility == View.VISIBLE) {
            outState.putString("active_face_session", sessaoUuid)
        }
    }

    override fun onPause() {
        super.onPause()
        salvarAtividadeAtual()
        desativarNFC()
    }

    override fun onDestroy() {
        if (::networkMonitor.isInitialized) {
            networkMonitor.stop()
        }
        super.onDestroy()
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
        if (metodoPresenca != "nfc") return
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
        val unidadeUuid = unidadeUuidSelecionada ?: return
        if (!isOnline || !NetworkMonitor.isOnline(this)) {
            isOnline = false
            atualizarIndicadorConexao()
            registrarNfcSemValidacao(nfc)
            validarProximoNfcDaFila()
            return
        }
        val token = sessionManager.getTokenWithBearer() ?: return
        nfcPendente = nfc
        viewModel.validarNFC(token, nfc, unidadeUuid)
    }

    private fun registrarNfcSemValidacao(nfc: String) {
        val unidadeUuid = unidadeUuidSelecionada ?: return
        participantes.add(
            ParticipanteSessao(
                nfc = nfc,
                unidadeId = unidadeUuid,
                horarioRegistro = obterTimestampUtc(),
                numeroApontamento = apontamentoAtual
            )
        )
        nfcsRegistrados.add(nfc)
        salvarParticipantesAtividadeAtual()
        nfcPendente = null
        atualizarListaNFCs()
        Toast.makeText(this, "Crachá salvo offline.", Toast.LENGTH_SHORT).show()
    }

    private fun validarProximoNfcDaFila() {
        if (nfcPendente != null) return
        val proximo = filaNfcsPendentes.pollFirst() ?: return
        iniciarValidacaoNfc(proximo)
    }

    private fun atualizarListaNFCs() {
        if (metodoPresenca == "facial") return
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
        if (metodoPresenca != "nfc") return
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
        quantidadeApontamentosAtual = 1
        apontamentoAtual = 1
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

    private fun deveEnfileirarPorFalhaConexao(mensagem: String): Boolean {
        if (!NetworkMonitor.isOnline(this)) return true
        val normalizada = mensagem.lowercase(Locale.getDefault())
        return listOf(
            "conex",
            "connect",
            "timeout",
            "timed out",
            "failed to connect",
            "unable to resolve",
            "software caused connection abort",
            "network is unreachable",
            "no address associated"
        ).any { normalizada.contains(it) }
    }

    private fun deveRegistrarNfcOfflinePorFalhaConexao(mensagem: String): Boolean {
        val deveRegistrarOffline = deveEnfileirarPorFalhaConexao(mensagem)
        if (deveRegistrarOffline) {
            isOnline = false
            atualizarIndicadorConexao()
        }
        return deveRegistrarOffline
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
