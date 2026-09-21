package com.aicode.feature.github.di

import com.aicode.feature.github.data.GitHubApi
import com.aicode.feature.github.data.GitHubAuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** GitHub API 依赖注入模块：Retrofit + 鉴权拦截器。 */
@Module
@InstallIn(SingletonComponent::class)
object GitHubModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(interceptor: GitHubAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideGitHubApi(client: OkHttpClient): GitHubApi =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
}
