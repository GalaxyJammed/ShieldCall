package com.example.shieldcall

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.BugReport
import android.net.Uri

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var admin by rememberSaveable { mutableStateOf(false) }
    if (admin) {
        AdminScreen { admin = false }
        return
    }
    var contact by rememberSaveable { mutableStateOf(false) }
    if (contact) {
        ContactUsScreen { contact = false }
        return
    }
    val context = LocalContext.current
    val level = remember { Badges.levelFor(context) }
    val scope = rememberCoroutineScope()

    var showPinDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var googleSignedIn by remember { mutableStateOf(Reports.signedIn()) }

    val userEmail = remember(googleSignedIn) { Reports.userEmail() }
    val userPhotoUrl = remember(googleSignedIn) { Reports.userPhotoUrl() }
    val userDisplayName = remember(googleSignedIn) { Reports.userDisplayName() }
    var showNotes by remember { mutableStateOf(false) }

    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(googleSignedIn) { isAdmin = googleSignedIn && Reports.isAdmin() }

    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .scrollbar(scroll)
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp)
    ) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val rm = context.getSystemService(RoleManager::class.java)
        val tm = context.getSystemService(TelecomManager::class.java)
        fun checkDialer() = (rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true) || (tm?.defaultDialerPackage == context.packageName)
        var isDefaultDialer by remember { mutableStateOf(checkDialer()) }
        val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isDefaultDialer = checkDialer()
        }

        MissingPermissionsCard()

        if (googleSignedIn) {
            ShieldCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UserAvatar(
                            photoUrl = userPhotoUrl,
                            fallbackChar = userEmail?.firstOrNull()?.uppercase() ?: "G"
                        )

                        Spacer(Modifier.width(16.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = userDisplayName ?: "Google Account",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            ContributorBadge(level)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = userEmail ?: "Verified Account",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View Profile")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    val ok = Reports.syncUserStats(context)
                                    isSyncing = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "Stats synced successfully!" else "Failed to sync stats.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sync Stats")
                            }
                        }

                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete Account & Data")
                        }
                    }
                }
            }
        } else {
            ShieldCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSigningIn) {
                            scope.launch {
                                isSigningIn = true
                                val ok = Auth.signIn(context)
                                isSigningIn = false
                                if (ok) {
                                    googleSignedIn = true
                                    Toast.makeText(context, "Signed in with Google!", Toast.LENGTH_SHORT).show()
                                    Reports.syncUserStats(context)
                                } else {
                                    Toast.makeText(context, "Sign in failed.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Add Google Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Sign in to sync stats and manage your profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSigningIn) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Section("Appearance")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(Icons.Default.DarkMode, "Dark theme", "Black background instead of white", Prefs.dark) {
                    Prefs.setDark(context, it)
                }
                HorizontalDivider()
                SettingRow(Icons.Default.PhoneInTalk, "Full-screen incoming call", "Off shows a small card on top of your screen. A locked phone always shows full screen.", Prefs.fullScreen) {
                    Prefs.setFullScreen(context, it)
                }
            }
        }

        if (!isDefaultDialer) {
            Spacer(Modifier.height(24.dp))

            Section("Automation")
            ShieldCard(Modifier.fillMaxWidth()) {
                SettingRow(Icons.Default.PhoneInTalk, "Automatic call lookup", "Show the info card when a call comes in", Prefs.lookup) {
                    Prefs.setLookup(context, it)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Section("Lock-screen")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(Icons.Default.Fingerprint, "Fingerprint lock", "Unlock app with fingerprint on entry", Prefs.fingerprint) {
                    Prefs.setFingerprint(context, it)
                }
                HorizontalDivider()
                SettingRow(Icons.Default.Lock, "PIN lock", "Unlock app with PIN on entry", Prefs.pinLock) {
                    if (it) {
                        Prefs.setPinLock(context, true)
                        showPinDialog = true
                    } else {
                        Prefs.setPinLock(context, false)
                    }
                }
                if (Prefs.pinLock) {
                    HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showPinDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Create PIN", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Set or change your app PIN code", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Section("Dial")
        DialSettings()

        Spacer(Modifier.height(24.dp))
        Section("Emergency")
        EmergencyCheck()

        Spacer(Modifier.height(24.dp))
        Section("Backup")
        BackupSettings()

        Spacer(Modifier.height(24.dp))
        Section("Privacy")
        ShieldCard(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Default.BugReport, "Send crash reports", "Anonymous crash data helps fix bugs. No contacts or numbers are included.", Prefs.crashReports) {
                Prefs.setCrashReports(context, it)
            }
        }

        if (isAdmin) {
            Spacer(Modifier.height(24.dp))
            Section("Moderation")
            ShieldCard(Modifier.fillMaxWidth()) {
                BlockLink("Reported reviews", null) { admin = true }
            }
        }
        Spacer(Modifier.height(24.dp))
        Section("Call minutes")
        CallMinutesSection()
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(24.dp))
        Section("About")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                    Column {
                        Text("About ShieldCall", style = MaterialTheme.typography.titleMedium)
                        Text("Version ${Update.current(context)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                BlockLink("What's new", null) { showNotes = true }
                HorizontalDivider()
                BlockLink("Contact us", null) { contact = true }
                HorizontalDivider()
                BlockLink("Privacy policy", null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Links.PRIVACY)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                HorizontalDivider()
                BlockLink("Open source licenses", null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Links.LICENSES)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        if (showNotes) WhatsNewDialog(all = true) { showNotes = false }

        Spacer(Modifier.height(32.dp))
    }

    if (showProfileDialog) {
        UserProfileDialog(
            userDisplayName = userDisplayName,
            userEmail = userEmail,
            photoUrl = userPhotoUrl,
            onDismiss = { showProfileDialog = false },
            level = level
        )
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = {
                showPinDialog = false
                if (Prefs.pinCode.isBlank()) {
                    Prefs.setPinLock(context, false)
                }
            },
            onConfirm = { newPin ->
                Prefs.setPinCode(context, newPin)
                Prefs.setPinLock(context, true)
                showPinDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text("Delete Account & Data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This action is permanent and cannot be undone.")
                    Text("The following data will be deleted:")
                    Text("• Google account association in ShieldCall\n• All identified call logs & stats\n• Your reviews and votes in community database")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            val ok = Reports.deleteAccountAndData(context)
                            isDeleting = false
                            showDeleteDialog = false
                            googleSignedIn = Reports.signedIn()
                            Toast.makeText(
                                context,
                                if (ok) "Account and all associated data deleted." else "Your data was deleted. To remove the account itself, sign in again and retry.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete Everything")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UserAvatar(photoUrl: String?, fallbackChar: String) {
    val bitmap by produceState<ImageBitmap?>(null, photoUrl) {
        value = photoUrl?.let { urlStr ->
            withContext(Dispatchers.IO) {
                try {
                    URL(urlStr).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    val shape = Modifier.size(56.dp).clip(CircleShape)
    val b = bitmap
    if (b != null) {
        Image(
            bitmap = b,
            contentDescription = "Profile Picture",
            modifier = shape,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackChar,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun UserProfileDialog(
    userDisplayName: String?,
    userEmail: String?,
    photoUrl: String?,
    onDismiss: () -> Unit,
    level: Int = 0
) {
    val context = LocalContext.current
    var anonymous by remember { mutableStateOf(Prefs.anonymousReviews) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("User Profile & Identity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(photoUrl = photoUrl, fallbackChar = userEmail?.firstOrNull()?.uppercase() ?: "G")
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(userDisplayName ?: "Google User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(userEmail ?: "Verified Account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Review Posting Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Choose how your identity appears when you post reviews on phone numbers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                anonymous = false
                                Prefs.setAnonymousReviews(context, false)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !anonymous,
                            onClick = {
                                anonymous = false
                                Prefs.setAnonymousReviews(context, false)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Use Google Account Name", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Reviews will display as ${userDisplayName ?: userEmail?.substringBefore("@") ?: "User"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                anonymous = true
                                Prefs.setAnonymousReviews(context, true)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = anonymous,
                            onClick = {
                                anonymous = true
                                Prefs.setAnonymousReviews(context, true)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Stay Anonymous", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Reviews will display as Anonymous User", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create PIN Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter a 4 to 6 digit PIN code to secure your app.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6); error = null },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(6); error = null },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN must be at least 4 digits."
                        pin != confirmPin -> error = "PINs do not match."
                        else -> onConfirm(pin)
                    }
                }
            ) { Text("Save PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingRow(icon: ImageVector, title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}