# Spectro ERP Native 3.0.0 Beta 7.1

ERP Android Kotlin/Jetpack Compose per produzione tessile, ordini, magazzino a colli, criticità, Cotone, attività operative e Zentha Sport.

## Identità Android
- applicationId/package: `com.spectro.app`, invariato.
- minSdk 26, compileSdk/targetSdk 36.
- versionCode 19, versionName `3.0.0-beta7.1`.
- Firma RELEASE: configurazione e secret GitHub `SPECTRO_SIGNING_BUNDLE_B64` invariati.

## Beta 7.1: navigazione e accessibilità
Menu moduli scorrevole e ricercabile, preferiti nella Home, barra con etichette e contenuti separati dalla navigazione inferiore. Zentha offre KPI cliccabili, filtri operativi e un selettore delle aree sempre accessibile. Animazioni brevi accompagnano pressione e cambio schermata.

[Changelog UI](CHANGELOG_V3.0.0_BETA7.1.md). La fase PWA Windows/Mac a quattro utenti, con permessi modificabili, è definita in [setup cloud](SPECTRO_CLOUD_SETUP.md): non è ancora attiva nell'app.

## Beta 7
- Storico cliente unificato: WorkOrder e produzioni legacy senza `workOrderId`, ricerca e dossier PDF; nessun doppio conteggio delle produzioni collegate. Caso Goccia verificato: 2 ordini + 7 legacy = 9 voci.
- Zentha ERP 2.0: dashboard, ordini, clienti, magazzino, prodotti, listini, produzione, pagamenti e statistiche.
- Prodotti con costi scomposti, varianti/taglie, giacenze, movimenti tracciati e scorte minime.
- Listini personalizzabili, fasce quantità, assegnazioni e prezzi cliente. Prezzo, costo e descrizione variante conservati nelle righe ordine.
- Distinta squadra, personalizzazioni, mockup approvato, documenti, workflow con data/ora, pagamenti multipli, margini e split utile.
- Sette documenti PDF: conferma, distinta, produzione, packing list, taglie, economia, storico.
- Restano disponibili tutti i moduli Spectro precedenti, backup e allegati.

## Uso Zentha
Creare prodotto e varianti; aprire Magazzino per carico iniziale e soglia. Nei Listini definire fasce inclusive e assegnarle ai clienti. Le fasce considerano la quantità totale del prodotto nell'ordine, sommando le varianti. I prezzi specifici cliente prevalgono sui listini; la variante specifica prevale sulla regola prodotto all'interno dello stesso livello.

Nell'ordine, «Applica listino» aggiorna esplicitamente i prezzi. Le variazioni di catalogo non ricalcolano lo storico. «Conferma scarico magazzino» scarica le sole righe con variante: salvataggi successivi registrano le differenze, l'annullamento restituisce le quantità. Gli ordini legacy senza variante non generano stock fittizio. Pagamenti e documenti restano conservati anche dopo annullamento.

## Dati e migrazioni
SQLite schema 6, backup portabile v8. Migrazione 5 → 6 additiva e ripetibile; nessun reset automatico. I backup v7 e precedenti restano importabili. Un archivio portabile incompleto viene rifiutato, e un ripristino fallito lascia intatto il database corrente.

Dettagli: [database](SPECTRO_DATABASE.md), [migrazione e verifiche Beta 7](MIGRATION_BETA7.md), [changelog](CHANGELOG_V3.0.0_BETA7.md).

## Build
Java 17, Android SDK 36 / Build Tools 35.0.0, Gradle 8.13:

```sh
gradle --no-daemon --max-workers=4 :app:testDebugUnitTest :app:assembleDebug
```

Robolectric usa SDK 28 per test SQLite e backup; il suo cache/lock risiede in `app/build/test-home`. Richiede accesso ai repository Maven al primo avvio. I test verificano i contenuti dei sette PDF; rendering e condivisione Android richiedono una prova su dispositivo.

GitHub Actions esegue test e build RELEASE con la firma esistente, per `main`, `spectro-3.0-beta` e `spectro-3.0-beta7`, producendo `SpectroERP-v3.0.0-BETA7.1-RELEASE`. L'APK debug locale è per test; l'aggiornamento dell'app installata richiede l'APK release con la firma originale, senza disinstallazione.
