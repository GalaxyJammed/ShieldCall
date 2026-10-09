package com.example.shieldcall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import java.text.DateFormatSymbols
import java.util.Calendar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

object Birthdays {
    fun monthName(m: Int): String =
        java.text.DateFormatSymbols.getInstance(Locale.getDefault()).months[m - 1]

    fun label(date: String): String {
        val md = date.takeLast(5)
        val m = md.substring(0, 2).toIntOrNull() ?: return date
        val d = md.substring(3, 5).toIntOrNull() ?: return date
        return "$d ${monthName(m)}"
    }

    fun isToday(date: String?): Boolean =
        !date.isNullOrBlank() && date.takeLast(5) == SimpleDateFormat("MM-dd", Locale.US).format(Date())

    private fun contactIdOf(c: Context, lookupKey: String): Long? {
        val uri = ContactsContract.Contacts.lookupContact(
            c.contentResolver,
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        ) ?: return null
        return ContentUris.parseId(uri)
    }

    /** Birthday text (yyyy-MM-dd) for a contact id. Local read only. */
    fun forContact(c: Context, contactId: Long): String? =
        c.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(Event.START_DATE),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
            arrayOf(contactId.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
            null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    fun forLookup(c: Context, lookupKey: String): String? =
        contactIdOf(c, lookupKey)?.let { forContact(c, it) }

    private fun birthdayRowId(c: Context, contactId: Long): Long? =
        c.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
            arrayOf(contactId.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
            null
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** Writes the birthday into the phone's contacts, so it syncs with Google like any other field. */
    fun set(c: Context, lookupKey: String, date: String?) {
        val cr = c.contentResolver
        val contactId = contactIdOf(c, lookupKey) ?: return
        val existing = birthdayRowId(c, contactId)
        if (date == null) {
            existing?.let { cr.delete(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, it), null, null) }
            return
        }
        val values = ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
            put(Event.START_DATE, date)
            put(Event.TYPE, Event.TYPE_BIRTHDAY)
        }
        if (existing != null) {
            cr.update(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, existing), values, null, null)
        } else {
            val raw = cr.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return
            values.put(ContactsContract.Data.RAW_CONTACT_ID, raw)
            cr.insert(ContactsContract.Data.CONTENT_URI, values)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BirthdayRow(lookupKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var month by remember { mutableIntStateOf(1) }
    var day by remember { mutableIntStateOf(1) }

    LaunchedEffect(lookupKey) {
        current = withContext(Dispatchers.IO) { Birthdays.forLookup(context, lookupKey) }
        loaded = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditSection("Birthday")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (!loaded) "…" else current?.let { Birthdays.label(it) } ?: "Not set",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {
                current?.let {
                    val md = it.takeLast(5)
                    month = md.substring(0, 2).toIntOrNull() ?: 1
                    day = md.substring(3, 5).toIntOrNull() ?: 1
                }
                picking = true
            }) { Text("Set") }
            if (current != null) {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { Birthdays.set(context, lookupKey, null) }
                        current = null
                    }
                }) { Text("Remove") }
            }
        }
    }

    if (picking) {
        YearlyCalendarDialog(
            initialMonth = month,
            initialDay = day,
            onSave = { m, d ->
                picking = false
                month = m
                day = d
                val date = "--%02d-%02d".format(m, d)
                scope.launch {
                    withContext(Dispatchers.IO) { Birthdays.set(context, lookupKey, date) }
                    current = date
                }
            },
            onDismiss = { picking = false }
        )
    }
}

/** Runs when the app opens. One notification per contact, once per day. Local reads only. */
object BirthdayCheck {
    suspend fun run(c: Context) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prefs = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getString("bday_day", null) == today) return
        prefs.edit().putString("bday_day", today).apply()

        val hits = ArrayList<Pair<Long, String>>()
        c.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, Event.START_DATE),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
            arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
            null
        )?.use { cur ->
            while (cur.moveToNext()) {
                if (Birthdays.isToday(cur.getString(2))) hits.add(cur.getLong(0) to cur.getString(1).orEmpty())
            }
        }
        if (hits.isEmpty()) return

        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("birthday", "Birthdays", NotificationManager.IMPORTANCE_DEFAULT))
        hits.forEach { (id, name) -> notifyOne(c, nm, id, name) }
    }

    private fun notifyOne(c: Context, nm: NotificationManager, contactId: Long, name: String) {
        val phone = c.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER),
            "${Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            c, contactId.toInt(),
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        val b = NotificationCompat.Builder(c, "birthday")
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle("Birthday today")
            .setContentText("$name is celebrating today, give them a call!")
            .setAutoCancel(true)
            .setContentIntent(open)
        if (phone != null) {
            val call = PendingIntent.getActivity(
                c, contactId.toInt() + 1,
                Intent(c, MainActivity::class.java)
                    .putExtra("dial", phone)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
            )
            b.addAction(android.R.drawable.sym_action_call, "Call", call)
        }
        nm.notify("bday$contactId", 6, b.build())
    }
}

@Composable
fun YearlyCalendarDialog(
    initialMonth: Int,
    initialDay: Int,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var shown by remember { mutableIntStateOf(initialMonth) }
    var selMonth by remember { mutableIntStateOf(initialMonth) }
    var selDay by remember { mutableIntStateOf(initialDay) }
    val locale = Locale.getDefault()
    val symbols = remember(locale) { DateFormatSymbols.getInstance(locale) }
    val firstDow = Calendar.getInstance(locale).firstDayOfWeek
    val cal = remember(shown, locale) {
        Calendar.getInstance(locale).apply {
            clear()
            set(2000, shown - 1, 1) // leap year, so February gets 29 days
        }
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val offset = (cal.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
    val weekdays = (0..6).map { i -> symbols.shortWeekdays[((firstDow - 1 + i) % 7) + 1].take(2) }
    val rows = (offset + daysInMonth + 6) / 7

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Birthday") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { shown = if (shown == 1) 12 else shown - 1 }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    Text(
                        symbols.months[shown - 1],
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { shown = if (shown == 12) 1 else shown + 1 }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    weekdays.forEach { w ->
                        Text(
                            w,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val day = r * 7 + col - offset + 1
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (day in 1..daysInMonth) {
                                    val selected = selMonth == shown && selDay == day
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable {
                                                selMonth = shown
                                                selDay = day
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$day",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selMonth, selDay) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}