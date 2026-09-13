# Come caricare Spectro ERP 3.0 Beta 1 su GitHub

Questa procedura usa GitHub Desktop ed evita di modificare file uno per uno dal browser.

## 1. Sicurezza prima di iniziare
- Conserva il backup creato dall'app attuale.
- Copialo anche sul PC o su un secondo supporto.
- Non caricare mai in GitHub file contenenti keystore, password o il valore del secret di firma.

## 2. Apri il repository locale
Apri GitHub Desktop e seleziona il repository `spectro-erp-update`.
Esegui `Fetch origin` / `Pull origin` per avere l'ultimo `main`.

## 3. Crea il branch Beta
Menu `Branch` -> `New branch`.
Nome esatto consigliato:

`spectro-3.0-beta`

Crealo partendo da `main` e selezionalo.

## 4. Copia la patch
Estrai `SpectroERP-v3.0.0-BETA1-PATCH.zip`.
Copia TUTTO il contenuto estratto nella cartella principale del repository locale, quella che contiene `app`, `.github`, `build.gradle.kts` e `settings.gradle.kts`.
Conferma `Sostituisci i file` quando Windows lo chiede.

Non cancellare la cartella nascosta `.git`.

## 5. Verifica GitHub Desktop
GitHub Desktop deve mostrare i file modificati/aggiunti.
Controlla che NON compaiano file tipo:
- keystore / `.jks` / `.p12`;
- file contenenti password;
- `SPECTRO_SIGNING_BUNDLE_VALUE.txt`.

## 6. Commit e push
Summary consigliato:

`Spectro ERP 3.0 Beta 1`

Premi `Commit to spectro-3.0-beta`, quindi `Publish branch` o `Push origin`.

## 7. Build GitHub Actions
Il workflow si avvia automaticamente anche sul branch `spectro-3.0-beta`.
Vai in `Actions` -> `Build Spectro Native APK`.

Se la build è verde, scarica l'artifact:

`SpectroERP-v3.0.0-BETA1-RELEASE`

Se la build è rossa, scarica il `Download log archive` completo e caricalo in ChatGPT: non modificare casualmente il codice.

## 8. Installazione sul telefono
Prima crea/conserva un altro backup dell'app attuale.
Apri l'APK Beta sul Samsung e prova ad aggiornare l'app SENZA disinstallarla.

Se Android propone `Aggiorna`, package e firma sono compatibili e i dati locali vengono mantenuti.

Se Android rifiuta l'installazione per firma incompatibile, NON procedere alla disinstallazione finché non hai verificato il backup. Potrebbe essere necessario il passaggio una tantum: backup -> disinstallazione vecchia build debug -> installazione release -> ripristino backup.

## 9. Controlli obbligatori dopo l'aggiornamento
Verifica prima di usare la Beta come versione principale:
- vecchi clienti e ordini presenti;
- vecchie produzioni presenti;
- giacenze magazzino presenti;
- tessuti legacy convertiti in colli;
- nuovo carico con più colli;
- selezione esatta dei colli in produzione;
- ordine In processo -> Avvia produzione;
- modifica ordine dalla dashboard;
- timeline con testo e immagini;
- ricerca globale;
- ordine Cotone e formule;
- catalogo/ordine/pagamento Zentha;
- creazione e ripristino backup 3.0.

## 10. Non fare ancora merge su main
Mantieni la Beta sul branch `spectro-3.0-beta` finché i test reali non sono completati. Solo dopo si prepara la 3.0.0 stabile e si esegue il merge in `main`.
