# SPECTRO_DATABASE.md

## Database attuale
File `spectro_native.db`, schema version 5.

Entità principali: Client, InventoryItem, FabricRoll, StockMovement, RollUsage, ProductionRecord, WorkOrder, OrderTimelineEvent, ClientIssue, IssueUpdate, PaymentDeadline, CottonOrder, EconomicWork, OperationalTask, ZenthaProduct, ZenthaOrder, ZenthaOrderItem, ZenthaExtraCost, ZenthaPayment.

## Bug storico cliente
`ProductionRecord.workOrderId` può essere nullo. Le produzioni legacy senza WorkOrder devono diventare consultabili senza essere duplicate o convertite in dati fittizi.

Campi legacy da mostrare quando presenti: date, client, orderRef, designs, fabricDisplayName/manualFabricName, fabricUsedMeters, printedPaperMeters, technicalNotes, images, grossRevenue, grossProfit.

## Nuove strutture Zentha consigliate per Beta 7
- ZenthaProductVariant: productId, sku, size, color, fit, variantType, active.
- ZenthaStockBalance: variantId, quantityAvailable, minimumQuantity, location opzionale.
- ZenthaStockMovement: LOAD, ORDER_USE, RETURN, ADJUSTMENT, SAMPLE, WASTE; quantity, dateTime, orderId, note.
- ZenthaPriceList: name, description, active.
- ZenthaPriceListRule: priceListId, product/variant, minQty, maxQty, unitPrice.
- ZenthaClientProfile: clientId, assignedPriceListId, notes.
- ZenthaOrderLineVariant: orderItemId, variantId, quantity, unitPriceSnapshot, unitCostSnapshot.
- ZenthaRosterEntry: orderId, surname/name, number, shirtSize, shortsSize, notes.
- ZenthaCustomization: order/orderItem, type, value/notes, attachments.
- Zentha order timeline/workflow persistente.

## Migrazione
Probabile passaggio schema 5 -> 6. Deve essere non distruttivo, idempotente e testato con DB schema 5 esistente.
Tutte le nuove entità devono entrare nel backup/restore.
