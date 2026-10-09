# Stato Spectro ERP

## Beta 7
- Branch di lavoro `spectro-3.0-beta7`, derivato da `spectro-3.0-beta` (`5d8132296ee50be195055f6028367fbaf127cc53`).
- versionCode 18, versionName `3.0.0-beta7`.
- applicationId `com.spectro.app`, firma release invariata.
- SQLite schema 6, backup v8 con import v7/legacy.
- Storico cliente unificato e Zentha ERP 2.0 implementati secondo scope; cloud completo ed Excel restano futuri. La distinta è strutturata per un futuro esportatore Excel.

## Verifica
La baseline Beta 6 ha superato 9 test e la build debug prima delle modifiche. Beta 7 aggiunge test di storico, calcolo, pricing, stock, migrazione, backup/allegati, contenuti PDF e regressioni. Esito finale: **30 test passati, nessuno saltato, build debug riuscita**. Comando di riferimento: `gradle :app:testDebugUnitTest :app:assembleDebug`.

Non è stata eseguita un'installazione su dispositivo né una build con il secret di firma release. Rendering/condivisione PDF, interazione UI e aggiornamento sopra l'app operativa richiedono la prova sul dispositivo. Vedere [migrazione e verifiche](MIGRATION_BETA7.md) e [changelog](CHANGELOG_V3.0.0_BETA7.md).
