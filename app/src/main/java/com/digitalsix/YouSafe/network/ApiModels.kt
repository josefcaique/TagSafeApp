package com.digitalsix.YouSafe.network

import com.google.gson.annotations.SerializedName

// ==========================================
// MODELOS PARA RECUPERAÇÃO DE SENHA
// ==========================================

data class ForgotPasswordRequest(
    @SerializedName("email")
    val email: String
)

data class ForgotPasswordResponse(
    @SerializedName("message")
    val message: String
)

// ==========================================
// MODELOS PARA RESET DE SENHA
// ==========================================

data class ResetPasswordRequest(
    @SerializedName("senha")
    val senha: String
)

data class ResetPasswordResponse(
    @SerializedName("message")
    val message: String
)

// ==========================================
// MODELOS PARA ATUALIZAÇÃO DE SENHA (PRIMEIRO ACESSO)
// ==========================================

data class UpdatePasswordRequest(
    @SerializedName("senha")
    val senha: String,

    @SerializedName("confirmSenha")
    val confirmSenha: String
)

data class UpdatePasswordResponse(
    @SerializedName("message")
    val message: String
)

// ==========================================
// MODELOS PARA GINASTICA, TREINAMENTOS E SESSOES
// ==========================================

data class GinasticaLaboralRequest(
    @SerializedName("descricao")
    val descricao: String
)

data class TreinamentoRequest(
    @SerializedName("nome")
    val nome: String,

    @SerializedName("descricao")
    val descricao: String,

    @SerializedName("carga_horaria")
    val cargaHoraria: Int,

    @SerializedName("validade_meses")
    val validadeMeses: Int,

    @SerializedName("obrigatorio")
    val obrigatorio: Boolean,

    @SerializedName("unidade_id")
    val unidadeId: String,

    @SerializedName("categorias")
    val categorias: List<String>,

    @SerializedName("participantes")
    val participantes: List<String> = emptyList()
)

data class TreinamentoResponse(
    @SerializedName("treinamento_id")
    val treinamentoId: Int,

    @SerializedName("public_id")
    val publicId: String,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("descricao")
    val descricao: String?
) {
    override fun toString(): String = nome
}

data class SessaoRequest(
    @SerializedName("unidade_id")
    val unidadeId: String,

    @SerializedName("instrutor_id")
    val instrutorId: String,

    @SerializedName("status_id")
    val statusId: String?,

    @SerializedName("data_inicio")
    val dataInicio: String,

    @SerializedName("treinamento_id")
    val treinamentoId: String?,

    @SerializedName("ginastica_laboral_id")
    val ginasticaLaboralId: String?,

    @SerializedName("modulo_id")
    val moduloId: String,

    @SerializedName("observacoes")
    val observacoes: String
)

data class IdResponse(
    @SerializedName(
        value = "id",
        alternate = ["treinamento_id", "ginastica_laboral_id", "sessao_id", "aula_id"]
    )
    val id: String?,

    @SerializedName("public_id")
    val publicId: String? = null
)

// ==========================================
// MODELOS PARA CRIAÇÃO DE AULA
// ==========================================

data class CriarAulaRequest(
    @SerializedName("descricao")
    val descricao: String,

    @SerializedName("unidade_id")
    val unidadeId: String,

    @SerializedName("data")
    val data: String? = null  // ISO 8601 format, opcional
)

data class CriarAulaResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("aula")
    val aula: AulaInfo
)

data class AulaInfo(
    @SerializedName("aula_id")
    val aulaId: Int?,

    @SerializedName("public_id")
    val publicId: String?,

    @SerializedName("data")
    val data: String,

    @SerializedName("unidade_id")
    val unidadeId: String?,

    @SerializedName("tipo_aula_id")
    val tipoAulaId: Int?,

    @SerializedName("instrutor_id")
    val instrutorId: String?,

    @SerializedName("status_id")
    val statusId: String?
)

// ==========================================
// MODELOS PARA CONFIRMAR SESSAO
// ==========================================

data class ConfirmarSessaoRequest(
    @SerializedName("data_fim")
    val dataFim: String,

    @SerializedName("participantes")
    val participantes: List<ParticipanteSessao>
)

data class ParticipanteSessao(
    @SerializedName("nfc")
    val nfc: String,

    @SerializedName("unidade_id")
    val unidadeId: String,

    @SerializedName("funcionario_id")
    val funcionarioId: String? = null,

    @SerializedName("nome")
    val nome: String? = null,

    @SerializedName("ativo")
    val ativo: Boolean? = null,

    @SerializedName("horario_registro")
    val horarioRegistro: String
)

// ==========================================
// MODELOS PARA ABORTAR AULA
// ==========================================

data class AbortarAulaRequest(
    @SerializedName("data_fim")
    val dataFim: String,

    @SerializedName("participantes")
    val participantes: List<ParticipanteSessao>
)

