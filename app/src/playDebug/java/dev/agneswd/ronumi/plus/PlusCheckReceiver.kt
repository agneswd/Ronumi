package dev.agneswd.ronumi.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.exportBackup
import dev.agneswd.ronumi.data.importBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/** Uses the existing shell broadcast and run-as result-file protocol. Ordinary apps cannot call it. */
class PlusCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.app.scope.launch {
            val result = runCatching {
                val plus = context.app.plus as PlayPlus
                val store = plus.store as FakeStore
                val scenario = intent.getStringExtra("scenario")
                if (scenario == "ads-ready" || scenario == "ads-read") return@runCatching dev.agneswd.ronumi.ads.adsCheck(context, scenario == "ads-ready")
                if (scenario == "backup") {
                    val uri = Uri.fromFile(File(context.cacheDir, "plus-check.ronumi"))
                    val password = "debug plus backup password".toCharArray()
                    store.set("unlocked")
                    refresh(plus)
                    exportBackup(context, context.app.dao, uri, password)
                    store.set("locked")
                    refresh(plus)
                    importBackup(context, context.app.dao, uri, password)
                    check(!plus.has(PlusFeature.WEBSITES)) { "Backup granted Plus" }
                    exportBackup(context, context.app.dao, uri, password)
                    store.set("unlocked")
                    refresh(plus)
                    importBackup(context, context.app.dao, uri, password)
                    check(plus.has(PlusFeature.WEBSITES)) { "Backup removed Plus" }
                    password.fill('\u0000')
                } else if (scenario in setOf("canceled", "purchase-error", "purchase-pending", "already-owned")) {
                    store.set(requireNotNull(scenario))
                    withContext(Dispatchers.Main) {
                        plus.purchase(store.activity)
                        awaitIdle(plus)
                    }
                } else if (scenario == "refund-on-foreground") {
                    store.set("refunded")
                } else if (scenario == "complete-pending") {
                    withContext(Dispatchers.Main) { store.completePending() }
                } else if (scenario == "pending-offline") {
                    store.set("offline")
                    refresh(plus)
                } else if (scenario != "read") {
                    store.set(requireNotNull(scenario))
                    refresh(plus)
                }
                val state = plus.state.value
                "${state.entitlement}\n${state.status}\n${state.price}\nacknowledged=${store.acknowledged}\n" +
                    "allFeatures=${PlusFeature.entries.all { plus.has(it) }}"
            }.fold({ "PASS\n$it" }, { "FAIL\n${it.stackTraceToString()}" })
            File(context.filesDir, "device-check.txt").writeText(result)
            pending.finish()
        }
    }
}

private suspend fun refresh(plus: PlayPlus) = withContext(Dispatchers.Main) {
    awaitIdle(plus)
    plus.restore()
    awaitIdle(plus)
}

private suspend fun awaitIdle(plus: PlayPlus) = withTimeout(20_000) {
    while (plus.state.value.status == PlusStatus.BUSY) delay(20)
}
