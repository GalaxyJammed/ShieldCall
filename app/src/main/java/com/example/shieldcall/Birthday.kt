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

object Birthdays {
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

/** Birthday row for the edit-contact form. Saves right away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayRow(lookupKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState()

    LaunchedEffect(lookupKey) {
        current = withContext(Dispatchers.IO) { Birthdays.forLookup(context, lookupKey) }
        loaded = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Birthday", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (!loaded) "…" else current ?: "Not set",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { picking = true }) { Text("Set") }
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
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    val ms = pickerState.selectedDateMillis ?: return@TextButton
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .format(Date(ms))
                    scope.launch {
                        withContext(Dispatchers.IO) { Birthdays.set(context, lookupKey, date) }
                        current = date
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
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