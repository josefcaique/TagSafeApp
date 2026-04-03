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
    val senha: String,

    @SerializedName("confirmSenha")
    val confirmSenha: String
)

data class ResetPasswordResponse(
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
    val unidadeId: Int,

    @SerializedName("categorias_id")
    val categoriasId: List<Int>
)

data class TreinamentoResponse(
    @SerializedName("treinamento_id")
    val treinamentoId: Int,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("descricao")
    val descricao: String?
) {
    override fun toString(): String = nome
}

data class SessaoRequest(
    @SerializedName("unidade_id")
    val unidadeId: Int,

    @SerializedName("instrutor_id")
    val instrutorId: Int,

    @SerializedName("status_id")
    val statusId: Int,

    @SerializedName("data_inicio")
    val dataInicio: String,

    @SerializedName("treinamento_id")
    val treinamentoId: Int?,

    @SerializedName("ginastica_laboral_id")
    val ginasticaLaboralId: Int?,

    @SerializedName("modulo_id")
    val moduloId: Int,

    @SerializedName("observacoes")
    val observacoes: String
)

data class IdResponse(
    @SerializedName(
        value = "id",
        alternate = ["treinamento_id", "ginastica_laboral_id", "sessao_id", "aula_id"]
    )
    val id: Int?
)

// ==========================================
// MODELOS PARA CRIAÇÃO DE AULA
// ==========================================

data class CriarAulaRequest(
    @SerializedName("descricao")
    val descricao: String,

    @SerializedName("unidade_id")
    val unidadeId: Int,

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
    val aulaId: Int,

    @SerializedName("data")
    val data: String,

    @SerializedName("unidade_id")
    val unidadeId: Int,

    @SerializedName("tipo_aula_id")
    val tipoAulaId: Int?,

    @SerializedName("instrutor_id")
    val instrutorId: Int,

    @SerializedName("status_id")
    val statusId: Int  // 4 = em progresso, 1 = confirmada, 2 = abortada
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
    val funcionarioId: Int? = null,

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
    val message: String,

    @SerializedName("token")
    val token: String,

    @SerializedName("expires_in")
    val expiresIn: String,

    @SerializedName("refresh_token")
    val refreshToken: String? = null,

    @SerializedName("refresh_expires_at")
    val refreshExpiresAt: String? = null,

    @SerializedName("usuario")
    val usuario: Usuario
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class Usuario(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("unidade_id")
    val unidadeId: Int,

    // ✅ ADICIONE ESTE CAMPO
    @SerializedName("empresa_id")
    val empresaId: Int,

    @SerializedName("primeiro_acesso")
    val primeiroAcesso: Boolean,

    @SerializedName("roles")
    val roles: List<Role>,

    @SerializedName("instrutor_id")
    val instrutorId: Int?,

    @SerializedName("unidades_atendidas")
    val unidadesAtendidas: List<UnidadeAtendida>
)

data class Instrutor(
    @SerializedName("id")
    val id: Int,
)

data class Role(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nome")
    val nome: String
)

data class UnidadeAtendida(
    @SerializedName(value = "id", alternate = ["unidade_id"])
    val id: Int,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("empresa")
    val empresa: String?
)

// ==========================================
// MODELOS PARA UNIDADES ATENDIDAS DO INSTRUTOR
// ==========================================

data class UnidadeAtendidaDetalhe(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("empresa")
    val empresa: String,

    @SerializedName("modulos")
    val modulos: List<com.digitalsix.YouSafe.network.modulos.moduloResponse>
)

data class InstrutorUnidadesResponse(
    @SerializedName("instrutor_id")
    val instrutorId: Int,

    @SerializedName("usuario_id")
    val usuarioId: Int,

    @SerializedName("unidades_atendidas")
    val unidadesAtendidas: List<UnidadeAtendidaDetalhe>
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

    @SerializedName("nome")
    val nome: String?,

    @SerializedName("nfc")
    val nfc: String,

    @SerializedName("ativo")
    val ativo: Boolean?,

    @SerializedName("unidade_id")
    val unidade_id: String?
)