data class AbortarAulaResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("aula")
    val aula: AulaInfo
)

// ==========================================
// MODELO PARA LOGIN (já existe, mas garantindo)
// ==========================================

data class LoginRequest(
    @SerializedName("email")
    val email: String,

    @SerializedName("senha")
    val senha: String
)

data class LoginResponse(
    @SerializedName("message")
    val message: String? = null,

    @SerializedName("token")
    val token: String,

    @SerializedName("expires_in")
    val expiresIn: String? = null,

    @SerializedName(value = "refreshToken", alternate = ["refresh_token"])
    val refreshToken: String? = null,

    @SerializedName("refresh_expires_at")
    val refreshExpiresAt: String? = null,

    @SerializedName(value = "user", alternate = ["usuario"])
    val usuario: Usuario
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class RefreshTokenResponse(
    @SerializedName("token")
    val token: String,

    @SerializedName(value = "refreshToken", alternate = ["refresh_token"])
    val refreshToken: String? = null,

    @SerializedName("refresh_expires_at")
    val refreshExpiresAt: String? = null
)

data class Usuario(
    @SerializedName("id")
    val id: Int,

    @SerializedName(value = "public_id", alternate = ["publicId", "uuid"])
    val publicId: String? = null,

    @SerializedName("nome")
    val nome: String? = null,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("unidade_id")
    val unidadeId: Int? = null,

    @SerializedName("unidade_public_id")
    val unidadePublicId: String? = null,

    @SerializedName("empresa_id")
    val empresaId: Int? = null,

    @SerializedName("primeiro_acesso")
    val primeiroAcesso: Boolean = false,

    @SerializedName("roles")
    val roles: List<Role> = emptyList(),

    @SerializedName("instrutor_id")
    val instrutorId: Int? = null,

    @SerializedName(
        value = "instrutor_public_id",
        alternate = ["instrutorPublicId", "instrutor_uuid", "instrutor_public_uuid"]
    )
    val instrutorPublicId: String? = null,

    @SerializedName("unidades_atendidas")
    val unidadesAtendidas: List<UnidadeAtendida>? = null,

    @SerializedName("unidades_ids")
    val unidadesIds: List<Int>? = null,

    @SerializedName("unidades_public_ids")
    val unidadesPublicIds: List<String>? = null
) {
    val instrutorUuid: String?
        get() = instrutorPublicId ?: publicId
}

data class Instrutor(
    @SerializedName("id")
    val id: Int,
)

data class Role(
    @SerializedName(value = "role_id", alternate = ["id"])
    val id: Int? = null,

    @SerializedName("public_id")
    val publicId: String? = null,

    @SerializedName("nome")
    val nome: String
)

data class UnidadeAtendida(
    @SerializedName(value = "id", alternate = ["unidade_id"])
    val id: Int? = null,

    @SerializedName(value = "public_id", alternate = ["publicId", "uuid"])
    val publicId: String? = null,

    @SerializedName("nome")
    val nome: String? = null,

    @SerializedName("empresa")
    val empresa: String?,

    @SerializedName("modulos")
    val modulos: List<com.digitalsix.YouSafe.network.modulos.moduloResponse>? = null
)

// ==========================================
// MODELOS PARA UNIDADES ATENDIDAS DO INSTRUTOR
// ==========================================

data class UnidadeAtendidaDetalhe(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName(value = "public_id", alternate = ["publicId", "uuid"])
    val publicId: String? = null,

    @SerializedName("nome")
    val nome: String? = null,

    @SerializedName("empresa")
    val empresa: String?,

    @SerializedName("modulos")
    val modulos: List<com.digitalsix.YouSafe.network.modulos.moduloResponse>? = null
)

data class InstrutorUnidadesResponse(
    @SerializedName("instrutor_id")
    val instrutorId: Int? = null,

    @SerializedName("usuario_id")
    val usuarioId: Int? = null,

    @SerializedName(value = "unidades", alternate = ["unidades_atendidas"])
    val unidades: List<UnidadeAtendidaDetalhe>? = null
)

// ==========================================
// MODELOS PARA TIPO DE AULA
// ==========================================

data class TipoAula(
    @SerializedName("tipo_aula_id")
    val tipoAulaId: Int,

    @SerializedName("descricao")
    val descricao: String
)

// Response da busca (pode vir com dados ou tudo null)
data class GetFuncionarioByNFCResponse(
    @SerializedName("funcionario_id")
    val funcionario_id: Int?,

    @SerializedName("public_id")
    val publicId: String?,

    @SerializedName("nome")
    val nome: String?,

    @SerializedName("nfc")
    val nfc: String,

    @SerializedName("ativo")
    val ativo: Boolean?,

    @SerializedName("unidade_id")
    val unidade_id: String?
)
