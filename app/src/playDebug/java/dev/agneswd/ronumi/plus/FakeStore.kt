package dev.agneswd.ronumi.plus

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import android.content.Context

internal class FakeStore(context: Context) : Store {
    private val prefs = context.getSharedPreferences("plus_fake_store", Context.MODE_PRIVATE)
    override var onPurchases: (StoreQuery) -> Unit = {}

    private var resumed = WeakReference<Activity>(null)
    val activity: Activity get() = checkNotNull(resumed.get()) { "Open Ronumi before the purchase check" }

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumed = WeakReference(activity) }
            override fun onActivityPaused(activity: Activity) { if (resumed.get() === activity) resumed.clear() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun set(mode: String) {
        require(mode in setOf("locked", "unlocked", "pending", "refunded", "offline", "canceled", "purchase-error", "purchase-pending", "already-owned"))
        prefs.edit().putString("mode", mode).putBoolean("acknowledged", false).commit()
    }

    override suspend fun queryPurchases(): StoreQuery = when (prefs.getString("mode", "locked")) {
        "offline" -> StoreQuery(StoreResult.ERROR)
        "unlocked", "already-owned" -> StoreQuery(StoreResult.OK, listOf(StorePurchase(setOf(PLUS_PRODUCT), "fake-token",
            PurchaseState.PURCHASED, prefs.getBoolean("acknowledged", false))))
        "pending" -> StoreQuery(StoreResult.OK, listOf(StorePurchase(setOf(PLUS_PRODUCT), "fake-token", PurchaseState.PENDING, false)))
        else -> StoreQuery(StoreResult.OK)
    }

    suspend fun completePending() {
        set("unlocked")
        onPurchases(queryPurchases())
    }

    override suspend fun queryPrice(): String = "$4.99"
    override suspend fun purchase(activity: Activity): StoreResult = when (prefs.getString("mode", "locked")) {
        "purchase-pending" -> {
            set("pending")
            onPurchases(queryPurchases())
            StoreResult.OK
        }
        "canceled" -> StoreResult.CANCELED
        "purchase-error" -> StoreResult.ERROR
        "already-owned" -> StoreResult.ALREADY_OWNED
        else -> StoreResult.UNAVAILABLE
    }
    override suspend fun acknowledge(token: String): Boolean {
        check(token == "fake-token")
        prefs.edit().putBoolean("acknowledged", true).commit()
        return true
    }
    val acknowledged: Boolean get() = prefs.getBoolean("acknowledged", false)
}
