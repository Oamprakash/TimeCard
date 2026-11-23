package com.oamsapps.punchd.billing

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
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-time "Remove ads" purchase (Play Billing Library 8). Owning it removes all ads, lifts the
 * 3-job limit and makes PDF/Excel export free. Play is the source of truth; DataStore caches the
 * result so the app works offline, and every start re-checks (which also handles refunds).
 *
 * Create an in-app product with ID [PRODUCT_ID] in Play Console: $2.99 / €2.99, regional pricing elsewhere.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "remove_ads"
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var details: ProductDetails? = null

    private val _price = MutableStateFlow<String?>(null)
    /** Localised price from Play, e.g. "$2.99"; null until loaded or when billing is unavailable. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _pending = MutableStateFlow(false)
    /** A purchase is waiting for payment (e.g. cash at a store). */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** Connects, loads the price and restores any earlier purchase. Safe to call repeatedly. */
    fun refresh() = whenReady {
        queryProductDetails()
        queryOwnedPurchases()
    }

    /** Opens Play's purchase sheet. Returns false if the product couldn't be loaded (offline, not set up). */
    fun purchase(activity: Activity): Boolean {
        val product = details ?: run { refresh(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build())
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (purchases != null) scope.launch { handle(purchases, isFullList = false) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> Unit // cancelled or error: nothing changes
        }
    }

    private fun whenReady(block: () -> Unit) {
        if (client.isReady) { block(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) block()
            }
            override fun onBillingServiceDisconnected() = Unit // reconnects on the next refresh()/purchase()
        })
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = productDetailsResult.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
                _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    private fun queryOwnedPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { handle(purchases, isFullList = true) }
        }
    }

    /**
     * [isFullList] is true for the restore query (everything the user owns). Then "not in the list"
     * means not owned, e.g. refunded, so the unlock is removed.
     */
    private suspend fun handle(purchases: List<Purchase>, isFullList: Boolean) {
        val mine = purchases.filter { PRODUCT_ID in it.products }
        _pending.value = mine.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        val owned = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        when {
            owned != null -> {
                // Unacknowledged purchases are refunded by Play after 3 days.
                if (!owned.isAcknowledged) {
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(owned.purchaseToken).build()
                    ) { }
                }
                settings.setRemoveAds(true)
            }
            isFullList -> settings.setRemoveAds(false)
        }
    }
}
