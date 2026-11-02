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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.habitcommit.data.HabitRepository
import dev.habitcommit.sync.GithubClient
import dev.habitcommit.sync.GithubSyncScheduler
import dev.habitcommit.sync.GithubTarget
import dev.habitcommit.sync.RepoSummary
import dev.habitcommit.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val current = remember { syncManager.settings.target() }

    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var confirmPull by remember { mutableStateOf(false) }
    var repos by remember { mutableStateOf<List<RepoSummary>?>(null) }
    var tokenForPicker by remember { mutableStateOf<String?>(null) }

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

    suspend fun connect(summary: RepoSummary, rawToken: String) {
        busy = true
        val parsed = GithubTarget.parse(
            summary.owner,
            summary.name,
            GithubTarget.DEFAULT_BRANCH,
            GithubTarget.DEFAULT_JOURNAL_DIR,
        )
        val target = parsed.getOrElse {
            report(it.message ?: "Repo non valido", true)
            busy = false
            return
        }
        val verified = withContext(Dispatchers.IO) { syncManager.verify(target, rawToken) }
        verified.fold(
            onSuccess = { info ->
                if (!info.canPush) {
                    report("Il token non ha permesso di scrittura su ${info.fullName}", true)
                } else {
                    // Follow the repository's default branch instead of assuming main.
                    val finalTarget = GithubTarget.parse(
                        summary.owner,
                        summary.name,
                        info.defaultBranch,
                        GithubTarget.DEFAULT_JOURNAL_DIR,
                    ).getOrDefault(target)
                    val saved = syncManager.saveConnection(finalTarget, rawToken)
                    saved.fold(
                        onSuccess = {
                            report(
                                "Collegato a ${info.fullName} (${finalTarget.branch}, " +
                                    "${finalTarget.journalDir}/)",
                                false,
                            )
                            token = ""
                        },
                        onFailure = { report(it.message ?: "Salvataggio fallito", true) },
                    )
                }
            },
            onFailure = { report(it.message ?: "Verifica fallita", true) },
        )
        busy = false
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
                    text = "Sync GitHub",
                    fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                )
                Text(
                    text = if (status.configured) {
                        "${status.repoLabel} · ${status.branch} · ${status.journalDir}/"
                    } else {
                        "Nessun repo collegato"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = onClose) { Text("Chiudi") }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Ogni valore registrato viene salvato in locale e poi inviato come un " +
                "commit separato in journal/AAAA-MM/<device>.jsonl. Le note non finiscono " +
                "mai nei messaggi di commit.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; message = null },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(if (status.configured) "Nuovo PAT (lascia vuoto per tenere quello salvato)" else "PAT fine-grained")
            },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Nascondi" else "Mostra", fontSize = 11.sp)
                }
            },
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Il PAT deve avere Contents: Read and write e Metadata: Read. Scegli " +
                "\"Only select repositories\" e limita il token al repo dei dati: l'app userà " +
                "il branch predefinito del repo e la cartella journal/.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 15.sp,
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

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val effectiveToken = token.ifBlank { syncManager.token() }
                if (effectiveToken.isNullOrBlank()) {
                    report("Inserisci un PAT GitHub", true)
                    return@Button
                }
                busy = true
                message = null
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) {
                        GithubClient.listRepositories(effectiveToken)
                    }
                    loaded.fold(
                        onSuccess = { list ->
                            when {
                                list.isEmpty() -> {
                                    report(
                                        "Il PAT non vede nessun repository. Aggiungine uno al " +
                                            "token su GitHub e riprova.",
                                        true,
                                    )
                                    busy = false
                                }
                                list.size == 1 -> {
                                    busy = false
                                    connect(list.first(), effectiveToken)
                                }
                                else -> {
                                    tokenForPicker = effectiveToken
                                    repos = list
                                    busy = false
                                }
                            }
                        },
                        onFailure = {
                            report(it.message ?: "Verifica fallita", true)
                            busy = false
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            Text(if (busy) "Verifico…" else "Collega repo")
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    GithubSyncScheduler.request(context)
                    report("Sincronizzazione in coda", false)
                },
                modifier = Modifier.weight(1f),
                enabled = status.configured && !busy,
            ) {
                Text("Sincronizza ora", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { confirmPull = true },
                modifier = Modifier.weight(1f),
                enabled = status.configured && !busy,
            ) {
                Text("Scarica habits.json", fontSize = 12.sp)
            }
        }

        if (status.configured) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = buildString {
                    append("In coda: ${status.pending}")
                    status.lastSyncAt?.let { append(" · ultima sync ${formatInstant(it)}") }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            status.lastMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    color = if (status.lastOk) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    syncManager.disconnect()
                    token = ""
                    report("Sync scollegata. I dati locali restano.", false)
                },
            ) {
                Text("Scollega repo")
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    repos?.let { list ->
        AlertDialog(
            onDismissRequest = { repos = null },
            title = { Text("Scegli repo") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "${list.size} repo visibili al token. Per evitare di scegliere, " +
                            "limita il PAT a un solo repository.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    list.forEach { summary ->
                        TextButton(
                            onClick = {
                                val picked = summary
                                val rawToken = tokenForPicker
                                repos = null
                                if (rawToken != null) {
                                    scope.launch { connect(picked, rawToken) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(summary.fullName, fontSize = 13.sp)
                                Text(
                                    text = if (summary.isPrivate) "privato" else "pubblico",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { repos = null }) { Text("Chiudi") }
            },
        )
    }

    if (confirmPull) {
        AlertDialog(
            onDismissRequest = { confirmPull = false },
            title = { Text("Scaricare habits.json?") },
            text = {
                Text(
                    "Il file del repo sostituirà la configurazione locale dopo la validazione. " +
                        "I valori registrati non vengono toccati.",
                )
            },
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
                                            report("habits.json aggiornato dal repo", false)
                                            onConfigReplaced()
                                        },
                                        onFailure = { report(it.message ?: "Config non valida", true) },
                                    )
                                },
                                onFailure = { report(it.message ?: "Download fallito", true) },
                            )
                            busy = false
                        }
                    },
                ) { Text("Scarica") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPull = false }) { Text("Annulla") }
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
    DateTimeFormatter.ofPattern("d MMM HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
