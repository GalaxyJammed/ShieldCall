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

data class Perms(val phone: Boolean = false, val overlay: Boolean = false, val role: Boolean = false) {
    val all get() = phone && overlay && role
}

class MainActivity : FragmentActivity() {

    private var perms by mutableStateOf(Perms())

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        refresh()
        setContent {
            ShieldTheme(Prefs.dark) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
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
}

@Composable
fun Root(perms: Perms, onPhone: () -> Unit, onOverlay: () -> Unit, onRole: () -> Unit) {
    var number by rememberSaveable { mutableStateOf<String?>(null) }
    var lastNumber by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
