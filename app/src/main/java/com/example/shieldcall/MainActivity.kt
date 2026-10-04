package com.example.shieldcall

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.LaunchedEffect
import android.os.Build
import android.app.NotificationManager
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.widget.Toast
import com.google.firebase.crashlytics.FirebaseCrashlytics

data class Perms(val phone: Boolean = false, val overlay: Boolean = false, val role: Boolean = false) {
    val all get() = phone && overlay && role
}

class MainActivity : FragmentActivity() {

    private var perms by mutableStateOf(Perms())

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(Prefs.crashReports)
        Favorites.load(this)
        AllowList.load(this)
        refresh()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handle(intent)
        setContent {
            ShieldTheme(Prefs.dark) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
                        val shared = when (intent?.action) {
                            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                            else -> null
                        }
                        if (shared != null) {
                            intent?.action = Intent.ACTION_MAIN
                            val found = Reports.firstNumber(shared, Reports.region(this@MainActivity))
                            if (found != null) {
                                Prefs.addRecentLookup(this@MainActivity, found)
                                LookupRequest.number = found
                            } else {
                                Toast.makeText(this@MainActivity, "No phone number found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        var isUnlocked by rememberSaveable { mutableStateOf(false) }
                        val locked = (Prefs.fingerprint || Prefs.pinLock) && !isUnlocked
                        if (locked) {
                            LockScreen { isUnlocked = true }
                        } else {
                            LaunchedEffect(Unit) { Update.check(this@MainActivity) }
                            Root(perms, ::requestPhone, ::requestOverlay, ::requestRole)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val rm = getSystemService(RoleManager::class.java)
        perms = Perms(
            checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED,
            Settings.canDrawOverlays(this),
            rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        )
    }

    private fun requestPhone() {
        permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE))
    }

    private fun requestOverlay() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra("voicemail", false) == true) {
            intent.removeExtra("voicemail")
            getSystemService(NotificationManager::class.java).cancel(3)
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                getSystemService(TelecomManager::class.java)
                    .placeCall(Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null), null)
            }
            return
        }
        val number = intent?.getStringExtra("dial") ?: return
        DialRequest.number = number
        intent.removeExtra("dial")
        intent.getStringExtra("tag")?.let { getSystemService(NotificationManager::class.java).cancel(it, 2) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }
}

@Composable
fun Root(perms: Perms, onPhone: () -> Unit, onOverlay: () -> Unit, onRole: () -> Unit) {
    var number by rememberSaveable { mutableStateOf<String?>(null) }
    var lastNumber by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val request = DialRequest.number
    LaunchedEffect(request) {
        if (request != null) {
            tab = 0
            number = null
            settings = false
        }
    }
    val lookup = LookupRequest.number
    LaunchedEffect(lookup) {
        if (lookup != null) {
            settings = false
            number = lookup
            LookupRequest.number = null
        }
    }
    number?.let { lastNumber = it }
    val screen = when {
        !perms.all -> 0
        settings -> 1
        number == null -> 2
        else -> 3
    }
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            when {
                targetState == 1 -> slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                initialState == 1 -> slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                else -> fadeIn() togetherWith fadeOut()
            }
        },
        label = "screens"
    ) { s ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (s) {
                0 -> PermissionsScreen(perms, onPhone, onOverlay, onRole)
                1 -> SettingsScreen { settings = false }
                2 -> MainTabs(tab, { tab = it }, { number = it }, { settings = true })
                else -> InfoScreen(lastNumber) { number = null }
            }
        }
    }
}
