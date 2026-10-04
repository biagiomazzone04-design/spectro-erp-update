package com.spectro.app.data

enum class RecordType { PRODUCTION, NEGATIVE }

enum class WorkStatus {
    DRAFT, RECEIVED, IN_PROCESS, TRANSFERRED_TO_PRODUCTION,
    WAITING_MATERIAL, WAITING_CLIENT, IN_PRODUCTION, IN_PROGRESS,
    PRINTED, TRANSFERRED, READY, COMPLETED, DELIVERED, CLOSED,
    ON_HOLD, BLOCKED, CANCELLED
}

enum class Priority { NORMAL, HIGH }
enum class Blocker { NONE, WAITING_CLIENT, WAITING_MATERIAL }
enum class FabricMode { NONE, INVENTORY, MANUAL }
enum class OwnerType { CLIENT, HOUSE }

enum class IssueCategory { GRAPHICS, APPROVAL, MATERIAL, ORDER_CHANGE, PAYMENT, QUALITY, INFORMATION, OTHER }
enum class IssueResponsibility { CLIENT, INTERNAL, SUPPLIER, MIXED, TO_VERIFY }
enum class IssueSeverity { LOW, MEDIUM, HIGH, CRITICAL }
enum class IssueStatus { OPEN, WAITING_CLIENT, MONITORING, RESOLVED, CLOSED }
enum class PaymentMethod { UNSPECIFIED, BANK_TRANSFER, CASH, CHECK, CARD, OTHER }

enum class RollStatus { INTACT, OPEN, DEPLETED, BLOCKED }
enum class StockMovementType { LOAD, ORDER_USE, RETURN, ADJUSTMENT, WASTE }
enum class OrderKind { WORK, PRODUCTION, COTTON, ZENTHA }

enum class TimelineCategory {
    NOTE, CLIENT, INTERNAL_PROBLEM, GRAPHICS, FILE_ERROR, MATERIAL,
    SAMPLE, APPROVAL, PRODUCTION, ORDER_CHANGE, DELAY, DELIVERY, STATUS_CHANGE
}
enum class TimelineResponsibility { CLIENT, INTERNAL, SUPPLIER, NONE }
enum class TimelineSeverity { INFO, LOW, MEDIUM, HIGH, BLOCKING }
enum class TimelineStatus { OPEN, RESOLVED }

enum class CottonPrintType { REACTIVE, PIGMENT, OTHER }

enum class EconomicWorkStatus { OPEN, COMPLETED, PAID, CANCELLED }
enum class OperationalTaskType { SAMPLE, GRAPHICS_SEARCH, CLIENT_REQUEST, PREPARATION, OTHER }
enum class OperationalTaskStatus { TODO, IN_PROGRESS, DONE, CANCELLED }

enum class ZenthaOrderStatus {
    DRAFT, CONFIRMED, GRAPHICS, WAITING_APPROVAL, PRODUCTION, READY, DELIVERED, CLOSED, CANCELLED
}

data class Client(
    val id: Long = 0,
    val name: String,
    val defaultPrintPrice: Double = 0.0
)

data class InventoryItem(
    val id: Long = 0,
    val ownerType: OwnerType = OwnerType.CLIENT,
    val ownerName: String,
    val fabricName: String,
    val quantityMeters: Double,
    val purchasePricePerMeter: Double = 0.0,
    val createdDate: String,
    val images: List<String> = emptyList(),
    val minimumQuantityMeters: Double = 0.0,
    val supplierName: String = "",
    val composition: String = "",
    val weightGsm: String = "",
    val variant: String = "",
    val lotCode: String = ""
)

data class FabricRoll(
    val id: Long = 0,
    val inventoryId: Long,
    val rollCode: String,
    val initialMeters: Double,
    val availableMeters: Double,
    val createdDate: String,
    val note: String = "",
    val status: RollStatus = RollStatus.INTACT,
    val images: List<String> = emptyList()
)

data class FabricRollInput(
    val meters: Double,
    val images: List<String> = emptyList(),
    val code: String = "",
    val note: String = ""
)

