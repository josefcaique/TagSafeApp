package com.digitalsix.YouSafe.network.modulos

import com.google.gson.annotations.SerializedName

data class moduloResponse (
    @SerializedName("id")
    val id: Int,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("abreviacao")
    val abreviacao: String
)
