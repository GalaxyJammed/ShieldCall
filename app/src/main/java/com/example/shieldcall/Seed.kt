package com.example.shieldcall

import android.content.Context

object Seed {
    fun run(context: Context) {
        val prefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)
        val doneV2 = prefs.getBoolean("done_v2", false)
        val clearedFakeStats = prefs.getBoolean("cleared_fake_stats_v2", false)

        Thread {
            try {
                val dao = SpamDb.get(context).dao()
                if (!doneV2) {
                    dao.clear()
                    context.assets.open("spam_numbers.txt").bufferedReader().useLines { lines ->
                        lines.chunked(500).forEach { chunk ->
                            dao.insertAll(
                                chunk.mapNotNull { l ->
                                    l.split(",").takeIf { it.size == 2 }?.let { SpamNumber(it[0], it[1]) }
                                }
                            )
                        }
                    }
                    prefs.edit().putBoolean("done_v2", true).apply()
                }

                if (!clearedFakeStats) {
                    dao.clearIdentifications()
                    dao.clearHangups()
                    prefs.edit().putBoolean("cleared_fake_stats_v2", true).apply()
                }
            } catch (e: Exception) {
            }
        }.start()
    }
}