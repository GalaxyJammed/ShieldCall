package com.example.shieldcall

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshots.SnapshotStateList

data class EditableField(val id: Long?, val value: String, val type: Int)

data class ContactDetails(
    val lookupKey: String,
    val first: String,
    val last: String,
    val nameRowId: Long?,
    val rawContactId: Long?,
    val phones: List<EditableField>,
    val emails: List<EditableField>
)

object ContactEditor {
    fun load(c: Context, lookupUri: Uri): ContactDetails? {
        return try {
            val cr = c.contentResolver
            val contactUri = ContactsContract.Contacts.lookupContact(cr, lookupUri) ?: return null
            val contactId = ContentUris.parseId(contactUri)
            var lookupKey = ""
            cr.query(contactUri, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null)?.use {
                if (it.moveToFirst()) lookupKey = it.getString(0).orEmpty()
            }
            var first = ""
            var last = ""
            var nameRow: Long? = null
            var rawId: Long? = null
            val phones = ArrayList<EditableField>()
            val emails = ArrayList<EditableField>()
            cr.query(
                Data.CONTENT_URI,
                arrayOf(Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.RAW_CONTACT_ID),
                "${Data.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    val raw = cur.getLong(5)
                    if (rawId == null) rawId = raw
                    when (cur.getString(1)) {
                        StructuredName.CONTENT_ITEM_TYPE -> if (nameRow == null) {
                            nameRow = id
                            rawId = raw
                            first = cur.getString(3).orEmpty()
                            last = cur.getString(4).orEmpty()
                            if (first.isBlank() && last.isBlank()) first = cur.getString(2).orEmpty()
                        }
                        Phone.CONTENT_ITEM_TYPE -> phones.add(EditableField(id, cur.getString(2).orEmpty(), cur.getInt(3)))
                        Email.CONTENT_ITEM_TYPE -> emails.add(EditableField(id, cur.getString(2).orEmpty(), cur.getInt(3)))
                    }
                }
            }
            ContactDetails(lookupKey, first, last, nameRow, rawId, phones, emails)
        } catch (e: Exception) {
            null
        }
    }

    private fun diff(
        ops: ArrayList<ContentProviderOperation>,
        raw: Long?,
        mime: String,
        valueColumn: String,
        typeColumn: String,
        old: List<EditableField>,
        new: List<EditableField>
    ) {
        val kept = new.mapNotNull { it.id }.toSet()
        old.filter { it.id !in kept }.forEach { f ->
            ops.add(
                ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection("${Data._ID} = ?", arrayOf(f.id.toString()))
                    .build()
            )
        }
        new.forEach { f ->
            val v = f.value.trim()
            val id = f.id
            if (id != null) {
                val original = old.firstOrNull { it.id == id }
                if (v.isBlank()) {
                    ops.add(
                        ContentProviderOperation.newDelete(Data.CONTENT_URI)
                            .withSelection("${Data._ID} = ?", arrayOf(id.toString()))
                            .build()
                    )
                } else if (original == null || original.value != v || original.type != f.type) {
                    ops.add(
                        ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                            .withSelection("${Data._ID} = ?", arrayOf(id.toString()))
                            .withValue(valueColumn, v)
                            .withValue(typeColumn, f.type)
                            .build()
                    )
                }
            } else if (v.isNotBlank() && raw != null) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, raw)
                        .withValue(Data.MIMETYPE, mime)
                        .withValue(valueColumn, v)
                        .withValue(typeColumn, f.type)
                        .build()
                )
            }
        }
    }

    fun save(
        c: Context,
        d: ContactDetails,
        first: String,
        last: String,
        phones: List<EditableField>,
        emails: List<EditableField>
    ): Boolean {
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            val display = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
            val raw = d.rawContactId
            val nameRow = d.nameRowId
            if (nameRow != null) {
                ops.add(
                    ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection("${Data._ID} = ?", arrayOf(nameRow.toString()))
                        .withValue(StructuredName.DISPLAY_NAME, display)
                        .withValue(StructuredName.GIVEN_NAME, first)
                        .withValue(StructuredName.FAMILY_NAME, last)
                        .build()
                )
            } else if (raw != null && display.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, raw)
                        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(StructuredName.GIVEN_NAME, first)
                        .withValue(StructuredName.FAMILY_NAME, last)
                        .build()
                )
            }
            diff(ops, raw, Phone.CONTENT_ITEM_TYPE, Phone.NUMBER, Phone.TYPE, d.phones, phones)
            diff(ops, raw, Email.CONTENT_ITEM_TYPE, Email.ADDRESS, Email.TYPE, d.emails, emails)
            if (ops.isNotEmpty()) c.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun delete(c: Context, lookupKey: String): Boolean = try {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        c.contentResolver.delete(uri, null, null) > 0
    } catch (e: Exception) {
        false
    }
}

