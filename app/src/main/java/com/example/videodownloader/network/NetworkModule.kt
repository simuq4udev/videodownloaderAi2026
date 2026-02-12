package com.example.videodownloader.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object NetworkModule {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val pageService: PageService = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(okHttpClient)
        .build()
        .create(PageService::class.java)
}
