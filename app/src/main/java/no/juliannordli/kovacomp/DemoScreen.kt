package no.juliannordli.kovacomp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

// No repositories, preferences, notification services or network in this screen.
@Composable
fun DemoScreen(onExit: () -> Unit) {
    var selected by remember { mutableStateOf(-1) }
    var favorites by remember { mutableStateOf(setOf<Int>()) }
    var notice by remember { mutableStateOf("Alle aktiviteter er eksempeldata. Valgene slettes når du avslutter demoen.") }
    val names = listOf("Sanitetsvakt på idrettsarrangement", "Øvelse: søk og førstehjelp", "Beredskapsvakt")
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Kova Companion · DEMO", style = MaterialTheme.typography.headlineSmall) }
        item { Text(notice) }
        item { Button(onClick = { notice = "Eksempelvarsel: Oppmøtetid er endret til 18:30. Ingen push er sendt." }) { Text("Vis eksempelvarsel") } }
        item { Text("Mine vakter: ${favorites.size}") }
        items(names.size) { index ->
            ElevatedCard(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(names[index], style = MaterialTheme.typography.titleMedium)
                    Text("${LocalDate.now().plusDays(index.toLong() + 1)} • 18:00 • Eksempelkorps")
                    TextButton(onClick = { favorites = if (index in favorites) favorites - index else favorites + index }) {
                        Text(if (index in favorites) "★ Fjern favoritt" else "☆ Lagre favoritt")
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onExit) { Text("Avslutt demo") } }
    }
    if (selected >= 0) AlertDialog(onDismissRequest = { selected = -1 }, title = { Text(names[selected]) },
        text = { Text("Eksempelsted. Oppmøte 18:00. Dette er en syntetisk aktivitet uten ekte deltakere eller kontaktpersoner.") },
        confirmButton = { TextButton(onClick = { selected = -1 }) { Text("Lukk") } })
}

@Composable
fun SetupScreen(modifier: Modifier, organizations: List<KovaOrganization>, initialOrg: String,
                onComplete: (String, Boolean) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var org by remember { mutableStateOf(initialOrg) }
    var menu by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(false) }
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Kom i gang · $step av 3", style = MaterialTheme.typography.headlineSmall)
        when (step) {
            1 -> {
                Text("Velg hjelpekorps")
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(organizations.firstOrNull { it.code == org }?.name ?: org) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        organizations.forEach { row -> DropdownMenuItem(text = { Text(row.name) }, onClick = { org = row.code; menu = false }) }
                    }
                }
            }
            2 -> {
                Text("Vil du ha varsler fra det valgte korpset? Dette kan endres senere.")
                Row { Checkbox(checked = notifications, onCheckedChange = { notifications = it }); Text("Aktiver korpsvarsler") }
            }
            3 -> {
                Text("Lagre din første favorittvakt")
                Text("Åpne en aktivitet og trykk Favoritt. Den vises i Mine vakter. En favoritt er din personlige huskeliste, ikke en påmelding.")
            }
        }
        Button(onClick = { if (step < 3) step++ else onComplete(org, notifications) }) { Text(if (step < 3) "Neste" else "Åpne vaktene") }
        if (step > 1) TextButton(onClick = { step-- }) { Text("Tilbake") }
        Text("Uavhengig prosjekt. Ikke en offisiell Røde Kors-app.")
    }
}
