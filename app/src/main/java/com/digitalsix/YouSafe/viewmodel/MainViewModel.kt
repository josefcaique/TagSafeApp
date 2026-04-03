package com.digitalsix.YouSafe.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.digitalsix.YouSafe.EmpresaComUnidades
import com.digitalsix.YouSafe.TipoModulo
import com.digitalsix.YouSafe.UnidadeInfo
import com.digitalsix.YouSafe.network.AbortarAulaRequest
import com.digitalsix.YouSafe.network.ConfirmarSessaoRequest
import com.digitalsix.YouSafe.network.GetFuncionarioByNFCResponse
import com.digitalsix.YouSafe.network.GinasticaLaboralRequest
import com.digitalsix.YouSafe.network.ParticipanteSessao
import com.digitalsix.YouSafe.network.UnidadeAtendidaDetalhe
import com.digitalsix.YouSafe.network.SessaoRequest
import com.digitalsix.YouSafe.network.TreinamentoRequest
import com.digitalsix.YouSafe.network.InstrutorUnidadesResponse
import com.digitalsix.YouSafe.network.TreinamentoResponse
import com.digitalsix.YouSafe.network.modulos.moduloResponse
import com.digitalsix.YouSafe.repository.ApiResult
import com.digitalsix.YouSafe.repository.MainRepository
import com.digitalsix.YouSafe.repository.SessaoCriada
import com.google.gson.JsonElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

data class MainUiState(
    val empresasMap: Map<String, EmpresaComUnidades> = emptyMap(),
    val modulos: List<moduloResponse> = emptyList(),
    val treinamentos: List<TreinamentoResponse> = emptyList(),
    val mensagemErro: String? = null,
    val carregandoEmpresas: Boolean = false,
    val carregandoModulos: Boolean = false,
    val carregandoTreinamentos: Boolean = false
)

sealed class AcaoState {
    object Idle : AcaoState()
    object Loading : AcaoState()
    data class Sucesso(val sessaoId: Int? = null) : AcaoState()
    data class Erro(val mensagem: String) : AcaoState()
}

class MainViewModel(private val repository: MainRepository) : ViewModel() {
    private val _uiState = MutableLiveData(MainUiState())
    val uiState: LiveData<MainUiState> = _uiState

    private val _criarSessaoState = MutableLiveData<AcaoState>(AcaoState.Idle)
    val criarSessaoState: LiveData<AcaoState> = _criarSessaoState

    private val _confirmarSessaoState = MutableLiveData<AcaoState>(AcaoState.Idle)
    val confirmarSessaoState: LiveData<AcaoState> = _confirmarSessaoState

    private val _abortarSessaoState = MutableLiveData<AcaoState>(AcaoState.Idle)
    val abortarSessaoState: LiveData<AcaoState> = _abortarSessaoState

    private val _validarNFCState = MutableLiveData<AcaoState>(AcaoState.Idle)
    val validarNFCState: LiveData<AcaoState> = _validarNFCState

    private val _funcionarioValidado = MutableLiveData<GetFuncionarioByNFCResponse?>(null)
    val funcionarioValidado: LiveData<GetFuncionarioByNFCResponse?> = _funcionarioValidado

    private var treinamentosUnidadeId: Int? = null
    private var carregandoTreinamentos = false

    fun carregarEmpresasEUnidades(token: String, instrutorId: Int) {
        updateState { it.copy(carregandoEmpresas = true, mensagemErro = null) }
        viewModelScope.launch {
            when (val result = repository.getUnidadesAtendidas(token, instrutorId)) {
                is ApiResult.Success -> {
                    val unidades = result.data.unidadesAtendidas
                    if (unidades.isEmpty()) {
                        updateState {
                            it.copy(
                                empresasMap = emptyMap(),
                                carregandoEmpresas = false,
                                mensagemErro = "Nenhuma unidade encontrada para este instrutor"
                            )
                        }
                        return@launch
                    }

                    val empresasMap = unidades
                        .groupBy { it.empresa }
                        .mapValues { (empresaNome, unidadesEmpresa) ->
                            EmpresaComUnidades(
                                empresaNome = empresaNome,
                                unidades = unidadesEmpresa.map {
                                    UnidadeInfo(
                                        unidadeId = it.id,
                                        unidadeNome = it.nome,
                                        modulos = it.modulos
                                    )
                                }.sortedBy { it.unidadeNome }
                            )
                        }

                    updateState {
                        it.copy(
                            empresasMap = empresasMap,
                            carregandoEmpresas = false,
                            mensagemErro = null
                        )
                    }
                }
                is ApiResult.Error -> {
                    Log.e("EMPRESAS", "Erro na API: code=${result.code} | message=${result.message}")
                    updateState {
                        it.copy(
                            carregandoEmpresas = false,
                            mensagemErro = result.message
                        )
                    }
                }
            }
        }
    }

