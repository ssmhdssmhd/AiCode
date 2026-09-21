package com.aicode.feature.github.data

import com.aicode.feature.git.domain.GitCommandFailureException
import com.aicode.feature.git.domain.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub 功能编排层：鉴权校验 → 仓库列表 → clone。
 * API 调用与容器里的 git clone 两条链路在这里汇合，UI 只跟这一个仓库交互。
 */
@Singleton
class GitHubRepository @Inject constructor(
    private val api: GitHubApi,
    private val tokenStore: GitHubTokenStore,
    private val gitRepository: GitRepository
) {
    /** 用当前 token 调用 /user，验证 token 是否有效。成功时把 login 存进 store。 */
    suspend fun verifyToken(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = api.getUser()
            if (resp.isSuccessful && resp.body() != null) {
                val user = resp.body()!!
                tokenStore.save(tokenStore.token.value!!, user.login)
                user
            } else {
                val msg = resp.errorBody()?.string() ?: "HTTP ${resp.code()}"
                throw IllegalStateException(msg)
            }
        }
    }

    /** 当前用户的仓库列表，按最近更新排序，最多 3 页 90 个仓库。 */
    suspend fun listMyRepos(maxPages: Int = 3): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        runCatching {
            val all = mutableListOf<GitHubRepo>()
            for (page in 1..maxPages) {
                val resp = api.listMyRepos(page = page, perPage = 30)
                if (!resp.isSuccessful) break
                val body = resp.body().orEmpty()
                all.addAll(body)
                if (body.size < 30) break
            }
            all.sortedWith(compareByDescending<GitHubRepo> { it.updatedAt })
        }
    }

    /** 当前用户 starred 的仓库，最多 3 页。 */
    suspend fun listStarred(maxPages: Int = 3): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        runCatching {
            val all = mutableListOf<GitHubRepo>()
            for (page in 1..maxPages) {
                val resp = api.listStarred(page = page, perPage = 30)
                if (!resp.isSuccessful) break
                val body = resp.body().orEmpty()
                all.addAll(body)
                if (body.size < 30) break
            }
            all
        }
    }

    /** 搜索公开仓库。 */
    suspend fun search(query: String, page: Int = 1): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = api.searchRepos(query = query, page = page, perPage = 30)
            if (resp.isSuccessful) resp.body()?.items.orEmpty()
            else throw IllegalStateException(resp.errorBody()?.string() ?: "HTTP ${resp.code()}")
        }
    }

    /**
     * 把一个 GitHub 仓库 clone 到工作区。
     *
     * clone URL 优先用 HTTPS（配合凭证注入走 PAT 鉴权），不用 SSH 避免额外配置密钥。
     * [cloneDir] 允许覆盖默认目录名——通常直接用仓库名（repo.name）即可。
     * 返回 clone 后在容器内的绝对路径，UI 可以直接跳转到该路径。
     */
    suspend fun clone(repo: GitHubRepo, cloneDir: String? = null): Result<String> {
        val dirName = cloneDir ?: repo.name
        val targetPath = "${gitRepository.parentPath()}/$dirName"
        return try {
            gitRepository.clone(repo.cloneUrl, dirName)
            Result.success(targetPath)
        } catch (e: GitCommandFailureException) {
            Result.failure(IllegalStateException(e.message ?: "clone 失败", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
