package com.digitalsix.YouSafe.network

import com.digitalsix.YouSafe.network.modulos.moduloResponse
import com.google.gson.annotations.SerializedName

/**
 * Modelo para resposta do endpoint GET /empresas
 * Representa uma unidade com seus dados de empresa e módulos disponíveis
 */
data class EmpresaUnidadeResponse(
    @SerializedName("unidade_id")
    val unidadeId: Int,

    @SerializedName("public_id")
    val publicId: String,

    @SerializedName(value = "nome", alternate = ["empresa", "empresa_nome", "nome_empresa"])
    val nomeEmpresa: String,

    @SerializedName(value = "unidade", alternate = ["unidade_nome", "nome_unidade", "descricao"])
    val nomeUnidade: String,

    @SerializedName("cnpj")
    val cnpj: String?,

    @SerializedName("modulos")
    val modulos: List<moduloResponse>
)

/**
 * Classes auxiliares para UI
 */
data class Empresa(
    val id: Int,
    val nome: String
) {
    override fun toString() = nome
}

data class Unidade(
    val id: Int,
    val nome: String
) {
    override fun toString() = nome
}
