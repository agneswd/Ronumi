package dev.agneswd.ronumi.plus

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal class GooglePlayStore(context: Context) : Store {
    override var onPurchases: (StoreQuery) -> Unit = {}
    private val client = BillingClient.newBuilder(context)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { result, purchases -> onPurchases(StoreQuery(result.storeResult(), purchases.orEmpty().map { it.toStorePurchase() })) }
        .build()
    private val connection = Mutex()
    private var connectedOnce = false

    private suspend fun connect(): Boolean = connection.withLock {
        if (connectedOnce) return@withLock true // The library reconnects subsequent API calls automatically.
        withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        connectedOnce = result.responseCode == BillingClient.BillingResponseCode.OK
                        if (continuation.isActive) continuation.resume(connectedOnce)
                    }
                    override fun onBillingServiceDisconnected() = Unit
                })
            }
        } ?: false
    }

    override suspend fun queryPurchases(): StoreQuery {
        if (!connect()) return StoreQuery(StoreResult.UNAVAILABLE)
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                    if (continuation.isActive) continuation.resume(StoreQuery(result.storeResult(), purchases.map { it.toStorePurchase() }))
                }
            }
        } ?: StoreQuery(StoreResult.ERROR)
    }

    private suspend fun product(): ProductDetails? {
        if (!connect()) return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
                    QueryProductDetailsParams.Product.newBuilder().setProductId(PLUS_PRODUCT)
                        .setProductType(BillingClient.ProductType.INAPP).build(),
                )).build()
                client.queryProductDetailsAsync(params) { result, details ->
                    if (continuation.isActive) continuation.resume(
                        details.productDetailsList.singleOrNull { it.productId == PLUS_PRODUCT }
                            .takeIf { result.responseCode == BillingClient.BillingResponseCode.OK },
                    )
                }
            }
        }
    }

    // Stillpoint Plus has one permanent buy option. Do not select rental or preorder offers.
    private fun ProductDetails.buyOffer() = oneTimePurchaseOfferDetailsList?.firstOrNull {
        it.rentalDetails == null && it.preorderDetails == null
    }

    override suspend fun queryPrice(): String? = product()?.buyOffer()?.formattedPrice

    override suspend fun purchase(activity: Activity): StoreResult {
        // ProductDetails can expire. Fetch it again immediately before opening the store.
        val product = product() ?: return StoreResult.UNAVAILABLE
        val offer = product.buyOffer() ?: return StoreResult.UNAVAILABLE
        if (activity.isFinishing || activity.isDestroyed) return StoreResult.CANCELED
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
            .apply { offer.offerToken?.let(::setOfferToken) }.build()
        return client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build()).storeResult()
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!connect()) return false
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
            }
        } ?: false
    }
}

private fun BillingResult.storeResult(): StoreResult = when (responseCode) {
    BillingClient.BillingResponseCode.OK -> StoreResult.OK
    BillingClient.BillingResponseCode.USER_CANCELED -> StoreResult.CANCELED
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> StoreResult.ALREADY_OWNED
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> StoreResult.UNAVAILABLE
    else -> StoreResult.ERROR
}

private fun Purchase.toStorePurchase() = StorePurchase(products.toSet(), purchaseToken, when (purchaseState) {
    Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
    Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
    else -> PurchaseState.UNSPECIFIED
}, isAcknowledged)
