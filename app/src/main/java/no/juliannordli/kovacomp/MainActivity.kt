package no.juliannordli.kovacomp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.init(this)

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "kova-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<KovaSyncWorker>(30, TimeUnit.MINUTES).build()
        )

        setContent {
            MaterialTheme {
                KovaScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KovaScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { KovaRepository(context) }
    val scope = rememberCoroutineScope()

    var events by remember { mutableStateOf(repo.loadCache()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var org by remember { mutableStateOf(repo.organization()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repo.fetch(org) }
            }.onSuccess { fresh ->
                val old = repo.loadCache(org)
                val firstSync = old.isEmpty()
                val diff = repo.diff(old, fresh)

                repo.saveCache(fresh, org)
                events = fresh
                loading = false

                if (!firstSync) {
                    NotificationHelper.postDiff(context, diff)
                }
            }.onFailure {
                loading = false
                error = "Kunne ikke hente KOVA. Viser sist lagrede data."
            }
        }
    }

    LaunchedEffect(org) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KOVA Companion") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Android prototype • offentlig KOVA-data",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = org,
                onValueChange = { org = it },
                label = { Text("KOVA organisasjonskode") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        repo.setOrganization(org)
                        refresh()
                    }
                ) {
                    Text(if (loading) "Synker…" else "Synk nå")
                }

                OutlinedButton(
                    onClick = {
                        NotificationHelper.post(
                            context,
                            "Testvarsel",
                            "KOVA Companion-varslinger fungerer."
                        )
                    }
                ) {
                    Text("Test varsel")
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                events.size.toString() + " aktiviteter",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                event.description,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                event.dateLabel + " • " +
                                    event.time + " • " +
                                    event.type
                            )
                        }
                    }
                }
            }
        }
    }
}
