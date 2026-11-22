# Analisi Spectro ERP — sincronizzazione Zentha Android ↔ portale web (Beta 18)

Analisi del ramo `spectro-3.0-beta` (base Android 3.2.7 Beta 17, cloud 012) e
correzioni introdotte con **Android 3.2.8 Beta 18 + Cloud 013 + portale v15**.

## 1. Come funziona oggi il sistema

```
Android (SQLite, offline)                      Supabase                         Portale (Cloudflare Workers)
───────────────────────────                    ────────                         ───────────────────────────
modifica locale ──► pendingChanges=true
WorkManager / app aperta
  1. importa modifiche web  ◄── get_web_pending_records ── erp_records + web_record_overlays
  2. backup completo a blocchi ─► Storage (workspaces/…)
  3. mirror di TUTTI i record ─► publish_workspace_snapshot ─► erp_records ──► list_erp_records_safe_v2 ──► tabelle, dossier, PDF
  4. conferma overlay        ─► ack_web_changes                     ▲
                                                                     └── web_update_record / web_create_* (overlay e record "pending")
```

- Android è la **fonte canonica**: pubblica uno snapshot completo e un "mirror"
  JSON di ogni record (`erp_records`).
- Il portale **non scrive** direttamente sui record: salva un *overlay*
  (`web_record_overlays`) che si sovrappone in lettura. Android lo importa,
  lo incorpora nella pubblicazione successiva e lo conferma.
- I permessi (ruoli, override per utente, rimozione server-side dei campi
  economici senza `finance.view`) sono applicati dalle funzioni Supabase.

## 2. Perché gli ordini Zentha caricati da Android non erano visibili/modificabili sul portale

### 2.1 Causa principale: il filtro periodo nascondeva gli ordini (portale)
La lista di ogni modulo mostrava solo i record del periodo selezionato, con
periodo predefinito **"mese corrente fino a oggi"**. La data di riferimento era
calcolata come `date || dueDate || orderDate || …`: per Zentha veniva quindi
usata la **data di consegna prima della data ordine**. Un ordine inserito oggi
con consegna tra due settimane cadeva fuori da *tutti* i periodi predefiniti
(che terminano sempre oggi) e non compariva nella tabella né era apribile per
la modifica. Lo stesso valeva per gli Ordini Spectro (`dueDate` prima di
`receivedDate`).

### 2.2 Overlay web mai confermati → ripubblicazioni infinite (cloud + Android)
Un overlay veniva eliminato solo se tutti i suoi campi erano identici al
payload Android (`patch <@ payload`). Questo non accadeva quasi mai:
- il form web inviava **tutti** i campi, anche quelli non modificati;
- i numeri vuoti diventavano `0` (es. "ID ordine Zentha collegato" vuoto → `0`,
  mentre Android ha `null`): Android collegava la produzione all'ordine `0`;
- lo **stato** era un campo di testo libero: un valore non previsto veniva
  convertito dal telefono nel valore predefinito;
- i campi esistenti solo sul web (valutazione produzione: voto, criticità,
  problemi, nota finale) non esistono su Android;
- il trascinamento delle attività impostava lo stato `OPEN`, che Android non
  conosce (corretto è `TODO`).

Risultato: a ogni ciclo (apertura app e ogni 15 minuti) Android reimportava gli
stessi overlay, marcava il workspace come modificato e ripubblicava tutto.

### 2.3 Ogni pubblicazione riscriveva l'intero mirror (cloud)
Il payload di ogni record conteneva `_workspace_revision`, diverso a ogni
pubblicazione: **tutti** i record venivano aggiornati anche se invariati e il
trigger di audit salvava **due copie complete** di ciascuno (prima/dopo).
Con 6.000 record, una pubblicazione identica produceva 6.000 aggiornamenti e
~6.000 righe di audit (collaudo su PostgreSQL 16). Insieme al ciclo 2.2 questo
fa crescere il database senza limite: oltre i 500 MB del piano Supabase Free il
progetto passa in sola lettura e **la sincronizzazione si blocca del tutto**, e
anche prima le pubblicazioni diventano sempre più lente.

