package com.aicode.feature.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * GitHub REST API 客户端。仅覆盖集成所需的最小端点：
 *   - GET /user                → 验证 token + 拿登录名
 *   - GET /user/repos          → 当前用户的仓库列表（含分页）
 *   - GET /user/starred        → 当前用户 starred 的仓库
 *   - GET /search/repositories → 搜索仓库
 *
 * 鉴权走 Header `Authorization: Bearer <token>`，由 OkHttp Interceptor 统一注入。
 * 这里保留 Header 参数以便在不注入时手动传 token（如临时切换 token 测试）。
 */
interface GitHubApi {

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") auth: String? = null
    ): Response<GitHubUser>

    @GET("user/repos")
    suspend fun listMyRepos(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "updated",
        @Header("Authorization") auth: String? = null
    ): Response<List<GitHubRepo>>

    @GET("user/starred")
    suspend fun listStarred(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Header("Authorization") auth: String? = null
    ): Response<List<GitHubRepo>>

    @GET("search/repositories")
    suspend fun searchRepos(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "stars",
        @Header("Authorization") auth: String? = null
    ): Response<GitHubSearchResponse>
}

@Serializable
data class GitHubUser(
    val login: String,
    val id: Long,
    val name: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val email: String? = null
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("clone_url") val cloneUrl: String,
    @SerialName("ssh_url") val sshUrl: String? = null,
    val fork: Boolean = false,
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("forks_count") val forks: Int = 0,
    val language: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val archived: Boolean = false
)

@Serializable
data class GitHubSearchResponse(
    @SerialName("total_count") val totalCount: Int,
    val items: List<GitHubRepo>
)
