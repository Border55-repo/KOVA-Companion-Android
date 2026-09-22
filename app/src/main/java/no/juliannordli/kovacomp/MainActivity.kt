package no.juliannordli.kovacomp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val notificationTargetState = mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.init(this)
        notificationTargetState.value = NotificationTarget.fromIntent(intent)

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "kova-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<KovaSyncWorker>(15, TimeUnit.MINUTES).build()
        )

        setContent {
            KovaTheme {
                KovaScreen(
                    openTarget = notificationTargetState.value,
                    onTargetConsumed = { notificationTargetState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTargetState.value = NotificationTarget.fromIntent(intent)
    }
}

@Composable
fun KovaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF5BB8FF),
            secondary = Color(0xFFFF6B6B),
            background = Color(0xFF071626),
            surface = Color(0xFF102438),
            surfaceVariant = Color(0xFF19364F)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0067A8),
            secondary = Color(0xFFC62828),
            background = Color(0xFFF3F7FA),
            surface = Color.White,
            surfaceVariant = Color(0xFFE4EEF5)
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KovaScreen(
    openTarget: NotificationTarget? = null,
    onTargetConsumed: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { KovaRepository(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var org by remember { mutableStateOf(repo.organization()) }
    var events by remember(org) { mutableStateOf(repo.loadCache(org)) }
    var dataSource by remember(org) { mutableStateOf(repo.lastSourceLabel(org)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var orgMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf("Alle") }

    var notifyAdded by remember { mutableStateOf(settings.notifyAdded) }
    var notifyChanged by remember { mutableStateOf(settings.notifyChanged) }
    var notifyRemoved by remember { mutableStateOf(settings.notifyRemoved) }
    var showPast by remember { mutableStateOf(settings.showPastEvents) }
    var disabledTypes by remember { mutableStateOf(settings.disabledEventTypes) }
    var pushReady by remember(org) { mutableStateOf(false) }
    var bridgeHealth by remember { mutableStateOf<BridgeHealth?>(null) }

    var selectedEvent by remember { mutableStateOf<KovaEvent?>(null) }
    var selectedKind by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun refreshHealth() {
        scope.launch {
            bridgeHealth = runCatching {
                withContext(Dispatchers.IO) { BridgeHealthRepository.fetch() }
            }.getOrNull()
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
                dataSource = repo.lastSourceLabel(org)
                loading = false

                if (!firstSync) {
                    NotificationHelper.postDiff(context, diff, org)
                }
                refreshHealth()
            }.onFailure {
                loading = false
                error = "Kunne ikke hente KOVA. Viser sist lagrede data."
                refreshHealth()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        refreshHealth()
    }

    LaunchedEffect(org) {
        events = repo.loadCache(org)
        dataSource = repo.lastSourceLabel(org)
        pushReady = false
        PushManager.subscribeToOrganization(context, org) { success ->
            pushReady = success
        }
        refresh()
    }

    LaunchedEffect(openTarget) {
        val target = openTarget ?: return@LaunchedEffect

        if (org != target.organization) {
            org = target.organization
            repo.setOrganization(target.organization)
        }

        selectedEvent = repo.loadCache(target.organization)
            .firstOrNull { it.id == target.eventId }
            ?: target.toEvent()
        selectedKind = target.kind
        onTargetConsumed()
    }

    val today = LocalDate.now()
    val availableTypes = remember(events) {
        listOf("Alle") + events.map { it.type }.distinct().sorted()
    }
    val notificationTypes = remember(events) {
        events.map { it.type }.distinct().sorted()
    }
    val displayed = events.filter { event ->
        val eventDate = runCatching { LocalDate.parse(event.dateIso) }.getOrNull()
        val dateOk = showPast || eventDate == null || !eventDate.isBefore(today)
        val typeOk = typeFilter == "Alle" || event.type == typeFilter
        dateOk && typeOk
    }
    val nextEvent = events.firstOrNull {
        runCatching { !LocalDate.parse(it.dateIso).isBefore(today) }.getOrDefault(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KOVA Companion", fontWeight = FontWeight.Bold)
                        Text(
                            "Android v0.5.0 • Push + Health",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    if (selectedEvent == null) {
                        TextButton(onClick = { showSettings = !showSettings }) {
                            Text(if (showSettings) "Lukk" else "⚙ Innstillinger")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedEvent != null) {
            EventDetailScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                event = selectedEvent!!,
                organization = org,
                kind = selectedKind,
                onBack = {
                    selectedEvent = null
                    selectedKind = null
                },
                onCalendar = { CalendarHelper.addEvent(context, selectedEvent!!) },
                onOpen = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(selectedEvent!!.sourceUrl))
                    )
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Datakilde: " + dataSource) }
                        )
                        AssistChip(
                            onClick = { },
                            label = { Text(if (pushReady) "Push: aktiv" else "Push: ikke konfigurert") }
                        )
                        AssistChip(
                            onClick = { refreshHealth() },
                            label = { Text(bridgeHealth?.label() ?: "Bridge: sjekker…") }
                        )
                    }
                }

                item {
                    Box {
                        OutlinedButton(
                            onClick = { orgMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🚑 " + Organizations.nameFor(org))
                        }

                        DropdownMenu(
                            expanded = orgMenu,
                            onDismissRequest = { orgMenu = false }
                        ) {
                            Organizations.known.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.name) },
                                    onClick = {
                                        orgMenu = false
                                        org = item.code
                                        repo.setOrganization(item.code)
                                        typeFilter = "Alle"
                                        selectedEvent = null
                                    }
                                )
                            }
                        }
                    }
                }

                if (showSettings) {
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Varslinger",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                SettingSwitch("Nye aktiviteter", notifyAdded) {
                                    notifyAdded = it
                                    settings.notifyAdded = it
                                }
                                SettingSwitch("Endrede aktiviteter", notifyChanged) {
                                    notifyChanged = it
                                    settings.notifyChanged = it
                                }
                                SettingSwitch("Fjernede aktiviteter", notifyRemoved) {
                                    notifyRemoved = it
                                    settings.notifyRemoved = it
                                }

                                HorizontalDivider()

                                Text(
                                    "Varsle for aktivitetstyper",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (notificationTypes.isEmpty()) {
                                    Text(
                                        "Aktivitetstyper vises etter første synk.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    notificationTypes.forEach { type ->
                                        SettingSwitch(
                                            label = type,
                                            checked = type !in disabledTypes
                                        ) { enabled ->
                                            settings.setEventTypeEnabled(type, enabled)
                                            disabledTypes = settings.disabledEventTypes
                                        }
                                    }
                                }

                                HorizontalDivider()

                                SettingSwitch("Vis tidligere aktiviteter", showPast) {
                                    showPast = it
                                    settings.showPastEvents = it
                                }

                                Text(
                                    "Push filtreres på telefonen. Bridge og direkte KOVA beholdes som doble sikkerhetsnett.",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                OutlinedButton(
                                    onClick = {
                                        NotificationHelper.post(
                                            context,
                                            "KOVA Companion",
                                            "Testvarsel fungerer. Trykk åpner appen."
                                        )
                                    }
                                ) {
                                    Text("Send testvarsel")
                                }
                            }
                        }
                    }
                }

                error?.let { message ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                nextEvent?.let { event ->
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "NESTE AKTIVITET",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    event.description,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(event.dateLabel + " • " + event.time + " • " + event.type)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(onClick = {
                                        selectedEvent = event
                                        selectedKind = null
                                    }) {
                                        Text("Detaljer")
                                    }
                                    TextButton(
                                        onClick = { CalendarHelper.addEvent(context, event) }
                                    ) {
                                        Text("＋ Kalender")
                                    }
                                    TextButton(
                                        onClick = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(event.sourceUrl))
                                            )
                                        }
                                    ) {
                                        Text("KOVA")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { refresh() }) {
                            Text(if (loading) "Synker…" else "↻ Synk nå")
                        }
                        AssistChip(
                            onClick = { },
                            label = { Text(displayed.size.toString() + " aktiviteter") }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTypes.forEach { type ->
                            FilterChip(
                                selected = typeFilter == type,
                                onClick = { typeFilter = type },
                                label = { Text(type) }
                            )
                        }
                    }
                }

                if (displayed.isEmpty()) {
                    item {
                        Text(
                            "Ingen aktiviteter matcher filteret.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                items(displayed, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        onDetails = {
                            selectedEvent = event
                            selectedKind = null
                        },
                        onCalendar = { CalendarHelper.addEvent(context, event) },
                        onOpen = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(event.sourceUrl))
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun EventCard(
    event: KovaEvent,
    onDetails: () -> Unit,
    onCalendar: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                event.type.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                event.description,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                event.dateLabel + " • " + event.time,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDetails) {
                    Text("Detaljer")
                }
                TextButton(onClick = onCalendar) {
                    Text("＋ Kalender")
                }
                TextButton(onClick = onOpen) {
                    Text("KOVA")
                }
            }
        }
    }
}

@Composable
private fun EventDetailScreen(
    modifier: Modifier,
    event: KovaEvent,
    organization: String,
    kind: String?,
    onBack: () -> Unit,
    onCalendar: () -> Unit,
    onOpen: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedButton(onClick = onBack) {
                Text("← Tilbake")
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    kind?.let {
                        val label = when (it) {
                            "added" -> "NY AKTIVITET"
                            "changed" -> "ENDRET AKTIVITET"
                            "removed" -> "FJERNET AKTIVITET"
                            "test" -> "TESTVARSEL"
                            else -> it.uppercase()
                        }
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        event.description,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()

                    Text("Type: " + event.type)
                    Text("Dato: " + event.dateLabel)
                    Text("Tid: " + event.time)
                    Text("Korps: " + Organizations.nameFor(organization))

                    if (kind == "removed") {
                        Text(
                            "Aktiviteten er fjernet fra KOVA. Detaljene over kommer fra varselet.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onCalendar) {
                            Text("＋ Kalender")
                        }
                        OutlinedButton(onClick = onOpen) {
                            Text("Åpne KOVA")
                        }
                    }
                }
            }
        }
    }
}