> Da verificare sul tuo progetto: Supabase → *Settings → Usage* (dimensione
> database) e, su Android, *Impostazioni → Cloud* (ultimo errore di
> sincronizzazione). Se il database è vicino al limite, vedi §4 passo 5.

### 2.4 Attività modificate dal web mai arrivate su Android
Android importava solo overlay di ordini, produzioni e ordini Zentha. Le
modifiche web alle attività restavano visibili solo sul portale e coprivano in
seguito le modifiche fatte da telefono.

## 3. Correzioni introdotte

| Area | File | Correzione |
|---|---|---|
| Portale | `web/src/erp.ts` | Data di riferimento per tipo di record (Zentha → data ordine, Ordini → data ricezione, Attività → data creazione). Le viste **Operativi** e **Da attenzionare** mostrano tutti i record aperti indipendentemente dal periodo; Zentha e Ordini si aprono su *Operativi*. |
| Portale | `web/src/erp.ts` | Stato, priorità, gravità e criticità diventano **menu a tendina** con i soli valori ammessi da Android. Il salvataggio invia **solo i campi modificati**; un numero vuoto resta `null`. Trascinamento attività: `TODO` al posto di `OPEN`. |
| Cloud | `supabase/migrations/202611220013_…sql` | Pubblicazione **set-based**: aggiorna solo i record realmente cambiati (niente più `_workspace_revision`), soft-delete con anti-join. Seconda pubblicazione identica di 6.000 record: **0 aggiornamenti, 0 righe di audit**. |
| Cloud | idem | `get_web_pending_records` restituisce identità e revisione dell'overlay e invia ad Android solo overlay con differenze reali (incluse le **attività**). Nuova `ack_web_changes_v2`: conferma per revisione, rimuove i campi assorbiti da Android, **conserva i campi solo-web** (valutazione produzione) e non tocca overlay modificati dopo l'import. |
| Cloud | idem | `admin_prune_mirror_audit` (opzionale, manuale): elimina le copie di audit del mirror più vecchie di N giorni (minimo 30). |
| Android | `data/SpectroRepository.kt` | L'import web aggiorna e marca "da pubblicare" solo se il record locale cambia davvero: fine del ciclo infinito. Importa le modifiche web alle **attività** (stato, priorità, scadenza, testi). |
| Android | `sync/CloudSyncRepository.kt` | Memorizza le revisioni degli overlay importati e le conferma con `ack_web_changes_v2` **dopo** una pubblicazione riuscita; compatibile con progetti senza migrazione 013. |
| Android | `app/build.gradle.kts` | Versione `3.2.8-beta18` (versionCode 35). |

Verifiche eseguite: le 13 migrazioni si applicano in sequenza su PostgreSQL 16
con uno schema Supabase di prova; collaudati pubblicazione iniziale, ripubblicazione
identica, modifica/eliminazione di un record, overlay con campo solo-web, overlay
modificato dopo l'import e conferma legacy. Portale: `npm run build` (TypeScript
strict) riuscito. Android: livello dati/sync compilato con Kotlin 2.2 e i 28 test
unitari esistenti superati. **La build APK completa (Gradle + Android SDK) non è
stata eseguita in questo ambiente**: va confermata dal workflow GitHub.

## 4. Cosa fare per attivare le correzioni

1. **Supabase → SQL Editor**: esegui
   `supabase/migrations/202611220013_incremental_mirror_and_web_overlay_ack.sql`.
   È additiva e può essere rieseguita. Le app già installate continuano a
   funzionare (meglio di prima: le pubblicazioni non riscrivono più tutto).
2. Unisci il ramo `claude/vigilant-albattani-4u066n` in `spectro-3.0-beta`:
   Cloudflare ripubblica il portale e GitHub Actions genera l'APK firmato.
3. Installa l'APK 3.2.8 Beta 18 sopra la versione attuale (stessa firma, nessuna
   perdita dati).
