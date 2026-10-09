# Stato Spectro ERP

## Beta 7.1
- Branch di lavoro `spectro-3.0-beta7`, derivato da `spectro-3.0-beta` (`5d8132296ee50be195055f6028367fbaf127cc53`).
- versionCode 19, versionName `3.0.0-beta7.1`.
- applicationId `com.spectro.app`, firma release invariata.
- SQLite schema 6, backup v8 con import v7/legacy.
- Storico cliente unificato e Zentha ERP 2.0 implementati secondo scope; cloud completo ed Excel restano futuri. La distinta è strutturata per un futuro esportatore Excel.

## Verifica
La baseline Beta 6 ha superato 9 test e la build debug prima delle modifiche. Beta 7 aggiunge test di storico, calcolo, pricing, stock, migrazione, backup/allegati, contenuti PDF e regressioni. La Beta 7 ha superato 30 test; Beta 7.1 aggiunge test Compose di navigazione, scorrimento/font ingranditi, ricerca e preferiti, oltre ai filtri Zentha. Comando di riferimento: `gradle :app:testDebugUnitTest :app:assembleDebug`.

Non è stata eseguita un'installazione su dispositivo né una build con il secret di firma release. Rendering/condivisione PDF, interazione UI e aggiornamento sopra l'app operativa richiedono la prova sul dispositivo. Vedere [migrazione e verifiche](MIGRATION_BETA7.md) e [changelog](CHANGELOG_V3.0.0_BETA7.md).

## Aggiornamento UI e fase cloud

Beta 7.1 verificata con 35 test passati (nessuno saltato) e build debug riuscita.
Correzione scroll del centro moduli, spazi della barra inferiore, preferiti, transizioni, KPI cliccabili e filtri Zentha. Nessuna migrazione DB aggiuntiva. Le prove Compose includono il percorso reale fino a Zentha e Altri ricavi e il rendering di schermate campione; non sostituiscono la prova sul telefono.

Confermati quattro utenti, PWA Windows/Mac e permessi modificabili dal titolare. Nessun backend disponibile al momento; setup e sequenza di implementazione in `SPECTRO_CLOUD_SETUP.md`. Nessun dato è stato caricato su servizi esterni e nessun login cloud è attivo.
