# SPECTRO_DATABASE.md

## Database attuale
File `spectro_native.db`, schema version 11.

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
Il passaggio schema 5 -> 6 è additivo: crea `cloud_sync_state` per device UUID, revisione base, ultima revisione remota osservata, data sync, errore e percorso del backup di recupero. I dati ERP non vengono riscritti.

Il passaggio schema 7 -> 8 ripete in modo idempotente la sola creazione di `cloud_sync_state`. Serve a correggere le installazioni che erano già marcate schema 7 quando la sincronizzazione cloud è stata aggiunta; nessuna tabella operativa viene cancellata o ricreata.
Tutte le nuove entità devono entrare nel backup/restore.

### Cloud 011 — conservazione Storage

La migrazione Supabase `202610300011_deduplicated_storage_retention.sql` è additiva: memorizza nel riepilogo workspace i blocchi e gli allegati attivi e introduce `publish_workspace_snapshot_v3`. Gli allegati usano percorsi content-addressed `attachments/by-sha256/<digest>`, quindi lo stesso file fisico non viene duplicato tra record o revisioni. La funzione non cancella direttamente `storage.objects`: restituisce all'app solo i percorsi obsoleti della stessa organizzazione e la rimozione avviene tramite Storage API dopo il commit riuscito.

### Schema 7 — catalogo e documenti Zentha

Migrazione esclusivamente additiva:

- aggiunge a `zentha_products` la distinta costi (tessuto, costa, fodera/inserti, stampa, accessori, taglio, cucito/confezione, etichetta, imballo e altri costi), il nome listino, la percentuale di ricarico e il riferimento sorgente;
- aggiunge a `zentha_order_items` gli snapshot di codice e descrizione, variante, colore, ripartizione taglie, personalizzazioni e note produzione;
- inserisce in modo idempotente i 14 codici del listino `COSTI ZHENTA 13-10-2026`, senza aggiornare o sovrascrivere un codice già presente;
- porta il backup al formato v8, mantenendo importabili i formati precedenti.
