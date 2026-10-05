package com.example.shieldcall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.saveable.rememberSaveable

private fun flagValue(key: String) = when (key) {
    "spamScam" -> Prefs.spamScam
    "business" -> Prefs.business
    "foreign" -> Prefs.foreign
    "unknown" -> Prefs.unknown
    else -> Prefs.unsaved
}

@Composable
fun PreferencesScreen() {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }
    val blocked by dao.blockedFlow().collectAsState(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    page?.let {
        if (it == "allow") AllowListPage { page = null } else BlockListPage(it, dao) { page = null }
        return
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollbar(scroll, UiState.bottomInset).verticalScroll(scroll).padding(horizontal = 24.dp)) {
        ScreenTitle("Security")
        Spacer(Modifier.height(16.dp))
        Section("Block calls from")
        ShieldCard(Modifier.fillMaxWidth()) {
            listOf(
                Triple(Icons.Default.ReportProblem, "Spam & scam", "spamScam"),
                Triple(Icons.Default.Business, "Businesses", "business"),
                Triple(Icons.Default.Public, "Other countries", "foreign"),
                Triple(Icons.Default.VisibilityOff, "Unknown numbers", "unknown"),
                Triple(Icons.Default.PersonOff, "Unsaved numbers", "unsaved")
            ).forEachIndexed { i, (icon, title, key) ->
                if (i > 0) HorizontalDivider()
                val text = when (key) {
                    "spamScam" -> "More votes in spam or scam than safe"
                    "business" -> "Toll-free, premium and VoIP numbers"
                    "foreign" -> "Calls from outside your country"
                    "unknown" -> "Hidden or withheld caller ID"
                    else -> "Numbers not in your contacts"
                }
                SettingRow(icon, title, text, flagValue(key)) { Prefs.setFlag(context, key, it) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Section("Quiet hours")
        QuietHoursCard()
        Spacer(Modifier.height(24.dp))
        Section("When a call matches")
        ShieldCard(Modifier.fillMaxWidth()) {
            ActionRow(Icons.Default.CallEnd, "Hang up immediately", "Reject the call without ringing", "hangup")
            HorizontalDivider()
            ActionRow(Icons.Default.Block, "Block the number", "Reject it and add it to blocked numbers", "block")
        }
        Spacer(Modifier.height(24.dp))
        Section("Blocklist")
        ShieldCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Blocklist manager", style = MaterialTheme.typography.titleMedium)
                    Text("Add numbers, names, or countries to block", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
            val nNumbers = blocked.count { it.kind() == "numbers" }
            val nCountries = blocked.count { it.kind() == "countries" }
            val nNames = blocked.count { it.kind() == "names" }
            val nPrefixes = blocked.count { it.kind() == "prefixes" }
            if (nNumbers > 0) {
                HorizontalDivider()
                BlockLink("Blocked Numbers", nNumbers) { page = "numbers" }
            }
            if (nCountries > 0) {
                HorizontalDivider()
                BlockLink("Blocked Countries", nCountries) { page = "countries" }
            }
            if (nNames > 0) {
                HorizontalDivider()
                BlockLink("Blocked Names", nNames) { page = "names" }
            }
            if (nPrefixes > 0) {
                HorizontalDivider()
                BlockLink("Blocked Prefixes", nPrefixes) { page = "prefixes" }
            }
        }
        Spacer(Modifier.height(24.dp))
        Section("Allowlist")
        ShieldCard(Modifier.fillMaxWidth()) {
            BlockLink("Always allowed numbers", AllowList.keys.size) { page = "allow" }
        }
        Spacer(Modifier.height(24.dp))
        Section("Activity")
        ShieldCard(Modifier.fillMaxWidth()) {
            BlockLink("Blocked calls", null) { page = "log" }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showAddDialog) {
        AddToBlocklistDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { value, name, type ->
                dao.block(BlockedNumber(number = value, name = name, type = type))
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddToBlocklistDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    var numberInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var prefixInput by remember { mutableStateOf("") }
    var countryInput by remember { mutableStateOf(countries.first()) }
    var pickingCountry by remember { mutableStateOf(false) }
    val enabled = when (selectedTab) {
        0 -> numberInput.isNotBlank()
        1 -> nameInput.isNotBlank()
        2 -> true
        else -> prefixInput.length >= 3
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Add to blocklist", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, edgePadding = 0.dp) {
                    listOf("Phone", "Name", "Country", "Prefix").forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = numberInput,
                            onValueChange = { numberInput = it.filter(Char::isDigit) },
                            label = { Text("Phone number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Name / Note (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    1 -> OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Specific name in calls") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    2 -> OutlinedButton(
                        onClick = { pickingCountry = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("${countryInput.flag}  ${countryInput.name} (+${countryInput.code})")
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = prefixInput,
                            onValueChange = { prefixInput = it.filter(Char::isDigit).take(15) },
                            label = { Text("Starts with") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Include the country code. 4470 blocks every number starting +44 70.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = enabled,
                        onClick = {
                            when (selectedTab) {
                                0 -> onAdd(numberInput, nameInput.ifBlank { "+$numberInput" }, "number")
                                1 -> onAdd(nameInput.lowercase(), nameInput, "name")
                                2 -> onAdd(countryInput.name.lowercase(), countryInput.name, "country")
                                else -> onAdd(prefixInput, "+$prefixInput…", "prefix")
                            }
                        }
                    ) { Text("Block") }
                }
            }
        }
    }

    if (pickingCountry) {
        CountryPicker(
            onPick = {
                countryInput = it
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false }
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, text: String, value: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable { Prefs.setAction(context, value) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
        RadioButton(selected = Prefs.action == value, onClick = null)
    }
}
