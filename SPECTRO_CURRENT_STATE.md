# SPECTRO_CURRENT_STATE.md

## Snapshot handoff
Base: **Spectro ERP 3.1.7 Beta 9**.
- versionCode 26
- versionName `3.1.7-beta9`
- applicationId `com.spectro.app`
- SQLite schema 9
- backup formato v8
- branch di sviluppo usato: `spectro-3.0-beta`

## Funzioni già presenti
Clienti, WorkOrder, produzioni multiple, timeline, criticità, magazzino, colli, movimenti, Cotone, altri ricavi/lavori, attività, ricerca globale, PDF, backup/restore e Zentha base (prodotti, ordini, righe, costi extra, pagamenti, split utile).

Zentha include inoltre il catalogo analitico del listino 13/10/2026, costo unitario scomposto, snapshot di codice/descrizione/varianti e tre PDF separati per contabilità, produzione e controllo completo.

Il flusso di autenticazione consente di reinviare autonomamente l’email di conferma da Android e dal portale web. I messaggi Supabase più comuni sono tradotti e ricordano di usare esclusivamente il link più recente, perché ogni reinvio rende obsoleti quelli precedenti.

La Beta 4 ripara il permesso di sincronizzazione degli Admin esistenti, aggiunge i preset Supervisore e Attività/Zentha senza prezzi, sanifica lato server il mirror web per chi non possiede `finance.view`, ripristina lo scorrimento di Altri moduli e aggiunge le scorciatoie alla dashboard.

La Beta 5 aggiunge la migrazione locale schema 7 → 8 e un controllo auto-riparante per `cloud_sync_state`. Corregge le installazioni esistenti in cui il database era già marcato schema 7 prima dell'introduzione della sincronizzazione cloud, senza riscrivere i record ERP.

La Beta 6 cloud elimina il limite pratico di 50 MB per l’intero workspace: archivio, foto e allegati vengono suddivisi automaticamente in blocchi da 45 MB, pubblicati con un manifest e verificati singolarmente e globalmente tramite SHA-256. I workspace cloud precedenti in formato 1 restano compatibili.

La Beta 7 rende persistente la sincronizzazione automatica: ogni modifica autorizzata marca il workspace come pendente, viene pubblicata una sola volta dopo un breve raggruppamento delle operazioni e, in assenza di rete, resta salvata sul dispositivo e viene ritentata. Il portale web v3 aggiunge KPI, filtri, tabelle analitiche, righe cliccabili e dossier record, mantenendo la rimozione server-side dei dati economici per i ruoli senza `finance.view`.

Il portale web v5 introduce la Control Tower desktop: navigazione laterale persistente, ricerca globale, indicatori direzionali, statistiche e grafici operativi, viste per stato e periodo, raggruppamenti analitici, dossier stampabili ed esportazione CSV. Le etichette applicative sono centralizzate in italiano. La gestione accessi usa una matrice per modulo con ricerca, modifiche temporanee e un unico salvataggio per utente, oltre ai preset sola lettura/consenti tutto/nega tutto. Le scritture dirette sui record ERP restano disabilitate finché la sincronizzazione Android non passa dal workspace completo a record versionati: abilitarle prima esporrebbe i dati a conflitti e sovrascritture.

Il portale web v6 aumenta la leggibilità dell’intera interfaccia, imposta le statistiche sul mese corrente e aggiunge intervalli predefiniti o personalizzati dal/al. Zentha mostra anche catalogo prodotti e listini rilevati e genera documenti dedicati per produzione, contabilità e dossier completo. L’amministratore può aprire un’anteprima effettiva dei permessi di un utente; i dati economici vengono rimossi anche localmente durante l’anteprima. Il login accetta le password esistenti da almeno 6 caratteri, mentre la registrazione continua a richiederne almeno 8.

Il portale web v7 aggiunge l’anteprima globale di qualunque ruolo anche in assenza di un account registrato, rende centrali i KPI in metri di carta e tessuto e introduce l’analisi Produzione per cliente e plotter. Il Centro esportazioni permette di scegliere report direzionale, operativo senza prezzi o analisi completa e genera un documento A4 impaginato con KPI, barre comparative e dettaglio delle produzioni filtrate.