data class StockMovement(
    val id: Long = 0,
    val inventoryId: Long,
    val rollId: Long,
    val type: StockMovementType,
    val dateTime: String,
    val meters: Double,
    val orderKind: OrderKind? = null,
    val orderId: Long? = null,
    val orderRef: String = "",
    val note: String = ""
)

data class RollUsage(
    val id: Long = 0,
    val orderKind: OrderKind,
    val orderId: Long,
    val orderRef: String,
    val inventoryId: Long,
    val rollId: Long,
    val meters: Double,
    val createdAt: String
)

data class ProductionRecord(
    val id: Long = 0,
    val type: RecordType = RecordType.PRODUCTION,
    val date: String,
    val clientId: Long? = null,
    val clientName: String = "",
    val orderRef: String = "",
    val designs: List<String> = emptyList(),
    val printPricePerMeter: Double = 0.0,
    val plotter: String = "X1",
    val printedPaperMeters: Double = 0.0,
    val protectionPaperMeters: Double = 0.0,
    val paperWasteMeters: Double = 0.0,
    val fabricMode: FabricMode = FabricMode.NONE,
    val fabricId: Long? = null,
    val fabricUsedMeters: Double = 0.0,
    val fabricWasteMeters: Double = 0.0,
    val fabricSalePricePerMeter: Double = 0.0,
    val manualFabricName: String = "",
    val fabricDisplayName: String = "Nessuno",
    val technicalNotes: String = "",
    val images: List<String> = emptyList(),
    val refundCost: Double = 0.0,
    val fabricLossCost: Double = 0.0,
    val applyFixedCosts: Boolean = true,
    val paperPrintCost: Double = 0.0,
    val protectionPaperCost: Double = 0.0,
    val fabricUsedCost: Double = 0.0,
    val materialCostTotal: Double = 0.0,
    val grossRevenue: Double = 0.0,
    val grossProfit: Double = 0.0,
    val workOrderId: Long? = null
)

data class WorkOrder(
    val id: Long = 0,
    val receivedDate: String,
    val client: String,
    val title: String,
    val description: String = "",
    val images: List<String> = emptyList(),
    val status: WorkStatus = WorkStatus.IN_PROCESS,
    val dueDate: String = "",
    val priority: Priority = Priority.NORMAL,
    val blocker: Blocker = Blocker.NONE,
    val clientId: Long? = null,
    val orderCode: String = "",
    val fabricInventoryId: Long? = null,
    val orderedMeters: Double = 0.0,
    val printPricePerMeter: Double = 0.0,
    val designCode: String = "",
    val productionRecordId: Long? = null,
    val effectiveDeliveryDate: String = "",
    val cancelReason: String = ""
)

data class OrderTimelineEvent(
    val id: Long = 0,
    val workOrderId: Long,
    val clientId: Long? = null,
    val clientName: String,
    val orderRef: String,
    val date: String,
    val time: String = "",
    val category: TimelineCategory = TimelineCategory.NOTE,
    val responsibility: TimelineResponsibility = TimelineResponsibility.NONE,
    val severity: TimelineSeverity = TimelineSeverity.INFO,
    val status: TimelineStatus = TimelineStatus.OPEN,
    val title: String,
    val description: String = "",
    val oldDueDate: String = "",
    val newDueDate: String = "",
    val images: List<String> = emptyList(),
    val resolutionNote: String = "",
    val resolvedDate: String = ""
)

data class ClientIssue(
    val id: Long = 0,
    val clientId: Long? = null,
    val clientName: String,
    val orderRef: String = "",
    val title: String,
    val category: IssueCategory = IssueCategory.OTHER,
    val responsibility: IssueResponsibility = IssueResponsibility.TO_VERIFY,
    val severity: IssueSeverity = IssueSeverity.MEDIUM,
    val status: IssueStatus = IssueStatus.OPEN,
    val openedDate: String,
    val expectedDeliveryDate: String = "",
    val revisedDeliveryDate: String = "",
    val delayDays: Int = 0,
    val description: String = "",
    val internalNotes: String = "",
    val evidenceImages: List<String> = emptyList()
)

