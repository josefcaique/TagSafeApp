package com.digitalsix.YouSafe.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.digitalsix.YouSafe.network.AbortarAulaRequest
import com.digitalsix.YouSafe.network.ConfirmarSessaoRequest
import com.digitalsix.YouSafe.network.GinasticaLaboralResponse
import com.digitalsix.YouSafe.network.ParticipanteSessao
import com.digitalsix.YouSafe.network.SincronizarSessaoRequest
import com.digitalsix.YouSafe.network.TreinamentoResponse
import com.digitalsix.YouSafe.network.Usuario
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.util.UUID

data class AtividadeEmProgresso(
    val uuid: String,
    val inicioMs: Long,
    val unidadeUuid: String?,
    val unidadeNome: String?,
    val moduloNome: String?,
    val participantes: List<ParticipanteSessao> = emptyList(),
    val quantidadeApontamentos: Int = 1,
    val apontamentoAtual: Int = 1,
    val offline: Boolean = false,
    val moduloUuid: String? = null,
    val treinamentoUuid: String? = null,
    val ginasticaLaboralUuid: String? = null,
    val observacoes: String? = null,
    val dataInicioUtc: String? = null,
    val instrutorUuid: String? = null
)

data class SessaoOfflinePendente(
    val localId: String,
    val criadaEmMs: Long,
    val instrutorUuid: String? = null,
    val descricao: String? = null,
    val moduloNome: String? = null,
    val unidadeNome: String? = null,
    val request: SincronizarSessaoRequest
)

data class AcaoSessaoPendente(
    val localId: String,
    val sessaoUuid: String,
    val tipo: String,
    val criadaEmMs: Long,
    val instrutorUuid: String? = null,
    val descricao: String? = null,
    val moduloNome: String? = null,
    val unidadeNome: String? = null,
    val confirmarRequest: ConfirmarSessaoRequest? = null,
    val abortarRequest: AbortarAulaRequest? = null
)

