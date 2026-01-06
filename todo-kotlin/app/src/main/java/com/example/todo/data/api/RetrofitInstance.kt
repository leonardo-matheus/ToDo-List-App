package com.example.todo.data.api

import android.util.Log
import com.example.todo.data.local.PreferencesManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val TAG = "RetrofitInstance"
    
    // ⚠️ ALTERE PARA O IP/URL DO SEU SERVIDOR
    private const val BASE_URL = "https://todoapp.leonardomdev.me/"
    
    private var preferencesManager: PreferencesManager? = null
    
    fun init(preferencesManager: PreferencesManager) {
        this.preferencesManager = preferencesManager
        Log.d(TAG, "Initialized with BASE_URL: $BASE_URL")
    }
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("OkHttp", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        
        val token = runBlocking {
            preferencesManager?.getToken()
        }
        
        Log.d(TAG, "Request: ${originalRequest.url}")
        Log.d(TAG, "Token present: ${token != null}")
        
        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        
        chain.proceed(newRequest)
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: TodoApi by lazy {
        Log.d(TAG, "Creating API instance")
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TodoApi::class.java)
    }
}

