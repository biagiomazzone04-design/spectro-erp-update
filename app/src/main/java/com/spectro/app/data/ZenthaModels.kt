package com.spectro.app.data

enum class ZenthaMovementType { LOAD, ORDER_USE, RETURN, ADJUSTMENT, SAMPLE, WASTE }
enum class ZenthaPaymentState { UNPAID, DEPOSIT, PARTIAL, PAID }

data class ZenthaProductVariant(
    val id: Long = 0, val productId: Long, val sku: String = "", val size: String = "",
    val color: String = "", val fit: String = "Uomo", val variantType: String = "Standard", val active: Boolean = true
) { val label: String get() = listOf(size, color, fit, variantType).filter { it.isNotBlank() }.joinToString(" • ") }

data class ZenthaStockBalance(val id: Long = 0, val variantId: Long, val quantityAvailable: Int = 0, val minimumQuantity: Int = 0, val location: String = "")
/** Signed delta: outbound quantities are negative. Movements are append-only. */
data class ZenthaStockMovement(val id: Long = 0, val variantId: Long, val type: ZenthaMovementType, val quantity: Int, val dateTime: String, val orderId: Long? = null, val note: String = "")
data class ZenthaPriceList(val id: Long = 0, val name: String, val description: String = "", val active: Boolean = true)
/** clientId overrides list pricing; at least one of priceListId/clientId must be set. */
data class ZenthaPriceListRule(val id: Long = 0, val priceListId: Long? = null, val productId: Long, val variantId: Long? = null, val minQty: Int = 1, val maxQty: Int? = null, val unitPrice: Double, val clientId: Long? = null)
data class ZenthaClientProfile(val id: Long = 0, val clientId: Long, val assignedPriceListId: Long? = null, val notes: String = "")
data class ZenthaRosterEntry(val id: Long = 0, val orderId: Long, val surname: String, val name: String = "", val number: String = "", val shirtSize: String = "", val shortsSize: String = "", val notes: String = "", val productId: Long? = null)
data class ZenthaCustomization(val id: Long = 0, val orderId: Long, val orderItemId: Long? = null, val type: String = "Grafica", val value: String = "", val notes: String = "", val approved: Boolean = false, val attachments: List<String> = emptyList())
data class ZenthaOrderEvent(val id: Long = 0, val orderId: Long, val dateTime: String, val fromStatus: String = "", val toStatus: String, val note: String = "")

data class ZenthaData(
    val variants: List<ZenthaProductVariant> = emptyList(),
    val balances: List<ZenthaStockBalance> = emptyList(),
    val movements: List<ZenthaStockMovement> = emptyList(),
    val priceLists: List<ZenthaPriceList> = emptyList(),
    val priceRules: List<ZenthaPriceListRule> = emptyList(),
    val clientProfiles: List<ZenthaClientProfile> = emptyList(),
    val roster: List<ZenthaRosterEntry> = emptyList(),
    val customizations: List<ZenthaCustomization> = emptyList(),
    val events: List<ZenthaOrderEvent> = emptyList()
)

data class ZenthaTotals(
    val revenue: Double, val productCosts: Double, val extraCosts: Double, val profit: Double,
    val shareA: Double, val shareB: Double, val paid: Double, val outstanding: Double,
    val marginPercent: Double, val paymentState: ZenthaPaymentState, val overpayment: Double = 0.0
)

fun ZenthaOrderStatus.isFinished(): Boolean = this in setOf(ZenthaOrderStatus.DELIVERED, ZenthaOrderStatus.CLOSED, ZenthaOrderStatus.CANCELLED)
fun ZenthaOrderStatus.label(): String = when (this) {
    ZenthaOrderStatus.DRAFT -> "Bozza (legacy)"
    ZenthaOrderStatus.CONFIRMED -> "Confermato (legacy)"
    ZenthaOrderStatus.RECEIVED -> "Ricevuto"
    ZenthaOrderStatus.INCOMPLETE_DATA -> "Dati incompleti"
    ZenthaOrderStatus.GRAPHICS -> "Grafica"
    ZenthaOrderStatus.PROOF_SENT -> "Bozza inviata"
    ZenthaOrderStatus.WAITING_APPROVAL -> "Attesa approvazione"
    ZenthaOrderStatus.APPROVED -> "Approvato"
    ZenthaOrderStatus.PRODUCTION -> "In produzione"
    ZenthaOrderStatus.QUALITY_CHECK -> "Controllo qualità"
    ZenthaOrderStatus.READY -> "Pronto"
    ZenthaOrderStatus.DELIVERED -> "Consegnato"
    ZenthaOrderStatus.CLOSED -> "Chiuso"
    ZenthaOrderStatus.CANCELLED -> "Annullato"
}
val zenthaWorkflow = listOf(ZenthaOrderStatus.RECEIVED, ZenthaOrderStatus.INCOMPLETE_DATA, ZenthaOrderStatus.GRAPHICS, ZenthaOrderStatus.PROOF_SENT, ZenthaOrderStatus.WAITING_APPROVAL, ZenthaOrderStatus.APPROVED, ZenthaOrderStatus.PRODUCTION, ZenthaOrderStatus.QUALITY_CHECK, ZenthaOrderStatus.READY, ZenthaOrderStatus.DELIVERED, ZenthaOrderStatus.CLOSED)
fun ZenthaMovementType.label() = when(this) { ZenthaMovementType.LOAD -> "Carico"; ZenthaMovementType.ORDER_USE -> "Uso ordine"; ZenthaMovementType.RETURN -> "Reso"; ZenthaMovementType.ADJUSTMENT -> "Rettifica"; ZenthaMovementType.SAMPLE -> "Campione"; ZenthaMovementType.WASTE -> "Scarto" }
fun ZenthaPaymentState.label() = when(this) { ZenthaPaymentState.UNPAID -> "NON PAGATO"; ZenthaPaymentState.DEPOSIT -> "ACCONTO"; ZenthaPaymentState.PARTIAL -> "PARZIALE"; ZenthaPaymentState.PAID -> "PAGATO" }
