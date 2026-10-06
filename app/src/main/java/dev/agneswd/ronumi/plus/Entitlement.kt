package dev.agneswd.ronumi.plus

internal const val PLUS_PRODUCT = "stillpoint_plus"
internal enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }
internal data class StorePurchase(
    val products: Set<String>,
    val token: String,
    val state: PurchaseState,
    val acknowledged: Boolean,
)
internal sealed interface PurchaseQuery {
    data class Success(val purchases: List<StorePurchase>) : PurchaseQuery
    data object Failed : PurchaseQuery
}
internal data class EntitlementUpdate(val entitlement: Entitlement, val acknowledge: Set<String>)

/** Only a complete, successful ownership query can revoke access. Purchase events are partial lists. */
internal fun reduceEntitlement(
    previous: Entitlement,
    query: PurchaseQuery,
    authoritative: Boolean = true,
): EntitlementUpdate {
    val purchases = when (query) {
        PurchaseQuery.Failed -> return EntitlementUpdate(previous, emptySet())
        is PurchaseQuery.Success -> query.purchases.filter { PLUS_PRODUCT in it.products }
    }
    val purchased = purchases.filter { it.state == PurchaseState.PURCHASED }
    val entitlement = when {
        purchased.isNotEmpty() -> Entitlement.UNLOCKED
        !authoritative && previous == Entitlement.UNLOCKED -> previous
        purchases.any { it.state == PurchaseState.PENDING } -> Entitlement.PENDING
        authoritative -> Entitlement.LOCKED
        else -> previous
    }
    return EntitlementUpdate(entitlement, purchased.filterNot { it.acknowledged }.mapTo(mutableSetOf()) { it.token })
}
