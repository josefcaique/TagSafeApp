package com.digitalsix.YouSafe

import com.digitalsix.YouSafe.network.modulos.moduloResponse

data class EmpresaComUnidades(
    val empresaNome: String,
    val unidades: List<UnidadeInfo>
)

data class UnidadeInfo(
    val unidadeId: Int,
    val unidadeNome: String,
    val modulos: List<moduloResponse>
)

enum class TipoModulo {
    GINASTICA_LABORAL,
    TREINAMENTO,
    DESCONHECIDO
}