/**
 * Gerenciador de sessão do usuário
 * Armazena token JWT e dados do usuário logado
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "yousafe_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_REFRESH_EXPIRES_AT = "refresh_expires_at"
        private const val KEY_USUARIO = "usuario"
        private const val KEY_LOGGED_IN = "is_logged_in"
        private const val KEY_AULA_EM_PROGRESSO = "aula_em_progresso_uuid"
        private const val KEY_UNIDADE_EM_PROGRESSO = "unidade_em_progresso_uuid"
        private const val KEY_MODULO_EM_PROGRESSO = "modulo_em_progresso"
        private const val KEY_AULA_INICIO_MS = "aula_inicio_ms"
        private const val KEY_ATIVIDADES_EM_PROGRESSO = "atividades_em_progresso"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TREINAMENTOS_CACHE = "treinamentos_cache"
        private const val KEY_GINASTICAS_LABORAIS_CACHE = "ginasticas_laborais_cache"
        private const val KEY_SESSOES_OFFLINE_PENDENTES = "sessoes_offline_pendentes"
        private const val KEY_ACOES_SESSAO_PENDENTES = "acoes_sessao_pendentes"
    }

    /**
     * Salvar sessão após login bem-sucedido
     */
    fun saveSession(
        token: String,
        usuario: Usuario,
        refreshToken: String? = null,
        refreshExpiresAt: String? = null
    ) {
        val editor = prefs.edit()
        editor.putString(KEY_TOKEN, token)
        editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        editor.putString(KEY_REFRESH_EXPIRES_AT, refreshExpiresAt)
        editor.putString(KEY_USUARIO, gson.toJson(usuario))
        editor.putBoolean(KEY_LOGGED_IN, true)
        editor.apply()
    }

    /**
     * Verificar se está logado
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_LOGGED_IN, false)
    }

    /**
     * Obter token JWT
     */
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getRefreshExpiresAt(): String? {
        return prefs.getString(KEY_REFRESH_EXPIRES_AT, null)
    }

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val deviceId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        return deviceId
    }

    fun atualizarTokens(
        token: String,
        refreshToken: String? = null,
        refreshExpiresAt: String? = null
    ) {
        val editor = prefs.edit()
        editor.putString(KEY_TOKEN, token)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        if (!refreshExpiresAt.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_EXPIRES_AT, refreshExpiresAt)
        }
        editor.putBoolean(KEY_LOGGED_IN, true)
        editor.apply()
    }

    /**
     * Obter token JWT com prefixo Bearer
     * Usado para requisições autenticadas
     */
    fun getTokenWithBearer(): String? {
        val token = getToken()
        return if (token != null) "Bearer $token" else null
    }

    fun isAccessTokenValid(clockSkewSeconds: Long = 60): Boolean {
        val token = getToken()?.takeIf { it.isNotBlank() } ?: return false
        val expiresAtSeconds = getJwtExpirationSeconds(token) ?: return false
        val nowSeconds = System.currentTimeMillis() / 1000
        return expiresAtSeconds > nowSeconds + clockSkewSeconds
    }

    private fun getJwtExpirationSeconds(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val payloadJson = String(
                Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                ),
                Charsets.UTF_8
            )
            JSONObject(payloadJson).optLong("exp").takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obter usuário logado
     */
    fun getUsuario(): Usuario? {
        val usuarioJson = prefs.getString(KEY_USUARIO, null) ?: return null
        return try {
            gson.fromJson(usuarioJson, Usuario::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun salvarTreinamentosCache(unidadeUuid: String, treinamentos: List<TreinamentoResponse>) {
        if (unidadeUuid.isBlank()) return
        val cache = getTreinamentosCacheCompleto().toMutableMap()
        cache[unidadeUuid] = treinamentos
        prefs.edit().putString(KEY_TREINAMENTOS_CACHE, gson.toJson(cache)).apply()
    }

    fun getTreinamentosCache(unidadeUuid: String): List<TreinamentoResponse> {
        return getTreinamentosCacheCompleto()[unidadeUuid].orEmpty()
    }

    private fun getTreinamentosCacheCompleto(): Map<String, List<TreinamentoResponse>> {
        val json = prefs.getString(KEY_TREINAMENTOS_CACHE, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, List<TreinamentoResponse>>>() {}.type
            gson.fromJson<Map<String, List<TreinamentoResponse>>>(json, type).orEmpty()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun salvarGinasticasLaboraisCache(unidadeUuid: String, ginasticas: List<GinasticaLaboralResponse>) {
        if (unidadeUuid.isBlank()) return
        val cache = getGinasticasLaboraisCacheCompleto().toMutableMap()
        cache[unidadeUuid] = ginasticas
        prefs.edit().putString(KEY_GINASTICAS_LABORAIS_CACHE, gson.toJson(cache)).apply()
    }

    fun getGinasticasLaboraisCache(unidadeUuid: String? = null): List<GinasticaLaboralResponse> {
        return unidadeUuid
            ?.takeIf { it.isNotBlank() }
            ?.let { getGinasticasLaboraisCacheCompleto()[it] }
            .orEmpty()
    }

    private fun getGinasticasLaboraisCacheCompleto(): Map<String, List<GinasticaLaboralResponse>> {
        val json = prefs.getString(KEY_GINASTICAS_LABORAIS_CACHE, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, List<GinasticaLaboralResponse>>>() {}.type
            gson.fromJson<Map<String, List<GinasticaLaboralResponse>>>(json, type).orEmpty()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun salvarSessaoOfflinePendente(sessao: SessaoOfflinePendente) {
        val pendentes = getSessoesOfflinePendentes()
            .filterNot { it.localId == sessao.localId }
            .plus(sessao)
            .sortedBy { it.criadaEmMs }
        prefs.edit()
            .putString(KEY_SESSOES_OFFLINE_PENDENTES, gson.toJson(pendentes))
            .apply()
    }

    fun getSessoesOfflinePendentes(): List<SessaoOfflinePendente> {
        val json = prefs.getString(KEY_SESSOES_OFFLINE_PENDENTES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SessaoOfflinePendente>>() {}.type
            gson.fromJson<List<SessaoOfflinePendente>>(json, type).orEmpty()
                .filter { it.localId.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSessoesOfflinePendentesDoUsuario(): List<SessaoOfflinePendente> {
        val instrutorUuid = getUsuario()?.instrutorUuid
        return getSessoesOfflinePendentes().filter {
            it.instrutorUuid.isNullOrBlank() || it.instrutorUuid == instrutorUuid
        }
    }

    fun removerSessaoOfflinePendente(localId: String) {
        val pendentes = getSessoesOfflinePendentes().filterNot { it.localId == localId }
        prefs.edit()
            .putString(KEY_SESSOES_OFFLINE_PENDENTES, gson.toJson(pendentes))
            .apply()
    }

    fun salvarAcaoSessaoPendente(acao: AcaoSessaoPendente) {
        val pendentes = getAcoesSessaoPendentes()
            .filterNot { it.localId == acao.localId }
            .plus(acao)
            .sortedBy { it.criadaEmMs }
        prefs.edit()
            .putString(KEY_ACOES_SESSAO_PENDENTES, gson.toJson(pendentes))
            .apply()
    }

    fun getAcoesSessaoPendentes(): List<AcaoSessaoPendente> {
        val json = prefs.getString(KEY_ACOES_SESSAO_PENDENTES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AcaoSessaoPendente>>() {}.type
            gson.fromJson<List<AcaoSessaoPendente>>(json, type).orEmpty()
                .filter { it.localId.isNotBlank() && it.sessaoUuid.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAcoesSessaoPendentesDoUsuario(): List<AcaoSessaoPendente> {
        val instrutorUuid = getUsuario()?.instrutorUuid
        return getAcoesSessaoPendentes().filter {
            it.instrutorUuid.isNullOrBlank() || it.instrutorUuid == instrutorUuid
        }
    }

    fun removerAcaoSessaoPendente(localId: String) {
        val pendentes = getAcoesSessaoPendentes().filterNot { it.localId == localId }
        prefs.edit()
            .putString(KEY_ACOES_SESSAO_PENDENTES, gson.toJson(pendentes))
            .apply()
    }

    /**
     * Atualizar dados do usuário (para refresh de unidades atendidas)
     */
    fun atualizarUsuario(usuario: Usuario) {
        val editor = prefs.edit()
        editor.putString(KEY_USUARIO, gson.toJson(usuario))
        editor.apply()
    }

    /**
     * Atualizar flag de primeiro acesso
     * Chamado após usuário redefinir senha
     */
    fun atualizarPrimeiroAcesso(primeiroAcesso: Boolean) {
        val usuario = getUsuario() ?: return
        val usuarioAtualizado = usuario.copy(primeiroAcesso = primeiroAcesso)

        val editor = prefs.edit()
        editor.putString(KEY_USUARIO, gson.toJson(usuarioAtualizado))
        editor.apply()
    }

    /**
     * Limpar sessão (logout)
     */
    fun clearSession() {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        val sessoesPendentes = prefs.getString(KEY_SESSOES_OFFLINE_PENDENTES, null)
        val acoesPendentes = prefs.getString(KEY_ACOES_SESSAO_PENDENTES, null)
        val atividadesOffline = getAtividadesEmProgresso().filter { it.offline }
        val editor = prefs.edit()
        editor.clear()
        if (!deviceId.isNullOrBlank()) {
            editor.putString(KEY_DEVICE_ID, deviceId)
        }
        if (!sessoesPendentes.isNullOrBlank()) {
            editor.putString(KEY_SESSOES_OFFLINE_PENDENTES, sessoesPendentes)
        }
        if (!acoesPendentes.isNullOrBlank()) {
            editor.putString(KEY_ACOES_SESSAO_PENDENTES, acoesPendentes)
        }
        if (atividadesOffline.isNotEmpty()) {
            editor.putString(KEY_ATIVIDADES_EM_PROGRESSO, gson.toJson(atividadesOffline))
        }
        editor.apply()
    }

    /**
     * Obter nome do usuário
     */
    fun getNomeUsuario(): String {
        return getUsuario()?.nome ?: "Usuário"
    }

    /**
     * Obter email do usuário
     */
    fun getEmailUsuario(): String {
        return getUsuario()?.email ?: ""
    }

    // ==========================================
    // CRASH RECOVERY - GERENCIAMENTO DE AULA EM PROGRESSO
    // ==========================================

    /**
     * Salvar UUID da aula em progresso
     * Chamado quando instrutor inicia uma aula
     */
    fun salvarAulaEmProgresso(aulaUuid: String) {
        val editor = prefs.edit()
        editor.putString(KEY_AULA_EM_PROGRESSO, aulaUuid)
        editor.apply()
    }

    fun salvarAtividadeEmProgresso(atividade: AtividadeEmProgresso) {
        val atividades = getAtividadesEmProgresso()
            .filterNot { it.uuid == atividade.uuid }
            .plus(atividade)
            .sortedByDescending { it.inicioMs }
        prefs.edit()
            .putString(KEY_ATIVIDADES_EM_PROGRESSO, gson.toJson(atividades))
            .apply()
    }

    fun getAtividadesEmProgresso(): List<AtividadeEmProgresso> {
        val atividadesJson = prefs.getString(KEY_ATIVIDADES_EM_PROGRESSO, null)
        val atividades = if (atividadesJson.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<AtividadeEmProgresso>>() {}.type
                gson.fromJson<List<AtividadeEmProgresso>>(atividadesJson, type).orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
        }

        if (atividades.isNotEmpty()) {
            val instrutorUuid = getUsuario()?.instrutorUuid
            return atividades
                .filter {
                    it.uuid.isNotBlank() &&
                        (it.instrutorUuid.isNullOrBlank() || it.instrutorUuid == instrutorUuid)
                }
                .map { atividade ->
                    val quantidadeApontamentos = atividade.quantidadeApontamentos
                        .takeIf { it in 1..2 }
                        ?: 1
                    val apontamentoAtual = atividade.apontamentoAtual
                        .takeIf { it in 1..quantidadeApontamentos }
                        ?: 1
                    atividade.copy(
                        participantes = atividade.participantes.orEmpty(),
                        quantidadeApontamentos = quantidadeApontamentos,
                        apontamentoAtual = apontamentoAtual
                    )
                }
        }

        val aulaUuid = getAulaEmProgresso() ?: return emptyList()
        val inicioMs = getInicioAulaEmProgresso() ?: return emptyList()
        return listOf(
            AtividadeEmProgresso(
                uuid = aulaUuid,
                inicioMs = inicioMs,
                unidadeUuid = getUnidadeEmProgresso(),
                unidadeNome = null,
                moduloNome = getModuloEmProgresso()
            )
        )
    }

    fun getAtividadeEmProgresso(uuid: String): AtividadeEmProgresso? {
        return getAtividadesEmProgresso().firstOrNull { it.uuid == uuid }
    }

    fun atualizarParticipantesAtividade(uuid: String, participantes: List<ParticipanteSessao>) {
        val atividade = getAtividadeEmProgresso(uuid) ?: return
        salvarAtividadeEmProgresso(atividade.copy(participantes = participantes))
    }

    fun removerAtividadeEmProgresso(uuid: String) {
        val atividades = getAtividadesEmProgresso().filterNot { it.uuid == uuid }
        prefs.edit()
            .putString(KEY_ATIVIDADES_EM_PROGRESSO, gson.toJson(atividades))
            .apply()

        if (getAulaEmProgresso() == uuid) {
            limparAulaEmProgresso()
            limparUnidadeEmProgresso()
            limparModuloEmProgresso()
        }
    }

    fun salvarInicioAulaEmProgresso(inicioMs: Long) {
        val editor = prefs.edit()
        editor.putLong(KEY_AULA_INICIO_MS, inicioMs)
        editor.apply()
    }

    /**
     * Obter UUID da aula em progresso (se existir)
     * Retorna null se não houver aula em progresso
     */
    fun getAulaEmProgresso(): String? {
        return prefs.getString(KEY_AULA_EM_PROGRESSO, null)
    }

    fun getInicioAulaEmProgresso(): Long? {
        val inicioMs = prefs.getLong(KEY_AULA_INICIO_MS, -1L)
        return if (inicioMs == -1L) null else inicioMs
    }

    fun salvarUnidadeEmProgresso(unidadeUuid: String) {
        val editor = prefs.edit()
        editor.putString(KEY_UNIDADE_EM_PROGRESSO, unidadeUuid)
        editor.apply()
    }

    fun getUnidadeEmProgresso(): String? {
        return prefs.getString(KEY_UNIDADE_EM_PROGRESSO, null)
    }

    fun salvarModuloEmProgresso(moduloNome: String?) {
        val nome = moduloNome?.trim()
        val editor = prefs.edit()
        if (nome.isNullOrEmpty()) {
            editor.remove(KEY_MODULO_EM_PROGRESSO)
        } else {
            editor.putString(KEY_MODULO_EM_PROGRESSO, nome)
        }
        editor.apply()
    }

    fun getModuloEmProgresso(): String? {
        return prefs.getString(KEY_MODULO_EM_PROGRESSO, null)
    }

    /**
     * Limpar aula em progresso
     * Chamado quando aula é confirmada ou abortada
     */
    fun limparAulaEmProgresso() {
        val editor = prefs.edit()
        editor.remove(KEY_AULA_EM_PROGRESSO)
        editor.remove(KEY_AULA_INICIO_MS)
        editor.apply()
    }

    fun limparUnidadeEmProgresso() {
        val editor = prefs.edit()
        editor.remove(KEY_UNIDADE_EM_PROGRESSO)
        editor.apply()
    }

    fun limparModuloEmProgresso() {
        val editor = prefs.edit()
        editor.remove(KEY_MODULO_EM_PROGRESSO)
        editor.apply()
    }

    /**
     * Verificar se existe aula em progresso
     */
    fun temAulaEmProgresso(): Boolean {
        return getAtividadesEmProgresso().isNotEmpty()
    }
}