private fun phoneLabel(t: Int) = when (t) {
    Phone.TYPE_MOBILE -> "Mobile"
    Phone.TYPE_WORK -> "Work"
    Phone.TYPE_HOME -> "Home"
    else -> "Other"
}

private fun phoneNext(t: Int) = when (t) {
    Phone.TYPE_MOBILE -> Phone.TYPE_WORK
    Phone.TYPE_WORK -> Phone.TYPE_HOME
    Phone.TYPE_HOME -> Phone.TYPE_OTHER
    else -> Phone.TYPE_MOBILE
}

private fun emailLabel(t: Int) = when (t) {
    Email.TYPE_HOME -> "Home"
    Email.TYPE_WORK -> "Work"
    else -> "Other"
}

private fun emailNext(t: Int) = when (t) {
    Email.TYPE_HOME -> Email.TYPE_WORK
    Email.TYPE_WORK -> Email.TYPE_OTHER
    else -> Email.TYPE_HOME
}

@Composable
fun EditContactDialog(lookupUri: String, callKey: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val result by produceState<Pair<Boolean, ContactDetails?>>(false to null, lookupUri) {
        value = true to withContext(Dispatchers.IO) { ContactEditor.load(context, Uri.parse(lookupUri)) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            val details = result.second
            when {
                !result.first -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                details == null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Couldn't open this contact.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
                else -> EditForm(details, callKey, onDismiss)
            }
        }
    }
}

@Composable
private fun EditForm(d: ContactDetails, callKey: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf(d.first) }
    var last by remember { mutableStateOf(d.last) }
    val phones = remember { mutableStateListOf<EditableField>().apply { addAll(d.phones) } }
    val emails = remember { mutableStateListOf<EditableField>().apply { addAll(d.emails) } }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        scope.launch {
            saving = true
            val ok = withContext(Dispatchers.IO) {
                ContactEditor.save(context, d, first.trim(), last.trim(), phones.toList(), emails.toList())
            }
            saving = false
            if (ok) {
                ContactsVersion.n++
                Toast.makeText(context, "Contact updated", Toast.LENGTH_SHORT).show()
                onDismiss()
            } else {
                Toast.makeText(context, "Couldn't update the contact", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) save() else Toast.makeText(context, "Contacts permission is needed to save", Toast.LENGTH_SHORT).show()
    }

    val canSave = (first.isNotBlank() || last.isNotBlank()) && !saving

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Edit contact", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = first,
                onValueChange = { first = it.take(40) },
                label = { Text("First name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = last,
                onValueChange = { last = it.take(40) },
                label = { Text("Last name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        FieldSection("Phone numbers", phones, ::phoneLabel, ::phoneNext, KeyboardType.Phone, "Add phone", Phone.TYPE_MOBILE)
        FieldSection("Emails", emails, ::emailLabel, ::emailNext, KeyboardType.Email, "Add email", Email.TYPE_HOME)
        BirthdayRow(d.lookupKey)
        CallBackgroundRow(callKey)
        Text(
            "Changes are saved to your phone's contacts and sync with your Google account when this contact is stored there. The photo can't be edited here yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = canSave,
                onClick = {
                    if (context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) save()
                    else permission.launch(Manifest.permission.WRITE_CONTACTS)
                }
            ) { Text(if (saving) "Saving…" else "Save") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this contact?") },
            text = { Text("The contact is removed from your phone and from any account it syncs with. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { ContactEditor.delete(context, d.lookupKey) }
                            if (ok) {
                                ContactsVersion.n++
                                Toast.makeText(context, "Contact deleted", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Couldn't delete the contact", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FieldSection(
    title: String,
    items: SnapshotStateList<EditableField>,
    label: (Int) -> String,
    next: (Int) -> Int,
    keyboard: KeyboardType,
    addLabel: String,
    newType: Int
) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    items.forEachIndexed { i, f ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = f.value,
                onValueChange = { items[i] = f.copy(value = it.take(80)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { items[i] = f.copy(type = next(f.type)) }) {
                Text(label(f.type), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Default.Close, contentDescription = "Remove") }
        }
    }
    TextButton(onClick = { items.add(EditableField(null, "", newType)) }) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(addLabel)
    }
}