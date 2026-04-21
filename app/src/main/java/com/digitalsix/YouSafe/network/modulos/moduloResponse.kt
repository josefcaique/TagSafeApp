package com.digitalsix.YouSafe.network.modulos

import com.google.gson.annotations.SerializedName

data class moduloResponse (
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName(value = "public_id", alternate = ["publicId", "uuid"])
    val publicId: String? = null,

    @SerializedName("nome")
    val nome: String? = null,

    @SerializedName("abreviacao")
    val abreviacao: String? = null
) {
    override fun toString(): String = nome.orEmpty()
}
