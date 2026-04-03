package com.digitalsix.YouSafe.repository

import com.digitalsix.YouSafe.network.AbortarAulaRequest
import com.digitalsix.YouSafe.network.ApiService
import com.digitalsix.YouSafe.network.ConfirmarSessaoRequest
import com.digitalsix.YouSafe.network.EmpresaUnidadeResponse
import com.digitalsix.YouSafe.network.GetFuncionarioByNFCResponse
import com.digitalsix.YouSafe.network.GinasticaLaboralRequest
import com.digitalsix.YouSafe.network.IdResponse
import com.digitalsix.YouSafe.network.InstrutorUnidadesResponse
import com.digitalsix.YouSafe.network.SessaoRequest
import com.digitalsix.YouSafe.network.TreinamentoRequest
import com.digitalsix.YouSafe.network.TreinamentoResponse
import com.digitalsix.YouSafe.network.modulos.moduloResponse
import com.google.gson.JsonElement
import retrofit2.Response

data class SessaoCriada(
    val body: JsonElement?,
    val location: String?
)

class MainRepository(private val api: ApiService) {
    suspend fun getEmpresas(token: String): ApiResult<List<EmpresaUnidadeResponse>> =
        safeCallList { api.getEmpresas(token) }

    suspend fun getUnidadesAtendidas(token: String, instrutorId: Int): ApiResult<InstrutorUnidadesResponse> =
        safeCall { api.getUnidadesAtendidasPorInstrutor(token, instrutorId) }

    suspend fun getModulos(
        instrutorId: Int,
        unidadeId: Int
    ): ApiResult<List<moduloResponse>> =
        safeCallList { api.getModulosByInstrutorAndUnidade(instrutorId, unidadeId) }

    suspend fun getTreinamentos(
        token: String,
        unidadeId: Int
    ): ApiResult<List<TreinamentoResponse>> =
        safeCallList { api.getTreinamentosByUnidade(token, unidadeId) }

    suspend fun getFuncionarioByNFC(
        token: String,
        nfc: String,
        unidadeId: Int
    ): ApiResult<GetFuncionarioByNFCResponse> =
        safeCall { api.getFuncionarioByNFC(token, nfc, unidadeId) }

    suspend fun criarGinasticaLaboral(
        token: String,
        request: GinasticaLaboralRequest
    ): ApiResult<IdResponse> = safeCall { api.criarGinasticaLaboral(token, request) }

    suspend fun criarTreinamento(
        token: String,
        request: TreinamentoRequest
    ): ApiResult<IdResponse> = safeCall { api.criarTreinamento(token, request) }

    suspend fun criarSessao(
        token: String,
        request: SessaoRequest
    ): ApiResult<SessaoCriada> {
        return try {
            val response = api.criarSessao(token, request)
            if (response.isSuccessful) {
                ApiResult.Success(
                    SessaoCriada(
                        body = response.body(),
                        location = response.headers()["Location"]
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string()
                val message = errorBody?.ifBlank { null } ?: "Erro ${response.code()}"
                ApiResult.Error(message, response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Erro de conexao")
        }
    }

    suspend fun confirmarSessao(
        token: String,
        sessaoId: Int,
        request: ConfirmarSessaoRequest
    ): ApiResult<Unit> = safeCallUnit { api.confirmarSessao(token, sessaoId, request) }

    suspend fun abortarSessao(
        token: String,
        sessaoId: Int,
        request: AbortarAulaRequest
    ): ApiResult<Unit> = safeCallUnit { api.abortarSessao(token, sessaoId, request) }

    private suspend fun <T> safeCall(
        call: suspend () -> Response<T>
    ): ApiResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    ApiResult.Error("Resposta vazia")
                } else {
                    ApiResult.Success(body)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val message = errorBody?.ifBlank { null } ?: "Erro ${response.code()}"
                ApiResult.Error(message, response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Erro de conexao")
        }
    }

    private suspend fun safeCallUnit(
        call: suspend () -> Response<*>
    ): ApiResult<Unit> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                val message = errorBody?.ifBlank { null } ?: "Erro ${response.code()}"
                ApiResult.Error(message, response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Erro de conexao")
        }
    }

    private suspend fun <T> safeCallList(
        call: suspend () -> Response<List<T>>
    ): ApiResult<List<T>> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                ApiResult.Success(response.body() ?: emptyList())
            } else {
                val errorBody = response.errorBody()?.string()
                val message = errorBody?.ifBlank { null } ?: "Erro ${response.code()}"
                ApiResult.Error(message, response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Erro de conexao")
        }
    }
}
