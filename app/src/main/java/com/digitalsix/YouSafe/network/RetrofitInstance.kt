package com.digitalsix.YouSafe.network

import com.digitalsix.YouSafe.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    // ✅ Agora a URL vem automaticamente do Gradle (Produção ou Staging)
    private val BASE_URL = BuildConfig.BASE_URL

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
