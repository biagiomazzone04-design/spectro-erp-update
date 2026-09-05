package com.spectro.app.data

enum class RecordType { PRODUCTION, NEGATIVE }

enum class WorkStatus {
    IN_PROGRESS, COMPLETED,
    RECEIVED, WAITING_MATERIAL, WAITING_CLIENT, IN_PRODUCTION,
    PRINTED, TRANSFERRED, READY, DELIVERED, ON_HOLD, BLOCKED
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
    val minimumQuantityMeters: Double = 0.0
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
    val grossProfit: Double = 0.0
)

data class WorkOrder(
    val id: Long = 0,
    val receivedDate: String,
    val client: String,
    val title: String,
    val description: String = "",
    val images: List<String> = emptyList(),
    val status: WorkStatus = WorkStatus.RECEIVED,
    val dueDate: String = "",
    val priority: Priority = Priority.NORMAL,
    val blocker: Blocker = Blocker.NONE
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

data class AppSettings(
    val dailyFixedCost: Double = 760.0,
    val plotterX1Cost: Double = 0.80,
    val plotterX2Cost: Double = 1.00,
    val protectionPaperCost: Double = 0.10,
    val themeKey: String = "spectro"
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

data class SpectroSnapshot(
    val clients: List<Client> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val production: List<ProductionRecord> = emptyList(),
    val workOrders: List<WorkOrder> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val clientIssues: List<ClientIssue> = emptyList(),
    val issueUpdates: List<IssueUpdate> = emptyList(),
    val payments: List<PaymentDeadline> = emptyList()
)
