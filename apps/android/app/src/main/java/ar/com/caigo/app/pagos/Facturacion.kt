package ar.com.caigo.app.pagos

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Caigo Plus con Google Play Billing. Los productos son suscripciones con los mismos ids que en
 * App Store (`plus_semana`, `plus_mes`, `plus_temporada`); la compra lleva el id de la persona en
 * Caigo como obfuscatedAccountId, y el purchaseToken se manda a la API (`POST /v1/plus/google`),
 * que la verifica con la Play Developer API y la reconoce.
 */
class Facturacion(ctx: Context) {
    /** Un error para mostrarle a la persona (ya en criollo). */
    class ErrorPlay(mensaje: String) : Exception(mensaje)

    /** Lo que trae Play cuando se completa (o cancela) una compra. */
    sealed interface Resultado {
        data class Compras(val compras: List<Purchase>) : Resultado
        data object Cancelada : Resultado
        data class Fallo(val mensaje: String) : Resultado
    }

    private val _resultados = MutableSharedFlow<Resultado>(extraBufferCapacity = 8)
    val resultados: SharedFlow<Resultado> = _resultados

    private val cliente: BillingClient = BillingClient.newBuilder(ctx)
        .setListener { r, compras ->
            _resultados.tryEmit(
                when (r.responseCode) {
                    BillingClient.BillingResponseCode.OK -> Resultado.Compras(compras.orEmpty())
                    BillingClient.BillingResponseCode.USER_CANCELED -> Resultado.Cancelada
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> Resultado.Compras(emptyList())
                    else -> Resultado.Fallo(mensaje(r))
                },
            )
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private suspend fun conectar() {
        if (cliente.isReady) return
        val r = suspendCancellableCoroutine { cont ->
            cliente.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) {
                    if (cont.isActive) cont.resume(r)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED).build())
                }
            })
        }
        if (r.responseCode != BillingClient.BillingResponseCode.OK) throw ErrorPlay(mensaje(r))
    }

    /** Las suscripciones de Plus que Play tiene cargadas (puede ser vacía si todavía no existen). */
    suspend fun productos(ids: List<String>): List<ProductDetails> {
        conectar()
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build() },
        ).build()
        val r = cliente.queryProductDetails(params)
        if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) throw ErrorPlay(mensaje(r.billingResult))
        return r.productDetailsList.orEmpty()
    }

    /** Las compras de Plus vigentes en esta cuenta de Google (para "Restaurar compra"). */
    suspend fun compras(): List<Purchase> {
        conectar()
        val r = cliente.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) throw ErrorPlay(mensaje(r.billingResult))
        return r.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    }

    /** Abre la hoja de compra de Play. El resultado llega por [resultados]. */
    fun comprar(actividad: Activity, producto: ProductDetails, oferta: String, cuenta: String) {
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(producto).setOfferToken(oferta).build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(p))
            .apply { if (cuenta.isNotBlank()) setObfuscatedAccountId(cuenta) }
            .build()
        val r = cliente.launchBillingFlow(actividad, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) _resultados.tryEmit(Resultado.Fallo(mensaje(r)))
    }

    fun cerrar() = runCatching { cliente.endConnection() }

    companion object {
        val PRODUCTOS = listOf("plus_semana", "plus_mes", "plus_temporada")

        /** La oferta a usar: la de prueba gratis si hay, si no el plan base. */
        fun oferta(p: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
            val ofertas = p.subscriptionOfferDetails.orEmpty()
            return ofertas.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
                ?: ofertas.firstOrNull { it.offerId == null }
                ?: ofertas.firstOrNull()
        }

        /** Días de prueba gratis de la oferta (0 si no tiene). */
        fun diasGratis(o: ProductDetails.SubscriptionOfferDetails?): Int {
            val fase = o?.pricingPhases?.pricingPhaseList?.firstOrNull { it.priceAmountMicros == 0L } ?: return 0
            return runCatching { java.time.Period.parse(fase.billingPeriod).let { it.days + it.months * 30 + it.years * 365 } }.getOrDefault(0)
        }

        fun mensaje(r: BillingResult): String = when (r.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play no está disponible en este teléfono. Revisá que tengas una cuenta de Google."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Caigo Plus todavía no está disponible en Google Play."
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.NETWORK_ERROR -> "No pudimos hablar con Google Play. Probá de nuevo en un rato."
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "Caigo Plus todavía no está disponible en Google Play."
            else -> r.debugMessage.ifBlank { "Google Play no pudo completar la compra." }
        }
    }
}
