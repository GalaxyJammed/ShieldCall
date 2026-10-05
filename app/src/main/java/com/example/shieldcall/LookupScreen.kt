package com.example.shieldcall

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.google.i18n.phonenumbers.NumberParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Person

data class Country(val region: String, val name: String, val code: Int) {
    val flag: String
        get() = region.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

val countries: List<Country> by lazy {
    val util = PhoneNumberUtil.getInstance()
    util.supportedRegions
        .map { Country(it, Locale.Builder().setRegion(it).build().displayCountry, util.getCountryCodeForRegion(it)) }
        .sortedBy { it.name }
}

@Composable
fun LookupScreen(onSearch: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var country by remember {
        mutableStateOf(countries.firstOrNull { it.region == Locale.getDefault().country } ?: countries.first())
    }
    var digits by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val util = remember { PhoneNumberUtil.getInstance() }
    val parsed = remember(digits, country) {
        try {
            util.parse(digits, country.region).takeIf { util.isValidNumberForRegion(it, country.region) }
        } catch (e: NumberParseException) {
            null
        }
    }
    val ready = parsed != null
    fun go() {
        parsed?.let {
            val numStr = "${it.countryCode}${it.nationalNumber}"
            Prefs.addRecentLookup(context, numStr)
            LookupStats.record(context, numStr)
            onSearch(numStr)
        }
    }

    val recents = Prefs.recentLookups

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollbar(scroll, UiState.bottomInset).verticalScroll(scroll).padding(horizontal = 24.dp)) {
        UpdateCard()
        if (Update.latest != null) Spacer(Modifier.height(16.dp))
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
                Text(
                    "ShieldCall",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Check a number", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "See if a phone number has been reported as spam or scam.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.height(56.dp)) {
                        Text("${country.flag} +${country.code}")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        isError = digits.length >= 5 && parsed == null,
                        supportingText = { if (digits.length >= 5 && parsed == null) Text("Not a valid number for ${country.name}") },
                        value = digits,
                        onValueChange = { digits = it.filter(Char::isDigit).take(15) },
                        label = { Text("Phone number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { go() }),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { go() },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Check number") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val text = context.getSystemService(ClipboardManager::class.java)
                            .primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        val m = util.findNumbers(text, country.region).firstOrNull()?.number()
                        val c = m?.let { n -> countries.firstOrNull { it.region == util.getRegionCodeForNumber(n) } }
                        if (m != null && c != null) {
                            country = c
                            digits = util.format(m, PhoneNumberUtil.PhoneNumberFormat.NATIONAL).filter(Char::isDigit)
                        } else {
                            Toast.makeText(context, "No phone number found in clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Paste number from clipboard") }
            }
        }

        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Recent Lookups",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            recents.forEach { number ->
                Spacer(Modifier.height(8.dp))
                ShieldCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Prefs.addRecentLookup(context, number)
                            onSearch(number)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val contactInfo by produceState(ContactInfo(null, null, null), number) {
                            value = withContext(Dispatchers.IO) {
                                Reports.loadContactInfo(context, number)
                            }
                        }
                        Avatar(contactInfo.name ?: "", contactInfo.photo)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            if (contactInfo.name != null) {
                                Text(contactInfo.name!!, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text("+$number", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Unknown number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text("+$number", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (!contactInfo.location.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(contactInfo.location!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }

    if (picking) CountryPicker({ country = it; picking = false }) { picking = false }
}

@Composable
private fun Avatar(name: String, photo: String?) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(it))?.use { s ->
                        BitmapFactory.decodeStream(s)?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    val shape = Modifier.size(48.dp).clip(CircleShape)
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = shape, contentScale = ContentScale.Crop)
    } else {
        Box(
            shape.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (name.isBlank()) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    name.first().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun CountryPicker(onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    val list = remember(query) {
        countries.filter { it.name.contains(query, true) || it.code.toString().startsWith(query.removePrefix("+")) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search country or code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.scrollbar(listState), state = listState) {
                    items(list) { c ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp)) {
                            Text("${c.flag}  ${c.name}", Modifier.weight(1f))
                            Text("+${c.code}")
                        }
                    }
                }
            }
        }
    }
}
