package dev.agneswd.stillpoint.plus

import android.app.Activity

internal enum class StoreResult { OK, CANCELED, ALREADY_OWNED, UNAVAILABLE, ERROR }
internal data class StoreQuery(val result: StoreResult, val purchases: List<StorePurchase> = emptyList())

/** The debug store supplies the same purchase results as the Play Store adapter. */
internal interface Store {
    var onPurchases: (StoreQuery) -> Unit
    suspend fun queryPurchases(): StoreQuery
    suspend fun queryPrice(): String?
    suspend fun purchase(activity: Activity): StoreResult
    suspend fun acknowledge(token: String): Boolean
}