Il portale web v8 corregge il blocco dello scorrimento causato dalla classe dei pannelli modali rimasta sul `body` durante la navigazione. Introduce inoltre moduli di modifica per i record autorizzati e la migrazione cloud 005: le modifiche web vengono conservate come overlay persistenti, applicate dal lettore sicuro e non possono essere sovrascritte dalle pubblicazioni complete Android. L’integrazione inversa degli overlay dentro il database Android resta una fase successiva.

Il portale web v9 ridisegna Attività come bacheca operativa: scadute e odierne, lavorazioni in corso e attività da pianificare vengono mostrate prima dell’archivio, ordinate automaticamente e aperte direttamente nel dossier. Introduce inoltre un motion system coerente per navigazione, KPI, schede e pannelli, con colori semantici e rispetto dell’impostazione di accessibilità `prefers-reduced-motion`.

Il portale web v10 stabilizza le animazioni eliminando lo spostamento laterale del menu, aggiunge calendario settimanale e trascinamento autorizzato delle attività tra pianificazione, lavorazione e completamento. Introduce inoltre centro notifiche, tavolozza comandi `Ctrl/Cmd + K` e un Centro personalizzazione locale per dispositivo con temi, cinque colori indipendenti, font, dimensione testo, densità, raggi, larghezza menu e livello delle animazioni. Le preferenze sono conservate solo nel browser e non vengono sincronizzate tra Android, Windows o Mac.

Il portale web v11 concentra le attività aperte in un grande primo piano cromatico “Attività da lavorare”, eliminando la colonna “In corso”. Riordina Produzione in Cliente, Data, Carta/Tessuto, Plotter e Disegni con codifica cromatica, rinomina il KPI economico in Fatturato e ricorda localmente l’ultima sezione visitata. Aggiunge transizioni native tra viste, illuminazione reattiva al puntatore, feedback a onda sui pulsanti e animazioni progressive per grafici e tabelle.

Il portale web v13 rende deterministico l’ordinamento cronologico in tutti i moduli, corregge il ripristino involontario della vista al ritorno sulla scheda e conserva anche la posizione di scorrimento per sezione. Il dossier produttivo dispone di scheda tecnica, annotazioni, galleria immagini privata e valutazione post-produzione con voto, criticità, problemi e nota finale, aggregate anche nelle statistiche mensili. Android Beta 8 pubblica gli allegati come oggetti cloud privati separati e inserisce nel mirror soltanto i relativi percorsi protetti; le anteprime web usano URL firmati temporanei.

Il portale web v14 corregge il blocco della pagina Produzione all’apertura di un ordine: il contenitore analitico non conserva più una trasformazione CSS capace di spostare fuori schermo il dossier a posizione fissa. Il blocco dello scorrimento della pagina principale viene inoltre applicato soltanto dopo che il dossier è stato montato e reso visibile.

Android Beta 9 forza una sola ripubblicazione degli allegati locali per le installazioni aggiornate da versioni che avevano già marcato il workspace come allineato. Ogni scrittura marca ora la sincronizzazione prima della modifica e pianifica anche un lavoro persistente vincolato alla rete: se l’app passa in background o viene chiusa, il trasferimento viene ripreso automaticamente. I trasferimenti avviati dalla UI e dal lavoro in background sono serializzati per evitare revisioni concorrenti.

## Beta 6
Ha introdotto navigazione contestuale, home cliente, produzioni giornaliere raggruppate, dossier ordine più completo e tema Spectro Neon.

## Stato build
Lo snapshot con autenticazione, permessi, sincronizzazione automatica e workspace cloud a blocchi viene verificato con `assembleDebug`, test unitari e pipeline APK RELEASE firmata. L’installazione su un dispositivo reale resta una verifica operativa da eseguire prima del rilascio.

## Bug aperto prioritario
Nel profilo cliente (esempio Goccia) lo storico mostra solo alcuni ordini, mentre esistono altre lavorazioni passate.
Causa individuata: la vista usa soprattutto `WorkOrder`, mentre molte produzioni storiche sono `ProductionRecord` legacy con `workOrderId == null`.
I dati non vanno inventati o duplicati: serve uno storico unificato.
