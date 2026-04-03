package com.digitalsix.YouSafe.utils

import android.content.Context
import android.content.SharedPreferences
import com.digitalsix.YouSafe.network.Usuario
import com.google.gson.Gson

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
        private const val KEY_AULA_EM_PROGRESSO = "aula_em_progresso"
        private const val KEY_UNIDADE_EM_PROGRESSO = "unidade_em_progresso"
        private const val KEY_MODULO_EM_PROGRESSO = "modulo_em_progresso"
        private const val KEY_AULA_INICIO_MS = "aula_inicio_ms"
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

    /**
     * Obter token JWT com prefixo Bearer
     * Usado para requisições autenticadas
     */
    fun getTokenWithBearer(): String? {
        val token = getToken()
        return if (token != null) "Bearer $token" else null
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
        val editor = prefs.edit()
        editor.clear()
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
     * Salvar ID da aula em progresso
     * Chamado quando instrutor inicia uma aula
     */
    fun salvarAulaEmProgresso(aulaId: Int) {
        val editor = prefs.edit()
        editor.putInt(KEY_AULA_EM_PROGRESSO, aulaId)
        editor.apply()
    }

    fun salvarInicioAulaEmProgresso(inicioMs: Long) {
        val editor = prefs.edit()
        editor.putLong(KEY_AULA_INICIO_MS, inicioMs)
        editor.apply()
    }

    /**
     * Obter ID da aula em progresso (se existir)
     * Retorna null se não houver aula em progresso
     */
    fun getAulaEmProgresso(): Int? {
        val aulaId = prefs.getInt(KEY_AULA_EM_PROGRESSO, -1)
        return if (aulaId == -1) null else aulaId
    }

    fun getInicioAulaEmProgresso(): Long? {
        val inicioMs = prefs.getLong(KEY_AULA_INICIO_MS, -1L)
        return if (inicioMs == -1L) null else inicioMs
    }

    fun salvarUnidadeEmProgresso(unidadeId: Int) {
        val editor = prefs.edit()
        editor.putInt(KEY_UNIDADE_EM_PROGRESSO, unidadeId)
        editor.apply()
    }

    fun getUnidadeEmProgresso(): Int? {
        val unidadeId = prefs.getInt(KEY_UNIDADE_EM_PROGRESSO, -1)
        return if (unidadeId == -1) null else unidadeId
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
        return getAulaEmProgresso() != null
    }
}
