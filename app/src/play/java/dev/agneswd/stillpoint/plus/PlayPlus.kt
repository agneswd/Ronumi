package dev.agneswd.stillpoint.plus

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Context
import dev.agneswd.stillpoint.StillpointApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns the cache and entitlement policy. All changes run on the main dispatcher. */
internal class PlayPlus(app: StillpointApp, internal val store: Store) : Plus {
    // Encrypted user backups contain Room records only. They never read or clear these preferences.
    private val cache = app.getSharedPreferences("plus_entitlement", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(PlusState(
        if (cache.getBoolean("unlocked", false)) Entitlement.UNLOCKED else Entitlement.LOCKED,
    ))
    override val state = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queries = Mutex()
    private val acknowledgements = mutableSetOf<String>()
    private var purchaseRevision = 0L
    private var refreshAgain = false

    init {
        store.onPurchases = { result ->
            scope.launch {
                purchaseRevision++
                if (result.result == StoreResult.OK) applyPurchases(result, authoritative = false)
                else if (result.result == StoreResult.ALREADY_OWNED) restore()
                else setStatus(result.result)
            }
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = restore()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        restore()
    }

    override fun restore() {
        scope.launch {
            // Coalesce requests, but do not drop a restore requested during a product-details query.
            if (queries.isLocked) {
                refreshAgain = true
                return@launch
            }
            queries.withLock {
                do {
                    refreshAgain = false
                    mutableState.value = state.value.copy(status = PlusStatus.BUSY)
                    var revision: Long
                    var result: StoreQuery
                    do {
                        revision = purchaseRevision
                        result = store.queryPurchases()
                        // A purchase completed after this query started. Ask for a fresh full list.
                    } while (revision != purchaseRevision)
                    applyPurchases(result, authoritative = true)
                    val price = store.queryPrice()
                    mutableState.value = state.value.copy(price = price ?: state.value.price)
                } while (refreshAgain)
            }
        }
    }

    override fun purchase(activity: Activity) {
        scope.launch {
            if (state.value.status == PlusStatus.BUSY || state.value.entitlement != Entitlement.LOCKED) return@launch
            mutableState.value = state.value.copy(status = PlusStatus.BUSY)
            val revision = purchaseRevision
            val result = store.purchase(activity)
            if (result == StoreResult.ALREADY_OWNED) restore()
            else if (result != StoreResult.OK && revision == purchaseRevision) setStatus(result)
            // OK means the store opened. Keep BUSY until its callback or a foreground ownership query.
        }
    }

    private fun applyPurchases(result: StoreQuery, authoritative: Boolean) {
        val query = if (result.result == StoreResult.OK) PurchaseQuery.Success(result.purchases) else PurchaseQuery.Failed
        val update = reduceEntitlement(state.value.entitlement, query, authoritative)
        if (query is PurchaseQuery.Success && (authoritative || update.entitlement == Entitlement.UNLOCKED)) {
            cache.edit().putBoolean("unlocked", update.entitlement == Entitlement.UNLOCKED).apply()
        }
        mutableState.value = state.value.copy(entitlement = update.entitlement)
        setStatus(result.result)
        update.acknowledge.forEach { token ->
            if (acknowledgements.add(token)) scope.launch {
                try {
                    repeat(3) { attempt ->
                        if (store.acknowledge(token)) return@launch
                        delay(1_000L shl attempt)
                    }
                    mutableState.value = state.value.copy(status = PlusStatus.ERROR)
                    // A later foreground query retries. Failed acknowledgement never revokes locally.
                } finally { acknowledgements.remove(token) }
            }
        }
    }

    private fun setStatus(result: StoreResult) {
        mutableState.value = state.value.copy(status = when (result) {
            StoreResult.OK, StoreResult.ALREADY_OWNED -> PlusStatus.IDLE
            StoreResult.CANCELED -> PlusStatus.CANCELED
            StoreResult.UNAVAILABLE -> PlusStatus.UNAVAILABLE
            StoreResult.ERROR -> PlusStatus.ERROR
        })
    }
}
