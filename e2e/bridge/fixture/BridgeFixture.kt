package dev.agneswd.stillpoint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.agneswd.stillpoint.data.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/** Opt-in debug fixture. Never included by normal builds or release variants. */
class BridgeFixture : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.app.scope.launch {
            val result = runCatching {
                val dao = context.app.dao
                if (intent.getBooleanExtra("seed", false)) {
                    dao.saveLimit(AppLimit("com.android.chrome", 17))
                    dao.saveSchedule(Schedule(id = 9001, name = "Bridge schedule", startMinute = 60, endMinute = 120, enabled = false))
                    dao.addSite(BlockedSite("example.com"))
                    dao.saveUsageDay(UsageDay("2026-01-01", mapOf("com.android.chrome" to 120000L), 3, 1))
                    dao.saveHeld(HeldNotification(notificationKey = "bridge-secret", packageName = "example.app", title = "BRIDGE_PRIVATE_TITLE", text = "BRIDGE_PRIVATE_TEXT", postedAt = 1))
                }
                val file = File(context.filesDir, "bridge-reference.stillpoint")
                val password = "Bridge test password only".toCharArray()
                try {
                    exportBackup(context, dao, Uri.fromFile(file), password)
                    val plaintext = BackupCrypto.decrypt(file.readBytes(), password)
                    try {
                        val backup = Json.decodeFromString<Backup>(plaintext.decodeToString()).validated()
                        File(context.filesDir, "bridge-reference.json").writeText(Json { encodeDefaults = true; prettyPrint = true }.encodeToString(backup))
                    } finally { plaintext.fill(0) }
                } finally { password.fill('\u0000') }
                "Encrypted export decrypted and validated through Backup."
            }.fold({ "PASS\n$it" }, { "FAIL\n${it.stackTraceToString()}" })
            File(context.filesDir, "device-check.txt").writeText(result)
            pending.finish()
        }
    }
}
