package dev.habitcommit.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.habitcommit.R
import dev.habitcommit.data.HabitRepository
import dev.habitcommit.sync.GithubClient
import dev.habitcommit.sync.GithubSyncScheduler
import dev.habitcommit.sync.GithubTarget
import dev.habitcommit.sync.RepoSummary
import dev.habitcommit.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Fine-grained PAT creation page on GitHub. */
private const val PAT_CREATE_URL = "https://github.com/settings/tokens?type=beta"

/** Shortest plausible token: classic PATs are 40 chars, fine-grained ones longer. */
private const val MIN_TOKEN_LENGTH = 20

@Composable
fun SyncScreen(
    syncManager: SyncManager,
    repository: HabitRepository,
    onClose: () -> Unit,
    onConfigReplaced: () -> Unit,
) {
    val context = LocalContext.current
    val status by syncManager.status.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val savedTarget = remember { syncManager.settings.target() }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var confirmPull by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    var measuredRepos by remember { mutableStateOf<List<RepoSummary>>(emptyList()) }
    var proposal by remember {
        mutableStateOf(
            savedTarget?.let {
                RepoSummary(
                    owner = it.owner,
                    name = it.repo,
                    fullName = it.repoFullName,
                    isPrivate = false,
                    defaultBranch = it.branch,
                )
            },
        )
    }
    var branch by remember { mutableStateOf(savedTarget?.branch ?: "") }
    var path by remember { mutableStateOf(savedTarget?.journalDir ?: "") }

    // The token field must not appear in screenshots or the recents thumbnail.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    fun report(text: String, error: Boolean) {
        message = text
        isError = error
    }

    fun propose(summary: RepoSummary) {
        proposal = summary
        branch = summary.defaultBranch
        path = GithubTarget.DEFAULT_JOURNAL_DIR
    }

    // Search as soon as a plausible token is entered, debounced across keystrokes.
    val candidate = token.trim()
    LaunchedEffect(candidate) {
        if (candidate.length < MIN_TOKEN_LENGTH) return@LaunchedEffect
        delay(700)
        val loaded = withContext(Dispatchers.IO) {
            GithubClient.listRepositories(context.resources, candidate)
        }
        loaded.fold(
            onSuccess = { list ->
                measuredRepos = list
                val first = list.firstOrNull()
                if (first == null) {
                    report(context.getString(R.string.error_no_repo), true)
                } else {
                    propose(first)
                    message = null
                }
            },
            onFailure = { report(it.message ?: context.getString(R.string.error_verification_failed), true) },
        )
    }

    fun connect() {
        val target = proposal
        val rawToken = token.trim().ifBlank { syncManager.token() }
        when {
            target == null -> report(context.getString(R.string.error_token_required), true)
            rawToken.isNullOrBlank() -> report(context.getString(R.string.error_token_required), true)
            !GithubTarget.validBranch(branch.trim()) ->
                report(context.getString(R.string.error_branch_invalid), true)
            !GithubTarget.validPath(path.trim().trim('/')) ->
                report(context.getString(R.string.error_journal_dir_invalid), true)
            else -> {
                busy = true
                scope.launch {
                    val parsed = GithubTarget.parse(target.owner, target.name, branch, path)
                    val valid = parsed.getOrNull()
                    if (valid == null) {
                        report(context.getString(R.string.error_invalid_repo), true)
                        busy = false
                        return@launch
                    }
                    val verified = withContext(Dispatchers.IO) { syncManager.verify(valid, rawToken) }
                    verified.fold(
                        onSuccess = { info ->
                            if (!info.canPush) {
                                report(
                                    context.getString(R.string.error_no_write, info.fullName),
                                    true,
                                )
                            } else {
                                syncManager.saveConnection(valid, rawToken).fold(
                                    onSuccess = {
                                        token = ""
                                        measuredRepos = emptyList()
                                        report(
                                            context.getString(
                                                R.string.sync_saved,
                                                info.fullName,
                                                valid.branch,
                                                valid.journalDir,
                                            ),
                                            false,
                                        )
                                    },
                                    onFailure = {
                                        report(
                                            it.message
                                                ?: context.getString(R.string.error_save_failed),
                                            true,
                                        )
                                    },
                                )
                            }
                        },
                        onFailure = {
                            report(
                                it.message ?: context.getString(R.string.error_verification_failed),
                                true,
                            )
                        },
                    )
                    busy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sync_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (status.configured) {
                        "${status.repoLabel} · ${status.branch} · ${status.journalDir}/"
                    } else {
                        stringResource(R.string.not_connected)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.pat_label)) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(
                        text = stringResource(
                            if (showToken) R.string.action_hide else R.string.action_show,
                        ),
                        fontSize = 11.sp,
                    )
                }
            },
        )

        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { uriHandler.openUri(PAT_CREATE_URL) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(R.string.pat_help),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.pat_requirement),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )

        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }

        proposal?.let { current ->
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.repo_section),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                )
                if (measuredRepos.size > 1) {
                    TextButton(onClick = { pickerOpen = true }) {
                        Text(stringResource(R.string.action_change_repo), fontSize = 11.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = current.owner,
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.repo_owner), fontSize = 11.sp) },
                    singleLine = true,
                    enabled = false,
                )
                OutlinedTextField(
                    value = current.name,
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.repo_name), fontSize = 11.sp) },
                    singleLine = true,
                    enabled = false,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it; message = null },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.repo_branch), fontSize = 11.sp) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it; message = null },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.habitcommit_path), fontSize = 11.sp) },
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { connect() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(if (busy) stringResource(R.string.action_checking) else stringResource(R.string.action_connect))
            }
        }

        if (status.configured) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        GithubSyncScheduler.request(context)
                        report(context.getString(R.string.sync_queued_toast), false)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.action_sync_now), fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { confirmPull = true },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.action_download_config), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = buildString {
                    append(stringResource(R.string.sync_queued, status.pending))
                    status.lastSyncAt?.let {
                        append(" · ")
                        append(stringResource(R.string.sync_last_at, formatInstant(it)))
                    }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            if (!status.lastOk && status.lastMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = status.lastMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = {
                    syncManager.disconnect()
                    token = ""
                    measuredRepos = emptyList()
                    report(context.getString(R.string.sync_disconnected_toast), false)
                },
            ) {
                Text(stringResource(R.string.action_disconnect))
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(stringResource(R.string.pick_repo_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    measuredRepos.forEach { summary ->
                        TextButton(
                            onClick = {
                                pickerOpen = false
                                propose(summary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(summary.fullName, fontSize = 13.sp)
                                Text(
                                    text = stringResource(
                                        if (summary.isPrivate) R.string.repo_private else R.string.repo_public,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (confirmPull) {
        AlertDialog(
            onDismissRequest = { confirmPull = false },
            title = { Text(stringResource(R.string.download_title)) },
            text = { Text(stringResource(R.string.download_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPull = false
                        busy = true
                        scope.launch {
                            val pulled = withContext(Dispatchers.IO) { syncManager.downloadConfig() }
                            pulled.fold(
                                onSuccess = { text ->
                                    val saved = repository.saveConfig(text)
                                    saved.fold(
                                        onSuccess = {
                                            report(context.getString(R.string.config_pulled), false)
                                            onConfigReplaced()
                                        },
                                        onFailure = {
                                            report(
                                                it.message
                                                    ?: context.getString(R.string.invalid_configuration),
                                                true,
                                            )
                                        },
                                    )
                                },
                                onFailure = {
                                    report(
                                        it.message
                                            ?: context.getString(R.string.error_download_failed),
                                        true,
                                    )
                                },
                            )
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.action_download)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPull = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatInstant(instant: Instant): String =
    DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
