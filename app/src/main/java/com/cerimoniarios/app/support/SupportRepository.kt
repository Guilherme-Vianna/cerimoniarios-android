package com.cerimoniarios.app.support

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.cerimoniarios.app.AppPreferences
import com.cerimoniarios.app.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SupportTier(
    val productId: String,
    val price: String,
    val priceMicros: Long,
    val owned: Boolean,
    val pending: Boolean,
    internal val details: ProductDetails,
)

data class SupportState(
    val connection: Connection = Connection.Connecting,
    val tiers: List<SupportTier> = emptyList(),
    val isSupporter: Boolean = false,
) {
    enum class Connection { Connecting, Ready, Unavailable }
}

/**
 * One-time, non-consumable Play products. Owning any of them makes the account a supporter
 * (no ads), which survives reinstalls because Play keeps returning owned products.
 */
class SupportRepository(context: Context, private val preferences: AppPreferences) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _state = MutableStateFlow(SupportState(isSupporter = preferences.cachedSupporter))
    val state: StateFlow<SupportState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    private val lock = Any()
    private var products: List<ProductDetails> = emptyList()
    private var purchases: List<Purchase> = emptyList()
    private var purchasesLoaded = false

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        _state.update { it.copy(connection = SupportState.Connection.Connecting) }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    _state.update { it.copy(connection = SupportState.Connection.Ready) }
                    refresh()
                } else {
                    _state.update { it.copy(connection = SupportState.Connection.Unavailable) }
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun refresh() {
        if (!client.isReady) {
            if (_state.value.connection == SupportState.Connection.Unavailable) connect()
            return
        }
        queryProducts()
        queryPurchases()
    }

    fun purchase(activity: Activity, tier: SupportTier) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(tier.details).build()),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        when (result.responseCode) {
            BillingResponseCode.OK, BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> _messages.tryEmit(R.string.support_error_message)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, updated: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val newPurchases = updated.orEmpty()
                synchronized(lock) {
                    val tokens = newPurchases.map { it.purchaseToken }.toSet()
                    purchases = purchases.filterNot { it.purchaseToken in tokens } + newPurchases
                }
                newPurchases.forEach(::acknowledgeIfNeeded)
                notifyUser(newPurchases)
                publish()
            }
            BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> _messages.tryEmit(R.string.support_error_message)
        }
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCT_IDS.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.INAPP).build()
                },
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            synchronized(lock) { products = details.productDetailsList }
            publish()
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, owned ->
            if (result.responseCode != BillingResponseCode.OK) return@queryPurchasesAsync
            synchronized(lock) {
                purchases = owned
                purchasesLoaded = true
            }
            owned.forEach(::acknowledgeIfNeeded)
            publish()
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        // Unacknowledged purchases are refunded by Play after 3 days; failures are retried on the next refresh.
        client.acknowledgePurchase(params) { }
    }

    private fun notifyUser(newPurchases: List<Purchase>) {
        @StringRes val message = when {
            newPurchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> R.string.support_thanks_message
            newPurchases.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> R.string.support_pending_message
            else -> return
        }
        _messages.tryEmit(message)
    }

    private fun publish() {
        val next = synchronized(lock) {
            val completed = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            val pending = purchases.filter { it.purchaseState == Purchase.PurchaseState.PENDING }
            val supporter = if (purchasesLoaded || completed.isNotEmpty()) completed.isNotEmpty() else preferences.cachedSupporter
            val tiers = products.mapNotNull { details ->
                val offer = details.oneTimePurchaseOfferDetails ?: return@mapNotNull null
                SupportTier(
                    productId = details.productId,
                    price = offer.formattedPrice,
                    priceMicros = offer.priceAmountMicros,
                    owned = completed.any { details.productId in it.products },
                    pending = pending.any { details.productId in it.products },
                    details = details,
                )
            }.sortedBy { it.priceMicros }
            preferences.cachedSupporter = supporter
            SupportState(SupportState.Connection.Ready, tiers, supporter)
        }
        _state.value = next
    }

    companion object {
        /** Create these as one-time products in Play Console (Monetize → Products → In-app products). */
        val PRODUCT_IDS = listOf("apoio_2", "apoio_5", "apoio_10", "apoio_20", "apoio_50")
    }
}
