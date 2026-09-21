package com.aicode.feature.github.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * 给 GitHub API 请求自动注入 Bearer Token。
 * 每次请求都从 [GitHubTokenStore.token] Flow 读最新值——用户在 UI 改 token 后，
 * 下一次请求自动生效，不用重建 Retrofit 实例。
 */
class GitHubAuthInterceptor @Inject constructor(
    private val tokenStore: GitHubTokenStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenStore.token.first() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
        } else {
            chain.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
                .build()
        }
        return chain.proceed(request)
    }
}
