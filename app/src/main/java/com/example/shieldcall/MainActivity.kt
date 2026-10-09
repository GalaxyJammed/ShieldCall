package com.example.shieldcall

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Perms(val phone: Boolean = false, val overlay: Boolean = false, val role: Boolean = false) {
    val all: Boolean get() = phone && overlay && role
}

/** Asks the Stats screen to open a vote list ("spam", "scam" or "safe"). */
object ListRequest {
    var type by mutableStateOf<String?>(null)
}

class MainActivity : ComponentActivity() {

    private var perms by mutableStateOf(Perms())

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        Favorites.load(this)
        AllowList.load(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(Prefs.crashReports)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handle(intent)
        refresh()
        setContent {
            ShieldTheme(Prefs.dark) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
                        LaunchedEffect(Unit) { Update.check(this@MainActivity) }
                        Root(perms, ::requestPhone, ::requestOverlay, ::requestRole)
                        var showNew by remember { mutableStateOf(WhatsNew.shouldShow(this@MainActivity)) }
                        if (showNew) {
                            WhatsNewDialog(all = false) {
                                WhatsNew.markSeen(this@MainActivity)
                                showNew = false
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        Widgets.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        Widgets.refresh(this)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra("voicemail", false)) {
            intent.removeExtra("voicemail")
            getSystemService(NotificationManager::class.java).cancel(3)
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                SimChoice.placeUri(this, Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null))
            }
            return
        }

        when (intent.action) {
            WidgetActions.LOOKUP -> {
                TabRequest.tab = 0
                intent.action = Intent.ACTION_MAIN
                return
            }
            WidgetActions.DIAL -> {
                DialRequest.number = ""
                intent.action = Intent.ACTION_MAIN
                return
            }
            WidgetActions.RECENTS -> {
                TabRequest.contactsMode = 1
                TabRequest.tab = 1
                intent.action = Intent.ACTION_MAIN
                return
            }
            WidgetActions.CONTACTS -> {
                TabRequest.contactsMode = 0
                TabRequest.tab = 1
                intent.action = Intent.ACTION_MAIN
                return
            }
            WidgetActions.STATS -> {
                TabRequest.tab = 2
                intent.action = Intent.ACTION_MAIN
                return
            }
        }

        val shared = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (shared != null) {
            intent.action = Intent.ACTION_MAIN
            val found = Reports.firstNumber(shared, Reports.region(this))
            if (found != null) {
                Prefs.addRecentLookup(this, found)
                LookupRequest.number = found
            } else {
                Toast.makeText(this, "No phone number found", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val number = intent.getStringExtra("dial") ?: return
        DialRequest.number = number
        intent.removeExtra("dial")
        intent.getStringExtra("tag")?.let {
            getSystemService(NotificationManager::class.java).cancel(it, 2)
        }
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
}

@Composable
fun Root(perms: Perms, onPhone: () -> Unit, onOverlay: () -> Unit, onRole: () -> Unit) {
    var number by rememberSaveable { mutableStateOf<String?>(null) }
    var lastNumber by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openList by rememberSaveable { mutableStateOf<String?>(null) }
    var returnList by rememberSaveable { mutableStateOf<String?>(null) }

    number?.let { lastNumber = it }

    val dialReq = DialRequest.number
    LaunchedEffect(dialReq) {
        if (dialReq != null) {
            settings = false
            number = null
            openList = null
            returnList = null
            tab = 0
        }
    }

    val tabReq = TabRequest.tab
    LaunchedEffect(tabReq) {
        if (tabReq != null) {
            settings = false
            number = null
            openList = null
            returnList = null
            tab = tabReq
            TabRequest.tab = null
        }
    }

    val lookupReq = LookupRequest.number
    LaunchedEffect(lookupReq) {
        if (lookupReq != null) {
            settings = false
            openList = null
            returnList = null
            number = lookupReq
            LookupRequest.number = null
        }
    }

    val listReq = ListRequest.type
    LaunchedEffect(listReq) {
        if (listReq != null) {
            settings = false
            number = null
            returnList = null
            tab = 2
            openList = listReq
            ListRequest.type = null
        }
    }

    val numberReq = NumberRequest.number
    LaunchedEffect(numberReq) {
        if (numberReq != null) {
            val from = ReturnRequest.listType
            ReturnRequest.listType = null
            settings = false
            returnList = from
            if (from == null) openList = null
            number = numberReq
            NumberRequest.number = null
        }
    }

    val screen = when {
        !perms.all -> 0
        settings -> 1
        number != null -> 3
        openList != null -> 4
        else -> 2
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
                2 -> MainTabs(
                    tab,
                    { tab = it },
                    {
                        returnList = null
                        number = it
                    },
                    { settings = true }
                )
                3 -> InfoScreen(lastNumber) {
                    number = null
                    val back = returnList
                    returnList = null
                    if (back != null) openList = back
                }
                else -> VotedNumbersScreen(
                    type = openList ?: "spam",
                    onBack = { openList = null },
                    onOpen = { n ->
                        ReturnRequest.listType = openList
                        NumberRequest.number = n
                    }
                )
            }
        }
    }
}