4. Collaudo consigliato: crea un ordine Zentha con consegna futura su Android →
   verifica che compaia nel portale (vista *Operativi*) → modifica lo stato dal
   web → riapri l'app: lo stato deve arrivare sul telefono e, dopo la
   sincronizzazione successiva, l'overlay deve sparire (nessuna ripubblicazione
   continua in *Impostazioni → Cloud*).
5. Facoltativo, se il database è grande: dopo qualche giorno di funzionamento
   regolare elimina nel SQL Editor le vecchie copie di audit del mirror
   (le azioni web, gli accessi e le pubblicazioni restano):
   `delete from public.audit_log where action in ('INSERT','UPDATE') and created_at < now() - interval '90 days';`
   e poi, in un momento in cui nessuno sta sincronizzando,
   `vacuum full public.audit_log;` per restituire lo spazio. Da un client
   autenticato come amministratore lo stesso è disponibile come RPC
   `admin_prune_mirror_audit(organizzazione, giorni)`.

## 5. Limiti che restano e roadmap per un gestionale multiutente

### 5.1 Limiti architetturali attuali
1. **Un solo telefono può pubblicare.** La sincronizzazione è a snapshot
   completo con controllo di revisione: se un secondo dispositivo (un
   dipendente, o un nuovo telefono) pubblica, gli altri ricevono "revisione di
   un altro dispositivo" e restano bloccati finché non scaricano tutto,
   sostituendo i dati locali. Oggi i dipendenti devono quindi lavorare dal
   **portale web**, non da un'altra app Android con sincronizzazione.
2. **Zentha dal web**: si modificano intestazione e stato dell'ordine, ma non
   righe prodotto, taglie, pagamenti e costi extra. Inoltre Android, a ogni
   salvataggio di un ordine, ricrea le righe con nuovi ID.
3. **Produzione dal web**: modificare i metri di tessuto non riallinea colli e
   movimenti di magazzino (lo fa solo Android).
4. Clienti, magazzino, cotone, criticità e finanza: le modifiche web restano
   solo sul portale.

### 5.2 Passo successivo consigliato: sincronizzazione per record
Sostituire lo snapshot completo con una sincronizzazione incrementale per
record (Android resta offline-first):
- tabella cloud per entità (o `erp_records` con `version` per record) come
  fonte unica;
- su Android una **outbox** di operazioni (crea/modifica/elimina) inviate una
  per una con `expected_version`; i conflitti si risolvono per campo, non
  bloccando l'intero workspace;
- download incrementale per `updated_at > ultimo_pull` (Supabase Realtime per
  il portale);
- il portale scrive direttamente sui record (niente più overlay) con le stesse
  regole di permesso e audit leggero (solo campi cambiati);
- backup completo a blocchi mantenuto solo come backup di sicurezza.

Questo abilita più telefoni e più utenti contemporaneamente e rende il web
equivalente all'app.

### 5.3 Zentha come fulcro di ordini e produzione
Con la sincronizzazione per record, in ordine di valore:
1. **Righe ordine complete dal web**: prodotti, varianti, taglie per quantità,
   personalizzazioni (nomi/numeri), con snapshot di prezzo/costo.
2. **Distinta atleti** importabile da CSV/Excel (cognome, numero, taglie).
3. **Workflow con timeline**: Bozza → Grafica → Approvazione cliente →
   Produzione → Pronto → Consegnato, con responsabile, data e note a ogni
   passaggio e notifiche agli utenti interessati.
4. **Coda di produzione**: ordini in produzione ordinati per consegna, con
   fabbisogno stimato di carta/tessuto calcolato dal catalogo e confronto con
   le produzioni registrate; assegnazione a plotter e giorno.
5. **Magazzino Zentha** (varianti e giacenze, carico/scarico da ordine) e
   **listini per cliente** con scaglioni di quantità, già previsti in
   `SPECTRO_DATABASE.md`.
6. **Cruscotto**: ordini in ritardo, margine per ordine e per cliente,
   carico di produzione della settimana, incassi da ricevere.