    fun definirModulos(modulos: List<moduloResponse>) {
        updateState { it.copy(modulos = modulos) }
    }

    fun carregarTreinamentosSeNecessario(token: String, unidadeId: Int) {
        if (carregandoTreinamentos) return
        val treinamentosAtual = _uiState.value?.treinamentos.orEmpty()
        if (treinamentosUnidadeId == unidadeId && treinamentosAtual.isNotEmpty()) return
        carregarTreinamentos(token, unidadeId)
    }

    fun limparTreinamentos() {
        treinamentosUnidadeId = null
        updateState { it.copy(treinamentos = emptyList()) }
    }

    fun limparModulos() {
        updateState { it.copy(modulos = emptyList()) }
    }

    fun limparErro() {
        updateState { it.copy(mensagemErro = null) }
    }

    fun criarSessao(
        token: String,
        tipoModulo: TipoModulo,
        nomeTreinamento: String,
        descricao: String,
        unidadeId: Int,
        instrutorId: Int,
        moduloId: Int,
        treinamentoSelecionadoId: Int?
    ) {
        _criarSessaoState.value = AcaoState.Loading
        viewModelScope.launch {
            val treinamentoId: Int?
            val ginasticaLaboralId: Int?

            when (tipoModulo) {
                TipoModulo.GINASTICA_LABORAL -> {
                    val result = repository.criarGinasticaLaboral(
                        token,
                        GinasticaLaboralRequest(descricao = descricao)
                    )
                    when (result) {
                        is ApiResult.Success -> {
                            val id = result.data.id
                            if (id == null) {
                                _criarSessaoState.value =
                                    AcaoState.Erro("Resposta sem ID da ginastica laboral")
                                return@launch
                            }
                            ginasticaLaboralId = id
                            treinamentoId = null
                        }
                        is ApiResult.Error -> {
                            _criarSessaoState.value = AcaoState.Erro(result.message)
                            return@launch
                        }
                    }
                }
                TipoModulo.TREINAMENTO -> {
                    val existenteId = treinamentoSelecionadoId
                    if (existenteId != null) {
                        treinamentoId = existenteId
                        ginasticaLaboralId = null
                    } else {
                        val result = repository.criarTreinamento(
                            token,
                            TreinamentoRequest(
                                nome = nomeTreinamento,
                                descricao = descricao,
                                cargaHoraria = 0,
                                validadeMeses = 0,
                                obrigatorio = false,
                                unidadeId = unidadeId,
                                categoriasId = emptyList()
                            )
                        )
                        when (result) {
                            is ApiResult.Success -> {
                                val id = result.data.id
                                if (id == null) {
                                    _criarSessaoState.value =
                                        AcaoState.Erro("Resposta sem ID do treinamento")
                                    return@launch
                                }
                                treinamentoId = id
                                ginasticaLaboralId = null
                            }
                            is ApiResult.Error -> {
                                _criarSessaoState.value = AcaoState.Erro(result.message)
                                return@launch
                            }
                        }
                    }
                }
                TipoModulo.DESCONHECIDO -> {
                    _criarSessaoState.value = AcaoState.Erro("Modulo nao suportado")
                    return@launch
                }
            }

            val request = SessaoRequest(
                unidadeId = unidadeId,
                instrutorId = instrutorId,
                statusId = 1,
                dataInicio = obterTimestampUtc(),
                treinamentoId = treinamentoId,
                ginasticaLaboralId = ginasticaLaboralId,
                moduloId = moduloId,
                observacoes = descricao.ifBlank { "" }
            )

            when (val result = repository.criarSessao(token, request)) {
                is ApiResult.Success -> {
                    val sessaoId = extrairIdSessao(result.data)
                    if (sessaoId == null) {
                        _criarSessaoState.value = AcaoState.Erro("Resposta sem ID da sessao")
                    } else {
                        _criarSessaoState.value = AcaoState.Sucesso(sessaoId)
                    }
                }
                is ApiResult.Error -> {
                    _criarSessaoState.value = AcaoState.Erro(result.message)
                }
            }
        }
    }

