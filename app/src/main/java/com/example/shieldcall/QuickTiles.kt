package com.example.shieldcall

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuietTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        Prefs.load(this)
        Prefs.setQuiet(this, !Prefs.quiet)
        render()
    }

    private fun render() {
        Prefs.load(this)
        val tile = qsTile ?: return
        tile.label = "Quiet Hours"
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_lock_silent_mode)
        tile.state = if (Prefs.quiet) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (Prefs.quiet) "On" else "Off"
        tile.updateTile()
    }
}

class DialerTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = "ShieldCall Dialer"
        tile.icon = Icon.createWithResource(this, android.R.drawable.sym_action_call)
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(WidgetActions.DIAL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}