data class IssueUpdate(
    val id: Long = 0,
    val issueId: Long,
    val date: String,
    val note: String,
    val images: List<String> = emptyList()
)

data class PaymentDeadline(
    val id: Long = 0,
    val clientId: Long? = null,
    val clientName: String,
    val orderRef: String = "",
    val amount: Double,
    val dueDate: String,
    val paid: Boolean = false,
    val paidDate: String = "",
    val method: PaymentMethod = PaymentMethod.UNSPECIFIED,
    val notes: String = ""
)

data class CottonOrder(
    val id: Long = 0,
    val orderCode: String = "",
    val clientId: Long? = null,
    val clientName: String,
    val orderDate: String,
    val dueDate: String = "",
    val fabricName: String,
    val printType: CottonPrintType = CottonPrintType.REACTIVE,
    val orderedMeters: Double,
    val actualProducedMeters: Double = 0.0,
    val deliveredMeters: Double = 0.0,
    val defectiveMeters: Double = 0.0,
    val sampleMeters: Double = 0.0,
    val leftoverMeters: Double = 0.0,
    val productionCostPerMeter: Double,
    val printPricePerMeter: Double,
    val fabricOwnerType: OwnerType = OwnerType.CLIENT,
    val inventoryId: Long? = null,
    val fabricCostPerMeter: Double = 0.0,
    val notes: String = "",
    val status: WorkStatus = WorkStatus.IN_PROCESS,
    val title: String = "",
    val images: List<String> = emptyList()
) {
    val revenueMeters: Double get() = if (deliveredMeters > 0) deliveredMeters else orderedMeters
    val costMeters: Double get() = if (actualProducedMeters > 0) actualProducedMeters else orderedMeters
    val grossRevenue: Double get() = revenueMeters * printPricePerMeter
    val productionCost: Double get() = costMeters * productionCostPerMeter
    val fabricCost: Double get() = if (fabricOwnerType == OwnerType.HOUSE) costMeters * fabricCostPerMeter else 0.0
    val totalCost: Double get() = productionCost + fabricCost
    val margin: Double get() = grossRevenue - totalCost
    val marginPercent: Double get() = if (grossRevenue <= 0) 0.0 else margin / grossRevenue * 100.0
}

data class EconomicMaterialUsage(
    val name: String,
    val quantity: Double,
    val unitCost: Double
) {
    val totalCost: Double get() = quantity * unitCost
}

data class EconomicWork(
    val id: Long = 0,
    val date: String,
    val clientId: Long? = null,
    val clientName: String,
    val title: String,
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val manualGrossRevenue: Double = 0.0,
    val materials: List<EconomicMaterialUsage> = emptyList(),
    val otherCosts: Double = 0.0,
    val notes: String = "",
    val images: List<String> = emptyList(),
    val status: EconomicWorkStatus = EconomicWorkStatus.OPEN
) {
    val grossRevenue: Double get() = if (manualGrossRevenue > 0) manualGrossRevenue else quantity * unitPrice
    val materialCosts: Double get() = materials.sumOf { it.totalCost }
    val totalCosts: Double get() = materialCosts + otherCosts
    val netProfit: Double get() = grossRevenue - totalCosts
}

data class OperationalTask(
    val id: Long = 0,
    val createdDate: String,
    val dueDate: String = "",
    val clientId: Long? = null,
    val clientName: String = "",
    val workOrderId: Long? = null,
    val title: String,
    val type: OperationalTaskType = OperationalTaskType.OTHER,
    val description: String = "",
    val priority: Priority = Priority.NORMAL,
    val status: OperationalTaskStatus = OperationalTaskStatus.TODO,
    val images: List<String> = emptyList()
)

data class ZenthaProduct(
    val id: Long = 0,
    val name: String,
    val sku: String = "",
    val category: String = "",
    val standardCost: Double,
    val standardPrice: Double,
    val description: String = "",
    val imagePath: String = "",
    val active: Boolean = true
)

