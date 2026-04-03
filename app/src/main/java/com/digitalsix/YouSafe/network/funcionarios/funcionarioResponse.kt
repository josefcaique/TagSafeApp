package com.digitalsix.YouSafe.network

import com.google.gson.annotations.SerializedName

data class funcionarioResponse (
    @SerializedName("funcionario_id")
    val id: Int,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("nfc")
    val nfc: String,

    @SerializedName("ativo")
    val ativo: Boolean,

    @SerializedName("unidade_id")
    val unidadeId: Int
)