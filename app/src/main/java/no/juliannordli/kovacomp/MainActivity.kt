package no.juliannordli.kovacomp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.text.DateFormat
import java.util.Date
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

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "kova-update-check",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).build()
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
    val registry = remember { OrganizationRegistry(context) }
    val favoriteStore = remember { FavoriteStore(context) }
    val scope = rememberCoroutineScope()

    var org by remember { mutableStateOf(repo.organization()) }
    var events by remember(org) { mutableStateOf(repo.loadCache(org)) }
    var dataSource by remember(org) { mutableStateOf(repo.lastSourceLabel(org)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var orgMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf("Alle") }
    var activitySearch by remember { mutableStateOf("") }
    var orgSearch by remember { mutableStateOf("") }
    var calendarMode by remember { mutableStateOf("list") }
    var showOnboarding by remember { mutableStateOf(!settings.onboardingComplete) }

    var notifyAdded by remember { mutableStateOf(settings.notifyAdded) }
    var notifyChanged by remember { mutableStateOf(settings.notifyChanged) }
    var notifyRemoved by remember { mutableStateOf(settings.notifyRemoved) }
    var showPast by remember { mutableStateOf(settings.showPastEvents) }
    var disabledTypes by remember { mutableStateOf(settings.disabledEventTypes) }
    var availableOrganizations by remember { mutableStateOf(registry.loadCache()) }
    var subscribedOrganizations by remember { mutableStateOf(settings.subscribedOrganizations) }
    var pushReady by remember { mutableStateOf(false) }
    var bridgeHealth by remember { mutableStateOf<BridgeHealth?>(null) }
    var latestRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatusMessage by remember {
        mutableStateOf<String?>(null)
    }
    var favorites by remember { mutableStateOf(favoriteStore.list()) }
    var showMyActivities by remember { mutableStateOf(false) }
    var myRange by remember { mutableStateOf(MyActivitiesRange.MONTH) }
    var myActivitiesLimit by remember { mutableStateOf(20) }
    var remind24Hours by remember { mutableStateOf(settings.remind24Hours) }
    var remind6Hours by remember { mutableStateOf(settings.remind6Hours) }
    var remind2Hours by remember { mutableStateOf(settings.remind2Hours) }
    var remind1Hour by remember { mutableStateOf(settings.remind1Hour) }
    var remind30Minutes by remember { mutableStateOf(settings.remind30Minutes) }
    var quietHoursEnabled by remember { mutableStateOf(settings.quietHoursEnabled) }
    var quietStartHour by remember { mutableStateOf(settings.quietStartHour) }
    var quietEndHour by remember { mutableStateOf(settings.quietEndHour) }
    var showNotificationHistory by remember { mutableStateOf(false) }
    var notificationHistory by remember { mutableStateOf(NotificationHistoryStore.list(context)) }

    var selectedEvent by remember { mutableStateOf<KovaEvent?>(null) }
    var selectedKind by remember { mutableStateOf<String?>(null) }
    var selectedOrganization by remember { mutableStateOf<String?>(null) }

    var notificationEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationEnabled =
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun refreshOrganizations() {
        scope.launch {
            val fresh = runCatching {
                withContext(Dispatchers.IO) { registry.fetch() }
            }.getOrNull()

            if (!fresh.isNullOrEmpty()) {
                availableOrganizations = fresh
            }
        }
    }

    fun refreshHealth() {
        scope.launch {
            bridgeHealth = runCatching {
                withContext(Dispatchers.IO) { BridgeHealthRepository.fetch() }
            }.getOrNull()
        }
    }

    fun downloadUpdate(release: ReleaseInfo) {
        val apkUrl = release.apkUrl
        if (apkUrl.isNullOrBlank()) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
            )
            return
        }

        runCatching {
            UpdateDownloader.enqueue(
                context = context,
                url = apkUrl,
                versionTag = release.tagName
            )
        }.onSuccess {
            updateStatusMessage =
                "Laster ned " + release.tagName +
                    " i bakgrunnen. Trykk på nedlastingsvarselet når den er ferdig."
        }.onFailure {
            updateStatusMessage =
                "Kunne ikke starte nedlasting. Åpner releasesiden."
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
            )
        }
    }

    fun checkForUpdate(showFeedback: Boolean = true) {
        if (checkingUpdate) return
        checkingUpdate = true
        if (showFeedback) {
            updateStatusMessage = "Sjekker etter oppdatering…"
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ReleaseChecker.fetchLatest() }
            }.onSuccess { latest ->
                latestRelease = latest

                when {
                    latest == null -> {
                        if (showFeedback) {
                            updateStatusMessage =
                                "Fant ingen publisert versjon på GitHub."
                        }
                    }

                    latest.isNewerThan(BuildConfig.VERSION_NAME) -> {
                        updateStatusMessage =
                            "Ny versjon " + latest.tagName +
                                " er tilgjengelig."

                        if (showFeedback) {
                            NotificationHelper.postUrl(
                                context,
                                "Ny KOVA Companion-versjon",
                                latest.name +
                                    " er tilgjengelig. Trykk for å oppdatere.",
                                latest.htmlUrl
                            )
                        }
                    }

                    else -> {
                        if (showFeedback) {
                            updateStatusMessage =
                                "Du har nyeste versjon: v" +
                                    BuildConfig.VERSION_NAME
                        }
                    }
                }
            }.onFailure { failure ->
                if (showFeedback) {
                    updateStatusMessage =
                        "Oppdateringssjekk feilet: " +
                            (failure.message ?: "ukjent feil")
                }
            }

            checkingUpdate = false
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
                favoriteStore.refreshFromEvents(org, fresh)
                favorites = favoriteStore.list()
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
        if (settings.onboardingComplete && Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        refreshOrganizations()
        refreshHealth()
        checkForUpdate(showFeedback = false)
    }

    LaunchedEffect(subscribedOrganizations) {
        pushReady = false
        PushManager.setSubscriptions(
            context,
            subscribedOrganizations
        ) { success ->
            pushReady = success
        }
    }

    LaunchedEffect(org) {
        events = repo.loadCache(org)
        dataSource = repo.lastSourceLabel(org)
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
        selectedOrganization = target.organization
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
        val query = activitySearch.trim()
        val searchOk = query.isBlank() ||
            event.description.contains(query, ignoreCase = true) ||
            event.type.contains(query, ignoreCase = true) ||
            event.dateLabel.contains(query, ignoreCase = true) ||
            event.time.contains(query, ignoreCase = true)
        dateOk && typeOk && searchOk
    }
    val upcomingEvents = events
        .filter {
            runCatching { !LocalDate.parse(it.dateIso).isBefore(today) }
                .getOrDefault(false)
        }
        .sortedWith(
            compareBy<KovaEvent> { it.dateIso }
                .thenBy { it.normalizedTime.ifBlank { "99:99" } }
        )
    val nextEvent = upcomingEvents.firstOrNull()
    val thisWeekEvents = upcomingEvents.filter {
        runCatching {
            !LocalDate.parse(it.dateIso).isAfter(today.plusDays(7))
        }.getOrDefault(false)
    }
    val laterEvents = upcomingEvents.filter {
        runCatching {
            val date = LocalDate.parse(it.dateIso)
            date.isAfter(today.plusDays(7)) &&
                !date.isAfter(today.plusDays(30))
        }.getOrDefault(false)
    }
    val myActivities = remember(favorites, myRange) {
        MyActivities.filter(favorites, myRange, today)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KOVA Companion", fontWeight = FontWeight.Bold)
                        Text(
                            "Android v" + BuildConfig.VERSION_NAME +
                                if (showOnboarding) " • Velkommen" else " • Kommende vakter",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    if (selectedEvent == null && !showOnboarding) {
                        TextButton(onClick = { showSettings = !showSettings }) {
                            Text(if (showSettings) "Lukk" else "⚙ Innstillinger")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showOnboarding) {
            OnboardingScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                onContinue = {
                    settings.onboardingComplete = true
                    showOnboarding = false
                    if (Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        } else if (selectedEvent != null) {
            EventDetailScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                event = selectedEvent!!,
                organizationCode = selectedOrganization ?: org,
                organizationName = Organizations.nameFor(
                    selectedOrganization ?: org,
                    availableOrganizations
                ),
                kind = selectedKind,
                isFavorite = favoriteStore.isFavorite(
                    selectedOrganization ?: org,
                    selectedEvent!!
                ),
                onBack = {
                    selectedEvent = null
                    selectedKind = null
                    selectedOrganization = null
                },
                onFavorite = {
                    val targetOrg = selectedOrganization ?: org
                    val newValue = !favoriteStore.isFavorite(targetOrg, selectedEvent!!)
                    favoriteStore.setFavorite(targetOrg, selectedEvent!!, newValue)
                    favorites = favoriteStore.list()
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "NESTE VAKT",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    event.description,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    event.dateLabel + " • " + event.displayTime,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        selectedEvent = event
                                        selectedOrganization = org
                                        selectedKind = null
                                    }
                                ) {
                                    Text("Se vakten")
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ElevatedCard(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Denne uka",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    thisWeekEvents.size.toString() +
                                        if (thisWeekEvents.size == 1) " vakt" else " vakter",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        ElevatedCard(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Senere",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    laterEvents.size.toString() +
                                        if (laterEvents.size == 1) " vakt" else " vakter",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                if (thisWeekEvents.isNotEmpty()) {
                    item {
                        Text(
                            "Denne uka",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(thisWeekEvents.take(4), key = { "week-" + it.id }) { event ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedEvent = event
                                selectedOrganization = org
                                selectedKind = null
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(event.description, fontWeight = FontWeight.SemiBold)
                                Text(
                                    event.dateLabel + " • " + event.displayTime,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (showSettings) {
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Teknisk status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("KOVA-data: " + dataSource)
                            Text(
                                "Bridge: " +
                                    (bridgeHealth?.label() ?: "sjekker…")
                            )
                            Text(
                                if (pushReady) {
                                    "Push: aktiv for " +
                                        subscribedOrganizations.size +
                                        " korps"
                                } else {
                                    "Push: synkroniserer abonnement"
                                }
                            )
                            Text(
                                "Varsler: " +
                                    if (notificationEnabled) "på" else "av"
                            )
                            val pushDiagnostic = PushDiagnostics.snapshot(context)
                            Text(
                                "Siste push: " + pushDiagnostic.label(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Varseltyper: Ny " + if (notifyAdded) "på" else "av" +
                                    " • Endret " + if (notifyChanged) "på" else "av" +
                                    " • Fjernet " + if (notifyRemoved) "på" else "av"
                            )
                            if (!notifyAdded) {
                                Text(
                                    "Nye aktiviteter er slått av i Innstillinger.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val lastSync = repo.lastSync(org)
                            Text(
                                "Sist synk: " +
                                    if (lastSync > 0L) {
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.SHORT,
                                            DateFormat.SHORT
                                        ).format(Date(lastSync))
                                    } else {
                                        "ikke synkronisert"
                                    }
                            )

                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://www.kova.no/")
                                        )
                                    )
                                }
                            ) {
                                Text("Logg inn / Åpne KOVA")
                            }

                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://border55-repo.github.io/KlarX/kova/admin/")
                                        )
                                    )
                                }
                            ) {
                                Text("KOVA Admin")
                            }
                            Text(
                                "Innlogging skjer hos KOVA / Røde Kors. KOVA Companion lagrer ikke brukernavn eller passord.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            HorizontalDivider()
                            Text(
                                "Om KOVA Companion",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Prosjekteier: Julian Nordli",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "KOVA Companion er et uavhengig prosjekt og er ikke en offisiell Røde Kors-app.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (!notificationEnabled) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                        ).apply {
                                            putExtra(
                                                Settings.EXTRA_APP_PACKAGE,
                                                context.packageName
                                            )
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text("Åpne varselinnstillinger")
                                }
                            }

                            TextButton(onClick = { refreshHealth() }) {
                                Text("Oppdater teknisk status")
                            }
                        }
                    }
                }
                }

                latestRelease?.takeIf { it.isNewerThan(BuildConfig.VERSION_NAME) }?.let { release ->
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "NY VERSJON TILGJENGELIG",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    release.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Du har v" + BuildConfig.VERSION_NAME +
                                        ". " + release.tagName + " kan lastes ned nå.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = { downloadUpdate(release) }
                                ) {
                                    Text("Last ned oppdatering")
                                }

                                TextButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(release.htmlUrl)
                                            )
                                        )
                                    }
                                ) {
                                    Text("Åpne releasesiden")
                                }
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "Mine vakter",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        favorites.size.toString() + " favoritter på tvers av korps",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(onClick = { showMyActivities = !showMyActivities }) {
                                    Text(if (showMyActivities) "Skjul" else "Vis")
                                }
                            }

                            if (showMyActivities) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MyActivitiesRange.values().forEach { range ->
                                        FilterChip(
                                            selected = myRange == range,
                                            onClick = {
                                                myRange = range
                                                myActivitiesLimit = 20
                                            },
                                            label = { Text(range.label) }
                                        )
                                    }
                                }

                                if (myActivities.isEmpty()) {
                                    Text(
                                        "Ingen favorittvakter i valgt periode.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    myActivities.take(myActivitiesLimit).forEach { favorite ->
                                        MyActivityRow(
                                            favorite = favorite,
                                            organizationName = Organizations.nameFor(
                                                favorite.organization,
                                                availableOrganizations
                                            ),
                                            onDetails = {
                                                selectedEvent = favorite.event
                                                selectedOrganization = favorite.organization
                                                selectedKind = null
                                            },
                                            onRemove = {
                                                favoriteStore.setFavorite(
                                                    favorite.organization,
                                                    favorite.event,
                                                    false
                                                )
                                                favorites = favoriteStore.list()
                                            },
                                            onCalendar = {
                                                CalendarHelper.addEvent(context, favorite.event)
                                            }
                                        )
                                    }
                                    if (myActivities.size > myActivitiesLimit) {
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                myActivitiesLimit += 20
                                            }
                                        ) {
                                            Text(
                                                "Vis flere (" +
                                                    (myActivities.size - myActivitiesLimit) +
                                                    " igjen)"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Box {
                        OutlinedButton(
                            onClick = { orgMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🚑 " + Organizations.nameFor(org, availableOrganizations))
                        }

                        DropdownMenu(
                            expanded = orgMenu,
                            onDismissRequest = {
                                orgMenu = false
                                orgSearch = ""
                            }
                        ) {
                            OutlinedTextField(
                                value = orgSearch,
                                onValueChange = { orgSearch = it },
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .widthIn(min = 280.dp),
                                label = { Text("Søk etter hjelpekorps") },
                                singleLine = true
                            )

                            availableOrganizations
                                .filter { it.category == "hjelpekorps" }
                                .filter {
                                    orgSearch.isBlank() ||
                                        it.name.contains(orgSearch, ignoreCase = true) ||
                                        it.code.contains(orgSearch, ignoreCase = true)
                                }
                                .forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.name) },
                                    onClick = {
                                        orgMenu = false
                                        orgSearch = ""
                                        org = item.code
                                        repo.setOrganization(item.code)
                                        typeFilter = "Alle"
                                        activitySearch = ""
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

                                Text(
                                    "Korps jeg følger",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    subscribedOrganizations.size.toString() +
                                        " av " +
                                        availableOrganizations.count { it.category == "hjelpekorps" } +
                                        " hjelpekorps valgt",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                availableOrganizations
                                    .filter { it.category == "hjelpekorps" }
                                    .forEach { organization ->
                                        SettingSwitch(
                                            label = organization.name,
                                            checked = organization.code in subscribedOrganizations
                                        ) { enabled ->
                                            settings.setOrganizationSubscribed(
                                                organization.code,
                                                enabled
                                            )
                                            subscribedOrganizations =
                                                settings.subscribedOrganizations
                                        }
                                    }

                                HorizontalDivider()

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
                                    "Stille perioder",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                SettingSwitch("Bruk stille periode", quietHoursEnabled) {
                                    quietHoursEnabled = it
                                    settings.quietHoursEnabled = it
                                }

                                if (quietHoursEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                quietStartHour = (quietStartHour + 1) % 24
                                                settings.quietStartHour = quietStartHour
                                            }
                                        ) {
                                            Text(
                                                "Fra " +
                                                    quietStartHour.toString().padStart(2, '0') +
                                                    ":00"
                                            )
                                        }
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                quietEndHour = (quietEndHour + 1) % 24
                                                settings.quietEndHour = quietEndHour
                                            }
                                        ) {
                                            Text(
                                                "Til " +
                                                    quietEndHour.toString().padStart(2, '0') +
                                                    ":00"
                                            )
                                        }
                                    }
                                    Text(
                                        "Påminnelser for Mine vakter slipper gjennom. Andre varsler registreres i historikken uten å forstyrre.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
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

                                Text(
                                    "Påminnelser for favoritter",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                SettingSwitch("24 timer før", remind24Hours) {
                                    remind24Hours = it
                                    settings.remind24Hours = it
                                    ReminderScheduler.rescheduleAll(context)
                                }

                                SettingSwitch("6 timer før", remind6Hours) {
                                    remind6Hours = it
                                    settings.remind6Hours = it
                                    ReminderScheduler.rescheduleAll(context)
                                }

                                SettingSwitch("2 timer før", remind2Hours) {
                                    remind2Hours = it
                                    settings.remind2Hours = it
                                    ReminderScheduler.rescheduleAll(context)
                                }

                                SettingSwitch("1 time før", remind1Hour) {
                                    remind1Hour = it
                                    settings.remind1Hour = it
                                    ReminderScheduler.rescheduleAll(context)
                                }

                                SettingSwitch("30 minutter før", remind30Minutes) {
                                    remind30Minutes = it
                                    settings.remind30Minutes = it
                                    ReminderScheduler.rescheduleAll(context)
                                }

                                Text(
                                    "Påminnelser planlegges lokalt på telefonen. Android kan forskyve tidspunktet litt ved strømsparing.",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            "Varselhistorikk",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            notificationHistory.size.toString() + " nylige hendelser",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            notificationHistory = NotificationHistoryStore.list(context)
                                            showNotificationHistory = !showNotificationHistory
                                        }
                                    ) {
                                        Text(if (showNotificationHistory) "Skjul" else "Vis")
                                    }
                                }

                                if (showNotificationHistory) {
                                    notificationHistory.take(20).forEach { item ->
                                        Text(
                                            DateFormat.getDateTimeInstance(
                                                DateFormat.SHORT,
                                                DateFormat.SHORT
                                            ).format(Date(item.timestamp)) +
                                                " • " + item.state +
                                                " • " + item.title,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (notificationHistory.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                NotificationHistoryStore.clear(context)
                                                notificationHistory = emptyList()
                                            }
                                        ) {
                                            Text("Tøm historikk")
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

                                Text(
                                    "Installert versjon: v" + BuildConfig.VERSION_NAME,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                HorizontalDivider()

                                Text(
                                    "Om KOVA Companion",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    "Uoffisiell app for offentlig KOVA-kalenderdata. Appen er ikke en offisiell Røde Kors- eller KOVA-app.",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(
                                                        "https://github.com/Border55-repo/KOVA-Companion-Android/blob/main/docs/privacy-policy.md"
                                                    )
                                                )
                                            )
                                        }
                                    ) {
                                        Text("Personvern", maxLines = 1)
                                    }

                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(
                                                        "https://github.com/Border55-repo/KOVA-Companion-Android"
                                                    )
                                                )
                                            )
                                        }
                                    ) {
                                        Text("Prosjekt", maxLines = 1)
                                    }
                                }

                                OutlinedButton(
                                    enabled = !checkingUpdate,
                                    onClick = { checkForUpdate(showFeedback = true) }
                                ) {
                                    Text(
                                        if (checkingUpdate) {
                                            "Sjekker etter oppdatering…"
                                        } else {
                                            "Sjekk etter oppdatering"
                                        }
                                    )
                                }

                                updateStatusMessage?.let { status ->
                                    Text(
                                        status,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            status.startsWith("Oppdateringssjekk feilet") ->
                                                MaterialTheme.colorScheme.error
                                            status.startsWith("Ny versjon") ->
                                                MaterialTheme.colorScheme.primary
                                            else ->
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }

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
                                Text(event.dateLabel + " • " + event.displayTime + " • " + event.type)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        TextButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                selectedEvent = event
                                                selectedOrganization = org
                                                selectedKind = null
                                            }
                                        ) {
                                            Text("Detaljer", maxLines = 1)
                                        }
                                        TextButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                val newValue =
                                                    !favoriteStore.isFavorite(org, event)
                                                favoriteStore.setFavorite(
                                                    org,
                                                    event,
                                                    newValue
                                                )
                                                favorites = favoriteStore.list()
                                            }
                                        ) {
                                            Text(
                                                if (favoriteStore.isFavorite(org, event)) {
                                                    "★ Favoritt"
                                                } else {
                                                    "☆ Favoritt"
                                                },
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        TextButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                CalendarHelper.addEvent(context, event)
                                            }
                                        ) {
                                            Text("＋ Kalender", maxLines = 1)
                                        }
                                        TextButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(event.sourceUrl)
                                                    )
                                                )
                                            }
                                        ) {
                                            Text("KOVA", maxLines = 1)
                                        }
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
                    OutlinedTextField(
                        value = activitySearch,
                        onValueChange = { activitySearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Søk i aktiviteter") },
                        placeholder = { Text("Navn, type, dato eller tid") },
                        singleLine = true
                    )
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = calendarMode == "list",
                            onClick = { calendarMode = "list" },
                            label = { Text("Liste") }
                        )
                        FilterChip(
                            selected = calendarMode == "week",
                            onClick = { calendarMode = "week" },
                            label = { Text("Uke") }
                        )
                        FilterChip(
                            selected = calendarMode == "month",
                            onClick = { calendarMode = "month" },
                            label = { Text("Måned") }
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

                when (calendarMode) {
                    "week" -> {
                        var previousWeek: Int? = null

                        displayed
                            .groupBy { it.dateIso }
                            .forEach { (_, dayEvents) ->
                                val first = dayEvents.first()
                                val date = runCatching {
                                    LocalDate.parse(first.dateIso)
                                }.getOrNull()
                                val week = date?.get(
                                    java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()
                                )

                                if (week != null && week != previousWeek) {
                                    item(key = "week-" + week + "-" + first.dateIso) {
                                        Text(
                                            "Uke " + week,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    previousWeek = week
                                }

                                item(key = "week-day-" + first.dateIso) {
                                    Text(
                                        first.dateLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                items(
                                    dayEvents,
                                    key = { "week-calendar-" + it.id }
                                ) { event ->
                                    EventCard(
                                        event = event,
                                        isFavorite = favoriteStore.isFavorite(org, event),
                                        onFavorite = {
                                            val newValue =
                                                !favoriteStore.isFavorite(org, event)
                                            favoriteStore.setFavorite(org, event, newValue)
                                            favorites = favoriteStore.list()
                                        },
                                        onDetails = {
                                            selectedEvent = event
                                            selectedOrganization = org
                                            selectedKind = null
                                        },
                                        onCalendar = {
                                            CalendarHelper.addEvent(context, event)
                                        },
                                        onOpen = {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(event.sourceUrl)
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                    }

                    "month" -> {
                        displayed
                            .groupBy { it.dateIso.take(7) }
                            .forEach { (monthKey, monthEvents) ->
                                val monthDate = runCatching {
                                    LocalDate.parse(monthEvents.first().dateIso)
                                }.getOrNull()
                                val monthName = monthDate?.month?.getDisplayName(
                                    java.time.format.TextStyle.FULL,
                                    java.util.Locale("nb", "NO")
                                ) ?: monthKey
                                val monthTitle = monthName.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase() else it.toString()
                                } + (monthDate?.let { " " + it.year } ?: "")

                                item(key = "month-" + monthKey) {
                                    Text(
                                        monthTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                monthEvents
                                    .groupBy { it.dateIso }
                                    .forEach { (_, dayEvents) ->
                                        val first = dayEvents.first()

                                        item(key = "month-day-" + first.dateIso) {
                                            Text(
                                                first.dateLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        items(
                                            dayEvents,
                                            key = { "month-calendar-" + it.id }
                                        ) { event ->
                                            EventCard(
                                                event = event,
                                                isFavorite = favoriteStore.isFavorite(org, event),
                                                onFavorite = {
                                                    val newValue =
                                                        !favoriteStore.isFavorite(org, event)
                                                    favoriteStore.setFavorite(
                                                        org,
                                                        event,
                                                        newValue
                                                    )
                                                    favorites = favoriteStore.list()
                                                },
                                                onDetails = {
                                                    selectedEvent = event
                                                    selectedOrganization = org
                                                    selectedKind = null
                                                },
                                                onCalendar = {
                                                    CalendarHelper.addEvent(context, event)
                                                },
                                                onOpen = {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse(event.sourceUrl)
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                            }
                    }

                    else -> {
                        items(displayed, key = { it.id }) { event ->
                            EventCard(
                                event = event,
                                isFavorite = favoriteStore.isFavorite(org, event),
                                onFavorite = {
                                    val newValue = !favoriteStore.isFavorite(org, event)
                                    favoriteStore.setFavorite(org, event, newValue)
                                    favorites = favoriteStore.list()
                                },
                                onDetails = {
                                    selectedEvent = event
                                    selectedOrganization = org
                                    selectedKind = null
                                },
                                onCalendar = {
                                    CalendarHelper.addEvent(context, event)
                                },
                                onOpen = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(event.sourceUrl)
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    modifier: Modifier,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Velkommen til KOVA Companion",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                "En raskere mobiloversikt over offentlig KOVA-kalenderdata.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Dette får du",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("• Velg hjelpekorps og følg flere korps samtidig")
                    Text("• Varsler om nye, endrede og fjernede aktiviteter")
                    Text("• Mine vakter, favoritter og lokale påminnelser")
                    Text("• Søk, filtre og kalenderoversikt")
                    Text("• Direkte KOVA-fallback hvis Bridge ikke er tilgjengelig")
                }
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Personvern og tilgang",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Appen bruker offentlig KOVA-data. Den ber ikke om Røde Kors-passord, Okta-konto eller private KOVA-data."
                    )
                }
            }
        }
        item {
            Text(
                "Du kan endre korps, varsler, påminnelser og filtre senere i Innstillinger.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinue
            ) {
                Text("Kom i gang")
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
private fun MyActivityRow(
    favorite: FavoriteActivity,
    organizationName: String,
    onDetails: () -> Unit,
    onRemove: () -> Unit,
    onCalendar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                organizationName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                favorite.event.description,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                favorite.event.dateLabel + " • " +
                    favorite.event.displayTime + " • " +
                    favorite.event.type,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(onClick = onDetails) {
                    Text("Detaljer")
                }
                TextButton(onClick = onCalendar) {
                    Text("＋ Kalender")
                }
                TextButton(onClick = onRemove) {
                    Text("★ Fjern")
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: KovaEvent,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
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
                event.dateLabel + " • " + event.displayTime,
                style = MaterialTheme.typography.bodyMedium
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDetails
                    ) {
                        Text("Detaljer", maxLines = 1)
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onFavorite
                    ) {
                        Text(
                            if (isFavorite) "★ Favoritt" else "☆ Favoritt",
                            maxLines = 1
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onCalendar
                    ) {
                        Text("＋ Kalender", maxLines = 1)
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onOpen
                    ) {
                        Text("KOVA", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDetailScreen(
    modifier: Modifier,
    event: KovaEvent,
    organizationCode: String,
    organizationName: String,
    kind: String?,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onCalendar: () -> Unit,
    onOpen: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val noteStore = remember { NoteStore(context) }
    var localNote by remember(organizationCode, event.semanticKey) {
        mutableStateOf(noteStore.get(organizationCode, event))
    }

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
                    event.weekNumber?.let { Text("Uke: " + it) }
                    if (event.monthLabel.isNotBlank()) {
                        Text("Måned: " + event.monthLabel)
                    }
                    Text("Tid: " + event.displayTime)
                    Text("Korps: " + organizationName)

                    OutlinedTextField(
                        value = localNote,
                        onValueChange = {
                            localNote = it
                            noteStore.set(organizationCode, event, it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Lokal note") },
                        supportingText = {
                            Text("Lagres bare på denne telefonen.")
                        },
                        minLines = 2
                    )

                    if (kind == "removed") {
                        Text(
                            "Aktiviteten er fjernet fra KOVA. Detaljene over kommer fra varselet.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = onFavorite
                            ) {
                                Text(
                                    if (isFavorite) "★ Favoritt" else "☆ Favoritt",
                                    maxLines = 1
                                )
                            }
                            if (kind != "removed") {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = onCalendar
                                ) {
                                    Text("＋ Kalender", maxLines = 1)
                                }
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpen
                        ) {
                            Text("Åpne KOVA", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
