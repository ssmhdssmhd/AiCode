package com.aicode.feature.github.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aicode.R
import com.aicode.core.ui.AppTextField
import com.aicode.core.ui.dialogTextFieldColors
import com.aicode.feature.github.data.GitHubRepo
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import compose.icons.FeatherIcons
import compose.icons.feathericons.Search

/**
 * GitHub 集成主界面：
 *   - 未授权 → 显示 token 输入表单
 *   - 已授权 → tab 切换（我的仓库 / Starred / 搜索）+ 仓库列表 + clone 按钮
 * 与 GitScreen 同款模式：Scaffold + TopAppBar + Snackbar。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(
    viewModel: GitHubViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onCloneSuccess: ((path: String, dirName: String) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val token by viewModel.token.collectAsState()
    val username by viewModel.username.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // clone 成功后往上抛
    viewModel.onCloneSuccess = onCloneSuccess

    // toast → Snackbar 自动消费
    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.github_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (token == null) {
                GitHubTokenInput(
                    loading = state.loading,
                    error = state.error,
                    onSave = viewModel::saveToken
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 用户信息条
                    username?.let { u ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "✓ 已连接 @$u",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = viewModel::logout) {
                                Text(stringResource(R.string.github_logout))
                            }
                        }
                    }

                    // Tab 切换
                    TabRow(selectedTabIndex = tab.ordinal) {
                        GitHubTab.values().forEach { t ->
                            Tab(
                                selected = tab == t,
                                onClick = { viewModel.setTab(t) },
                                text = { Text(t.displayName()) }
                            )
                        }
                    }

                    // 搜索框（仅在 Search tab 显示，或所有 tab 都显示？简单起见所有 tab 都能搜）
                    if (tab == GitHubTab.SEARCH) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            placeholder = { Text(stringResource(R.string.github_search_hint)) },
                            leadingIcon = { Icon(FeatherIcons.Search, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            state.loading && state.repos.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            state.error != null -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = viewModel::refresh) {
                                            Text(stringResource(R.string.github_retry))
                                        }
                                    }
                                }
                            }
                            state.repos.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.github_empty),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    items(state.repos, key = { it.id }) { repoItem ->
                                        RepoRow(
                                            repo = repoItem,
                                            cloning = state.cloning == repoItem.fullName,
                                            onClone = { viewModel.clone(repoItem) }
                                        )
                                    }
                                    item { Spacer(Modifier.height(16.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun GitHubTab.displayName(): String = when (this) {
    GitHubTab.MY_REPOS -> "我的仓库"
    GitHubTab.STARRED -> "已 Star"
    GitHubTab.SEARCH -> "搜索"
}

/** token 输入表单（首次授权时）。 */
@Composable
private fun GitHubTokenInput(
    loading: Boolean,
    error: String?,
    onSave: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.github_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.github_auth_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.github_token_label)) },
            placeholder = { Text("ghp_xxxxxxxxxxxx") },
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "🙈" else "👁", style = MaterialTheme.typography.bodyMedium)
                }
            },
            singleLine = true,
            isError = error != null,
            enabled = !loading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (token.isNotBlank()) onSave(token)
            })
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.github_token_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(24.dp))

        TextButton(
            onClick = { onSave(token) },
            enabled = token.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.github_connect))
        }
    }
}

/** 仓库列表里的一行。 */
@Composable
private fun RepoRow(
    repo: GitHubRepo,
    cloning: Boolean,
    onClone: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = settingsPageBackground()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repo.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    if (repo.fork) {
                        Text("fork", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (repo.private) {
                        Text("🔒", style = MaterialTheme.typography.labelSmall)
                    }
                    if (repo.archived) {
                        Text("📦", style = MaterialTheme.typography.labelSmall)
                    }
                }
                repo.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repo.language?.let { lang ->
                        Text(lang, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("★ ${repo.stars}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            TextButton(
                onClick = onClone,
                enabled = !cloning
            ) {
                if (cloning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.github_clone))
            }
        }
    }
}
