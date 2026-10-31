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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
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
import dev.habitcommit.sync.GithubSyncScheduler
import dev.habitcommit.sync.GithubTarget
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

    var owner by remember { mutableStateOf(current?.owner.orEmpty()) }
    var repo by remember { mutableStateOf(current?.repo.orEmpty()) }
    var branch by remember {
        mutableStateOf(current?.branch ?: GithubTarget.DEFAULT_BRANCH)
    }
    var journalDir by remember {
        mutableStateOf(current?.journalDir ?: GithubTarget.DEFAULT_JOURNAL_DIR)
    }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var confirmPull by remember { mutableStateOf(false) }

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
                        "${status.repoLabel} · ${status.branch}"
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
            value = owner,
            onValueChange = { owner = it.trim(); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Owner") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = repo,
            onValueChange = { repo = it.trim(); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Repo") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it.trim(); message = null },
                modifier = Modifier.weight(1f),
                label = { Text("Branch") },
                singleLine = true,
            )
            OutlinedTextField(
                value = journalDir,
                onValueChange = { journalDir = it.trim(); message = null },
                modifier = Modifier.weight(1f),
                label = { Text("Cartella journal") },
                singleLine = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; message = null },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(if (status.configured) "Nuovo token (lascia vuoto per tenere quello salvato)" else "Token fine-grained")
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
            text = "Il PAT deve avere Contents: Read and write e Metadata: Read. GitHub non " +
                "permette di limitare un token a una sottocartella: usa un repo dedicato " +
                "ai dati, oppure una push ruleset che consenta solo journal/**.",
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
                busy = true
                message = null
                scope.launch {
                    val parsed = GithubTarget.parse(owner, repo, branch, journalDir)
                    val target = parsed.getOrElse {
                        report(it.message ?: "Dati non validi", true)
                        busy = false
                        return@launch
                    }
                    val effectiveToken = token.ifBlank { syncManager.token() }
                    if (effectiveToken.isNullOrBlank()) {
                        report("Inserisci un token GitHub", true)
                        busy = false
                        return@launch
                    }
                    val verified = withContext(Dispatchers.IO) {
                        syncManager.verify(target, effectiveToken)
                    }
                    verified.fold(
                        onSuccess = { info ->
                            if (!info.canPush) {
                                report("Il token non ha permesso di scrittura su ${info.fullName}", true)
                            } else {
                                val saved = syncManager.saveConnection(target, token.ifBlank { null })
                                saved.fold(
                                    onSuccess = {
                                        report("Collegato a ${info.fullName} (${target.branch})", false)
                                    },
                                    onFailure = { report(it.message ?: "Salvataggio fallito", true) },
                                )
                            }
                        },
                        onFailure = { report(it.message ?: "Verifica fallita", true) },
                    )
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            Text(if (busy) "Verifico…" else "Salva e verifica")
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
