# SPECTRO_CODEX_START_HERE.md

## Obiettivo
Questo repository contiene Spectro ERP, gestionale Android nativo Kotlin/Jetpack Compose per produzione tessile, magazzino, ordini, criticità, cotone, attività, contabilità operativa e Zentha Sport.

La prossima release è **Spectro ERP 3.0.0 Beta 7**.

## Prima di modificare il codice
Leggi nell'ordine:
1. `AGENTS.md`
2. `SPECTRO_PROJECT_CONTEXT.md`
3. `SPECTRO_CURRENT_STATE.md`
4. `SPECTRO_ARCHITECTURE.md`
5. `SPECTRO_DATABASE.md`
6. `SPECTRO_BETA7_SCOPE.md`
7. `SPECTRO_ROADMAP.md`
8. `SPECTRO_CLOUD_ROADMAP.md`

Poi analizza l'intero repository e in particolare `Models.kt`, `SpectroDatabase.kt`, `SpectroRepository.kt`, `SpectroViewModel.kt`, `SpectroApp.kt`, `ClientsScreen.kt`, `DashboardScreen.kt`, `WorkOrdersScreen.kt`, `TimelineScreen.kt`, `InventoryScreen.kt`, `ZenthaScreen.kt`, `BackupManager.kt`, `ReportExporter.kt` e `.github/workflows/main.yml`.

## Sequenza di lavoro
1. Verifica che lo snapshot Beta 6 compili.
2. Correggi eventuali regressioni preesistenti senza cambiare il comportamento.
3. Implementa lo Storico Cliente Unificato.
4. Implementa Zentha ERP 2.0 secondo `SPECTRO_BETA7_SCOPE.md`.
5. Aggiungi test per migrazioni, deduplicazione storico, calcoli Zentha e stock.
6. Aggiorna backup/restore e PDF.
7. Solo quando pronto, porta a `versionCode = 18` e `versionName = 3.0.0-beta7`.
8. Aggiorna artifact GitHub a `SpectroERP-v3.0.0-BETA7-RELEASE`.

La task non è completa se test/build falliscono o se una migrazione può perdere dati.
