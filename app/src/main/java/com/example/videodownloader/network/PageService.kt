package com.example.videodownloader.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

interface PageService {
    @GET
    suspend fun fetchPage(@Url url: String): ResponseBody
}