    fun confirmarSessao(
        token: String,
        sessaoId: Int,
        dataFim: String,
        participantes: List<ParticipanteSessao>
    ) {
        _confirmarSessaoState.value = AcaoState.Loading
        viewModelScope.launch {
            val request = ConfirmarSessaoRequest(
                dataFim = dataFim,
                participantes = participantes
            )
            when (val result = repository.confirmarSessao(token, sessaoId, request)) {
                is ApiResult.Success -> _confirmarSessaoState.value = AcaoState.Sucesso()
                is ApiResult.Error -> _confirmarSessaoState.value = AcaoState.Erro(result.message)
            }
        }
    }

    fun abortarSessao(
        token: String,
        sessaoId: Int,
        dataFim: String,
        participantes: List<ParticipanteSessao>
    ) {
        _abortarSessaoState.value = AcaoState.Loading
        viewModelScope.launch {
            val request = AbortarAulaRequest(
                dataFim = dataFim,
                participantes = participantes
            )
            when (val result = repository.abortarSessao(token, sessaoId, request)) {
                is ApiResult.Success -> _abortarSessaoState.value = AcaoState.Sucesso()
                is ApiResult.Error -> _abortarSessaoState.value = AcaoState.Erro(result.message)
            }
        }
    }

    fun consumirCriarSessaoEstado() {
        _criarSessaoState.value = AcaoState.Idle
    }

    fun consumirConfirmarSessaoEstado() {
        _confirmarSessaoState.value = AcaoState.Idle
    }

    fun consumirAbortarSessaoEstado() {
        _abortarSessaoState.value = AcaoState.Idle
    }

    fun validarNFC(token: String, nfc: String, unidadeId: Int) {
        _validarNFCState.value = AcaoState.Loading
        viewModelScope.launch {
            when (val result = repository.getFuncionarioByNFC(token, nfc, unidadeId)) {
                is ApiResult.Success -> {
                    _funcionarioValidado.value = result.data
                    _validarNFCState.value = AcaoState.Sucesso()
                }
                is ApiResult.Error -> {
                    _funcionarioValidado.value = null
                    _validarNFCState.value = AcaoState.Erro(result.message)
                }
            }
        }
    }

    fun consumirValidarNFCEstado() {
        _validarNFCState.value = AcaoState.Idle
        _funcionarioValidado.value = null
    }

    private fun carregarTreinamentos(token: String, unidadeId: Int) {
        viewModelScope.launch {
            carregandoTreinamentos = true
            updateState { it.copy(carregandoTreinamentos = true, mensagemErro = null) }
            when (val result = repository.getTreinamentos(token, unidadeId)) {
                is ApiResult.Success -> {
                    treinamentosUnidadeId = unidadeId
                    updateState {
                        it.copy(
                            treinamentos = result.data,
                            carregandoTreinamentos = false
                        )
                    }
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            treinamentos = emptyList(),
                            carregandoTreinamentos = false,
                            mensagemErro = result.message
                        )
                    }
                }
            }
            carregandoTreinamentos = false
        }
    }

    private fun updateState(update: (MainUiState) -> MainUiState) {
        val current = _uiState.value ?: MainUiState()
        _uiState.value = update(current)
    }

    private fun extrairIdSessao(sessaoCriada: SessaoCriada): Int? {
        return extrairIdSessao(sessaoCriada.body)
            ?: extrairIdSessaoDeLocation(sessaoCriada.location)
    }

    private fun extrairIdSessao(body: JsonElement?): Int? {
        if (body == null) return null
        if (body.isJsonPrimitive) {
            val prim = body.asJsonPrimitive
            return if (prim.isNumber) prim.asInt else prim.asString.toIntOrNull()
        }
        if (!body.isJsonObject) return null

        val obj = body.asJsonObject
        val keys = listOf(
            "id",
            "sessao_id",
            "sessaoId",
            "id_sessao",
            "session_id",
            "sessionId",
            "aula_id"
        )
        for (key in keys) {
            if (obj.has(key)) {
                val id = obj.get(key)
                if (id.isJsonPrimitive) {
                    val prim = id.asJsonPrimitive
                    val value = if (prim.isNumber) prim.asInt else prim.asString.toIntOrNull()
                    if (value != null) return value
                }
            }
        }

        if (obj.has("sessao") && obj.get("sessao").isJsonObject) {
            return extrairIdSessao(obj.get("sessao"))
        }

        return null
    }

    private fun extrairIdSessaoDeLocation(locationHeader: String?): Int? {
        if (locationHeader.isNullOrBlank()) return null
        val match = Regex(".*/(\\d+)$").find(locationHeader)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun obterTimestampUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}

class MainViewModelFactory(
    private val repository: MainRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