data class ZenthaOrder(
    val id: Long = 0,
    val orderCode: String = "",
    val clientId: Long? = null,
    val clientName: String,
    val orderDate: String,
    val dueDate: String = "",
    val status: ZenthaOrderStatus = ZenthaOrderStatus.CONFIRMED,
    val notes: String = "",
    val attachments: List<String> = emptyList(),
    val splitPercentA: Double = 50.0,
    val splitPercentB: Double = 50.0
)

data class ZenthaOrderItem(
    val id: Long = 0,
    val orderId: Long,
    val productId: Long? = null,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val unitCost: Double
) {
    val revenue: Double get() = quantity * unitPrice
    val cost: Double get() = quantity * unitCost
    val margin: Double get() = revenue - cost
}

data class ZenthaExtraCost(
    val id: Long = 0,
    val orderId: Long,
    val label: String,
    val amount: Double
)

data class ZenthaPayment(
    val id: Long = 0,
    val orderId: Long,
    val date: String,
    val amount: Double,
    val method: PaymentMethod = PaymentMethod.UNSPECIFIED,
    val notes: String = ""
)

data class AppSettings(
    val dailyFixedCost: Double = 760.0,
    val plotterX1Cost: Double = 0.80,
    val plotterX2Cost: Double = 1.00,
    val protectionPaperCost: Double = 0.10,
    val themeKey: String = "spectro",
    val zenthaSplitA: Double = 50.0,
    val zenthaSplitB: Double = 50.0
)

data class DashboardDay(
    val date: String,
    val revenue: Double,
    val materialCosts: Double,
    val fixedCosts: Double,
    val netProfit: Double,
    val records: List<ProductionRecord>
)

data class AnalyticsSlice(val label: String, val value: Double)


data class ClientMonthlyStat(
    val clientName: String,
    val revenue: Double,
    val paperMeters: Double,
    val cottonMeters: Double,
    val profit: Double,
    val records: Int,
    val criticalities: Int = 0,
    val otherRevenue: Double = 0.0
)

data class MonthlyStats(
    val month: String,
    val productionRevenue: Double,
    val cottonRevenue: Double,
    val materialCosts: Double,
    val cottonCosts: Double,
    val fixedCosts: Double,
    val totalRevenue: Double,
    val totalCosts: Double,
    val netProfit: Double,
    val printedPaperMeters: Double,
    val cottonMeters: Double,
    val productionCount: Int,
    val cottonOrderCount: Int,
    val clients: List<ClientMonthlyStat>,
    val otherRevenue: Double = 0.0,
    val otherCosts: Double = 0.0,
    val otherNetProfit: Double = 0.0,
    val economicWorkCount: Int = 0,
    val criticalityCount: Int = 0
)

data class SpectroSnapshot(
    val clients: List<Client> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val production: List<ProductionRecord> = emptyList(),
    val workOrders: List<WorkOrder> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val clientIssues: List<ClientIssue> = emptyList(),
    val issueUpdates: List<IssueUpdate> = emptyList(),
    val payments: List<PaymentDeadline> = emptyList(),
    val fabricRolls: List<FabricRoll> = emptyList(),
    val stockMovements: List<StockMovement> = emptyList(),
    val rollUsages: List<RollUsage> = emptyList(),
    val timelineEvents: List<OrderTimelineEvent> = emptyList(),
    val cottonOrders: List<CottonOrder> = emptyList(),
    val zenthaProducts: List<ZenthaProduct> = emptyList(),
    val zenthaOrders: List<ZenthaOrder> = emptyList(),
    val zenthaItems: List<ZenthaOrderItem> = emptyList(),
    val zenthaExtraCosts: List<ZenthaExtraCost> = emptyList(),
    val zenthaPayments: List<ZenthaPayment> = emptyList(),
    val economicWorks: List<EconomicWork> = emptyList(),
    val operationalTasks: List<OperationalTask> = emptyList()
)
