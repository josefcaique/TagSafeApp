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
import com.digitalsix.YouSafe.network.UnidadeAtendida
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
    data class Sucesso(val sessaoId: String? = null) : AcaoState()
    data class Erro(val mensagem: String) : AcaoState()
}

class MainViewModel(private val repository: MainRepository) : ViewModel() {
    private companion object {
        const val STATUS_EM_ANDAMENTO_UUID = "fc0fb24e-3dba-4814-8d03-6daf3a4d467b"
    }

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

    private var treinamentosUnidadeId: String? = null
    private var carregandoTreinamentos = false

    fun carregarEmpresasEUnidades(
        token: String,
        instrutorId: String,
        unidadesDoLogin: List<UnidadeAtendida> = emptyList()
    ) {
        updateState { it.copy(carregandoEmpresas = true, mensagemErro = null) }
        viewModelScope.launch {
            when (val result = repository.getUnidadesAtendidas(token, instrutorId)) {
                is ApiResult.Success -> {
                    val empresasMap = montarEmpresasMap(result.data.unidades.orEmpty())
                        .ifEmpty { montarEmpresasMap(unidadesDoLogin.map { it.toDetalhe() }) }

                    if (empresasMap.isEmpty()) {
                        updateState {
                            it.copy(
                                empresasMap = emptyMap(),
                                carregandoEmpresas = false,
                                mensagemErro = "Nenhuma unidade com UUID encontrada para este instrutor"
                            )
                        }
                        return@launch
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
                    val empresasMap = montarEmpresasMap(unidadesDoLogin.map { it.toDetalhe() })
                    if (empresasMap.isNotEmpty()) {
                        updateState {
                            it.copy(
                                empresasMap = empresasMap,
                                carregandoEmpresas = false,
                                mensagemErro = null
                            )
                        }
                    } else {
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
    }

    private fun montarEmpresasMap(
        unidades: List<UnidadeAtendidaDetalhe>
    ): Map<String, EmpresaComUnidades> {
        return unidades
            .filter { !it.publicId.isNullOrBlank() }
            .groupBy { it.empresa ?: "Empresa" }
            .mapValues { (empresaNome, unidadesEmpresa) ->
                EmpresaComUnidades(
                    empresaNome = empresaNome,
                    unidades = unidadesEmpresa.map {
                        UnidadeInfo(
                            unidadeId = it.id ?: 0,
                            publicId = it.publicId.orEmpty(),
                            unidadeNome = it.nome.orEmpty(),
                            modulos = it.modulos.orEmpty().filter { modulo ->
                                !modulo.publicId.isNullOrBlank()
                            }
                        )
                    }.sortedBy { it.unidadeNome }
                )
            }
    }

    private fun UnidadeAtendida.toDetalhe(): UnidadeAtendidaDetalhe {
        return UnidadeAtendidaDetalhe(
            id = id,
            publicId = publicId,
            nome = nome,
            empresa = empresa,
            modulos = modulos
        )
    }

    fun definirModulos(modulos: List<moduloResponse>) {
        updateState { it.copy(modulos = modulos) }
    }

    fun carregarTreinamentosSeNecessario(token: String, unidadeId: String) {
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
        unidadeId: String,
        instrutorId: String,
        moduloId: String,
        treinamentoSelecionadoId: String?
    ) {
        _criarSessaoState.value = AcaoState.Loading
        viewModelScope.launch {
            val treinamentoId: String?
            val ginasticaLaboralId: String?

            when (tipoModulo) {
                TipoModulo.GINASTICA_LABORAL -> {
                    // Nota: O guia não detalhou POST /ginastica-laboral com UUID,
                    // mas seguindo a lógica de outros campos de unidade_id
                    val result = repository.criarGinasticaLaboral(
                        token,
                        GinasticaLaboralRequest(descricao = descricao)
                    )
                    when (result) {
                        is ApiResult.Success -> {
                            val id = result.data.publicId ?: result.data.id
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
                                categorias = emptyList()
                            )
                        )
                        when (result) {
                            is ApiResult.Success -> {
                                val id = result.data.publicId ?: result.data.id
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
                statusId = STATUS_EM_ANDAMENTO_UUID,
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
        sessaoId: String,
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
        sessaoId: String,
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

    fun validarNFC(token: String, nfc: String, unidadeId: String) {
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

    fun carregarTreinamentos(token: String, unidadeId: String) {
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

    private fun extrairIdSessao(sessaoCriada: SessaoCriada): String? {
        return extrairIdSessao(sessaoCriada.body)
            ?: extrairIdSessaoDeLocation(sessaoCriada.location)
    }

    private fun extrairIdSessao(body: JsonElement?): String? {
        if (body == null) return null
        if (body.isJsonPrimitive) {
            val prim = body.asJsonPrimitive
            return prim.asString
        }
        if (!body.isJsonObject) return null

        val obj = body.asJsonObject
        val keys = listOf(
            "public_id",
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
                    return id.asJsonPrimitive.asString
                }
            }
        }

        if (obj.has("sessao") && obj.get("sessao").isJsonObject) {
            return extrairIdSessao(obj.get("sessao"))
        }

        return null
    }

    private fun extrairIdSessaoDeLocation(locationHeader: String?): String? {
        if (locationHeader.isNullOrBlank()) return null
        // Tenta capturar UUID ou ID numérico no final da URL
        val match = Regex(".*/([a-f0-9\\-]+|\\d+)$").find(locationHeader)
        return match?.groupValues?.get(1)
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
