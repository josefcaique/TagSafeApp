package com.digitalsix.YouSafe.network

import com.digitalsix.YouSafe.network.modulos.moduloResponse
import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // ==========================================
    // ROTAS DE AUTENTICAÇÃO
    // ==========================================
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<LoginResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<ForgotPasswordResponse>

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<Any>

    @POST("auth/resetSenha/{id}")
    suspend fun resetPassword(
        @Header("Authorization") token: String,
        @Path("id") userId: Int,
        @Body request: ResetPasswordRequest
    ): Response<ResetPasswordResponse>

    // ==========================================
    // ROTAS DE GINASTICA, TREINAMENTOS E SESSOES
    // ==========================================

    @POST("ginastica-laboral")
    suspend fun criarGinasticaLaboral(
        @Header("Authorization") token: String,
        @Body request: GinasticaLaboralRequest
    ): Response<IdResponse>

    @POST("treinamentos")
    suspend fun criarTreinamento(
        @Header("Authorization") token: String,
        @Body request: TreinamentoRequest
    ): Response<IdResponse>

    @POST("sessoes")
    suspend fun criarSessao(
        @Header("Authorization") token: String,
        @Body request: SessaoRequest
    ): Response<JsonElement>

    @POST("sessoes/{id}/confirmar")
    suspend fun confirmarSessao(
        @Header("Authorization") token: String,
        @Path("id") aulaId: Int,
        @Body request: ConfirmarSessaoRequest
    ): Response<Any>

    @POST("sessoes/{id}/abortar")
    suspend fun abortarSessao(
        @Header("Authorization") token: String,
        @Path("id") aulaId: Int,
        @Body request: AbortarAulaRequest
    ): Response<AbortarAulaResponse>

    // ==========================================
    // ROTAS SEM AUTENTICAÇÃO (Módulos, Tipos, etc)
    // ==========================================
    @GET("tipos-aula")
    suspend fun getTiposAula(): Response<List<TipoAula>>

    @GET("modulos/instrutor/{instrutorId}/unidade/{unidadeId}")
    suspend fun getModulosByInstrutorAndUnidade(
        @Path("instrutorId") instrutorId: Int,
        @Path("unidadeId") unidadeId: Int
    ): Response<List<moduloResponse>>

    // ==========================================
    // ROTAS COM AUTENTICAÇÃO
    // ==========================================
    @GET("empresas")
    suspend fun getEmpresas(
        @Header("Authorization") token: String
    ): Response<List<EmpresaUnidadeResponse>>

    @GET("instrutores/unidades-atendidas/{instrutorId}")
    suspend fun getUnidadesAtendidasPorInstrutor(
        @Header("Authorization") token: String,
        @Path("instrutorId") instrutorId: Int
    ): Response<InstrutorUnidadesResponse>

    @GET("funcionarios/nfc/{nfc}/unidade/{unidadeId}")
    suspend fun getFuncionarioByNFC(
        @Header("Authorization") token: String,
        @Path("nfc") nfc: String,
        @Path("unidadeId") unidadeId: Int
    ): Response<GetFuncionarioByNFCResponse>

    @GET("treinamentos/unidade/{unidadeId}")
    suspend fun getTreinamentosByUnidade(
        @Header("Authorization") token: String,
        @Path("unidadeId") unidadeId: Int
    ): Response<List<TreinamentoResponse>>
}
