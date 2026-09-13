# Spectro ERP Native 3.0.0 Beta 1

Aggiornamento maggiore dell'app Android Spectro ERP per la gestione di stamperia, magazzino a colli, ordini, criticità, cotone e Zentha.

## Identità Android preservata
- applicationId/package: `com.spectro.app`
- minSdk: 26
- compileSdk/targetSdk: 36
- versionCode: 12
- versionName: `3.0.0-beta1`
- firma RELEASE: usa lo stesso secret GitHub `SPECTRO_SIGNING_BUNDLE_B64`

## Novità principali
- Magazzino cliente/azienda con lotti, colli, metri iniziali/residui e movimenti.
- Selezione dei colli realmente usati in produzione e scarico automatico delle giacenze.
- Ordine unico che passa da In processo a In produzione senza duplicare l'ordine.
- Modifica ordine e storico automatico delle modifiche principali.
- Timeline/criticità per ordine con categorie, responsabilità, gravità, allegati e PDF.
- Ricerca globale su clienti, ordini, tessuti, colli, timeline, cotone, Zentha e produzioni.
- Modulo Cotone con costi manuali, tessuto cliente/azienda, colli, preventivo e consuntivo.
- Modulo Zentha con catalogo, ordini multiprodotto, costi, utile, split 50/50 configurabile e pagamenti.
- Backup formato v5 con compatibilità di lettura dei backup native precedenti.

## Migrazione dati
Il database passa dalla versione 2 alla versione 3 tramite migrazione non distruttiva. I vecchi tessuti che possiedono solo una giacenza totale vengono convertiti in un collo `LEGACY-xxxx` con la stessa quantità; potranno poi essere rettificati manualmente.

Non è presente `fallbackToDestructiveMigration` e l'aggiornamento non deve cancellare il database esistente.

## Build
Il workflow `.github/workflows/main.yml` compila i branch `main` e `spectro-3.0-beta`, verifica la firma e pubblica l'artifact `SpectroERP-v3.0.0-BETA1-RELEASE`.

Vedi `ISTRUZIONI_GITHUB_SPECTRO_3_BETA.md` prima di caricare il codice.
