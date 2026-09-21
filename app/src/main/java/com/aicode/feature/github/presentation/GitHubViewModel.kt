package com.aicode.feature.github.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.feature.github.data.GitHubRepo
import com.aicode.feature.github.data.GitHubRepository
import com.aicode.feature.github.data.GitHubTokenStore
import com.aicode.feature.github.data.GitHubUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 当前选中的仓库列表来源 tab。 */
enum class GitHubTab { MY_REPOS, STARRED, SEARCH }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GitHubViewModel @Inject constructor(
    private val repo: GitHubRepository,
    private val tokenStore: GitHubTokenStore
) : ViewModel() {

    val token: StateFlow<String?> = tokenStore.token
    val username: StateFlow<String?> = tokenStore.username

    private val _state = MutableStateFlow(GitHubUiState())
    val state: StateFlow<GitHubUiState> = _state.asStateFlow()

    private val _tab = MutableStateFlow(GitHubTab.MY_REPOS)
    val tab: StateFlow<GitHubTab> = _tab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 有 token 时自动刷新仓库列表，切 tab 时重新加载。 */
    init {
        viewModelScope.launch {
            _tab.flatMapLatest { t ->
                flow {
                    emit(_state.value.copy(loading = true, error = null))
                    val result = when (t) {
                        GitHubTab.MY_REPOS -> repo.listMyRepos()
                        GitHubTab.STARRED -> repo.listStarred()
                        GitHubTab.SEARCH -> if (_searchQuery.value.isNotBlank())
                            repo.search(_searchQuery.value) else Result.success(emptyList())
                    }
                    result.fold(
                        onSuccess = { repos -> emit(_state.value.copy(repos = repos, loading = false)) },
                        onFailure = { err -> emit(_state.value.copy(loading = false, error = err.message)) }
                    )
                }
            }.collect { _state.value = it }
        }
    }

    fun setTab(t: GitHubTab) {
        _tab.value = t
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun search() {
        _tab.value = GitHubTab.SEARCH
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = when (_tab.value) {
                GitHubTab.MY_REPOS -> repo.listMyRepos()
                GitHubTab.STARRED -> repo.listStarred()
                GitHubTab.SEARCH -> repo.search(_searchQuery.value)
            }
            result.fold(
                onSuccess = { _state.value = _state.value.copy(repos = it, loading = false) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) }
            )
        }
    }

    fun saveToken(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            tokenStore.save(token.trim(), null)
            repo.verifyToken().fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, user = it, toast = "授权成功: ${it.login}") },
                onFailure = {
                    tokenStore.clear()
                    _state.value = _state.value.copy(loading = false, error = "授权失败: ${it.message}")
                }
            )
        }
    }

    fun logout() {
        tokenStore.clear()
        _state.value = _state.value.copy(user = null, repos = emptyList())
    }

    fun clone(repoInfo: GitHubRepo) {
        viewModelScope.launch {
            _state.value = _state.value.copy(cloning = repoInfo.fullName, error = null)
            val result = repo.clone(repoInfo)
            result.fold(
                onSuccess = { path ->
                    _state.value = _state.value.copy(
                        cloning = null,
                        toast = "✓ 已克隆到 $path"
                    )
                    // clone 成功后，自动把工作区切过去并刷新 Git 页
                    onCloneSuccess?.invoke(path, repoInfo.name)
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(cloning = null, error = "clone 失败: ${err.message}")
                }
            )
        }
    }

    fun clearToast() {
        if (_state.value.toast != null) {
            _state.value = _state.value.copy(toast = null)
        }
    }

    /** clone 成功时通知上层：path = 容器内路径，dirName = 目录名。 */
    var onCloneSuccess: ((String, String) -> Unit)? = null
}

data class GitHubUiState(
    val loading: Boolean = false,
    val repos: List<GitHubRepo> = emptyList(),
    val user: GitHubUser? = null,
    val cloning: String? = null,      // 正在 clone 的仓库 fullName
    val error: String? = null,
    val toast: String? = null
)
