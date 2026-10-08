# SPECTRO_BETA7_SCOPE.md

# A. Storico Cliente Unificato

## Problema
Cliente Goccia mostra solo pochi ordini, ma esistono altre produzioni storiche.

## Requisiti
- Profilo Cliente -> Ordini/Storico mostra WorkOrder moderni + produzioni legacy dello stesso cliente con `workOrderId == null`.
- Nessun duplicato.
- Ordinamento recente -> vecchio.
- Ricerca per titolo/nome ordine, codice/riferimento, tessuto, disegno e data.
- WorkOrder: nome in primo piano, tessuti/metri in MAIUSCOLO e grassetto, stato, data, criticità, produzioni, note e immagini.
- Legacy: etichetta `Produzione storica`, data, riferimento, tessuto, metri, disegni, note, immagini, ricavi/utili se disponibili.
- Tutto apribile e PDF esportabile.
- Statistiche senza doppio conteggio.

Acceptance test: 2 WorkOrder Goccia + 10 ProductionRecord Goccia, di cui 3 collegati ai WorkOrder e 7 legacy => visualizzare 2 ordini moderni + 7 voci legacy.

# B. Zentha ERP 2.0

## Home
Dashboard, Ordini, Clienti, Magazzino, Prodotti, Listini, Produzione, Pagamenti, Statistiche.

## Prodotti e varianti
Nome, SKU, categoria, linea, descrizione, foto, attivo, costo/prezzo standard.
Varianti per taglia, colore, Uomo/Donna/Junior, Standard/Double/Reverse/Altro e SKU variante.

## Costi
Costo base + stampa + confezione + accessori + packaging + altri costi = costo reale.

## Listini
Listini Standard, Società Sportive, Rivenditori, VIP e personalizzati.
Fasce quantità configurabili (es. 1-9, 10-19, 20-49, 50+), non hardcoded.
Assegnazione listino al cliente e override prezzo specifico cliente.

## Magazzino Zentha
Separato dal magazzino tessile: prodotto -> variante/taglia/colore -> quantità.
Movimenti obbligatori: Carico, Uso ordine, Reso, Rettifica, Campione, Scarto.
Soglia scorta minima e alert.

## Ordini
Cliente/società, contatto, data, consegna, documenti, mockup, loghi, note.
Righe prodotto con variante, quantità, prezzo e costo snapshot, ricavo/costo/margine.

## Distinta squadra
Cognome/nome, numero, taglia maglia, taglia pantalone, note e prodotto/kit opzionale. Modificabile ed esportabile PDF; predisporre export Excel futuro.

## Personalizzazioni
Nome/cognome, numero, sponsor, logo, colori, grafica, vestibilità, note produzione, mockup approvato e allegati.

## Workflow
RICEVUTO -> DATI INCOMPLETI -> GRAFICA -> BOZZA INVIATA -> ATTESA APPROVAZIONE -> APPROVATO -> IN PRODUZIONE -> CONTROLLO QUALITÀ -> PRONTO -> CONSEGNATO -> CHIUSO.
Ogni passaggio salva data/ora.

## Dashboard
Ordini aperti, attesa approvazione, produzione, ritardi, pronti, scorte basse, da incassare, utile mese.

## Economia
Ricavi, sconti, trasporto/altro ricavo, costi prodotti, personalizzazioni, packaging, trasporto, altri costi, utile, margine %, split utile configurabile default 50/50.

## Pagamenti
Totale, pagato, residuo, pagamenti multipli, metodo, data, note. Stati: NON PAGATO, ACCONTO, PARZIALE, PAGATO.

## PDF
Conferma ordine, distinta squadra, scheda produzione, packing list, riepilogo taglie, report economico, storico ordine. Tema nero/fucsia/arancio con KPI e grafici.

## Statistiche
Vendite mese/anno, clienti, prodotti più venduti, taglie, margine per prodotto/ordine, tempo medio ordine-consegna, valore stock, pagato/da incassare.

## Compatibilità
Preservare e migrare gli attuali ZenthaProduct, ZenthaOrder, ZenthaOrderItem, ZenthaExtraCost e ZenthaPayment. Non azzerare dati esistenti.

# C. Cloud futuro
Non implementare ancora backend cloud completo in Beta 7, ma evitare scelte incompatibili con UUID, audit, ruoli, sync, soft-delete e web app.
