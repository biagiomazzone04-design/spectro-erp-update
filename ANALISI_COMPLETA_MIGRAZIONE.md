# Analisi completa e migrazione — Spectro ERP

## Perimetro analizzato
Sono stati inventariati e controllati entrambi i pacchetti sorgente forniti durante il lavoro:

- `Spectro-ERP-main.zip`: 23 file, incluso un secondo progetto legacy annidato.
- `spectroprint-main.zip`: 15 file della versione Expo 54 usata per la build recente.
- `app-release.apk` e `app-release(1).apk`: struttura APK, bundle JS e librerie native/ABI.

Per i file sorgente/configurazione è stato controllato il contenuto; per PNG/APK sono stati controllati struttura, metadati e presenza/posizione delle risorse rilevanti.

## Inventario del progetto originale (23 file)

### Progetto principale
- `.github/workflows/main.yml` — workflow Android/Expo; sostituito con workflow Kotlin nativo.
- `App.js` — applicazione monolitica React Native; tutte le entità e funzioni principali sono state mappate nel nuovo progetto.
- `app.json` — package/Expo/icone; sostituito da `AndroidManifest.xml` + Gradle.
- `assets/adaptive-icon.png` — migrata nelle risorse native.
- `assets/favicon.png` — rilevante per web, non necessaria nell'app Android nativa.
- `assets/icon.png` — migrata come logo/icona nativa.
- `assets/snack-icon.png` — demo asset; non necessario nel nuovo prodotto.
- `assets/splash-icon.png` — riferimento grafico; splash nativo ricreato con logo Spectro.
- `components/AssetExample.js` — componente demo, non parte della logica ERP; rimosso.
- `eas.json` — configurazione EAS/Expo; non più necessaria.
- `index.js` — bootstrap React Native; sostituito da `MainActivity.kt`.
- `package.json` — dipendenze Expo/RN; sostituito da Gradle/Kotlin.

### Progetto legacy annidato `authentic-blue-donut/authentic-blue-donut/`
- `App.js` — vecchia variante dell'app.
- `README.md` — readme legacy.
- `app.json` — configurazione legacy con package differente.
- `assets/adaptive-icon.png`
- `assets/favicon.png`
- `assets/icon.png`
- `assets/snack-icon.png`
- `assets/splash-icon.png`
- `components/AssetExample.js`
- `index.js`
- `package.json` — Expo ~51, React 18.2, React Native 0.74.3.

Questa copia annidata era un rischio concreto di confusione/build del progetto sbagliato ed è stata completamente esclusa dalla nuova architettura.

## Inventario del repository Expo più recente (15 file)
- `.github/workflows/main.yml` — workflow funzionante dopo le correzioni assets, ma produce ancora APK React Native/Expo.
- `App.js` — 682 righe, sorgente principale ERP.
- `AssetExample.js` — demo non utilizzata; path asset incoerente.
- `README.md` — minimale.
- `README_BUILD_ANDROID.md` — istruzioni build Expo.
- `adaptive-icon.png`, `icon.png`, `splash-icon.png`, `snack-icon.png`, `favicon.png` — nella root; questa posizione aveva causato errori `./assets/...` durante `expo prebuild`.
- `app.json` — Expo SDK 54, `newArchEnabled:false`, target generato Android moderno.
- `eas.json` — profilo APK.
- `index.js` — `registerRootComponent(App)`.
- `main.yml` — secondo workflow duplicato nella root, fonte di confusione.
- `package.json` — Expo 54 / RN 0.81.5 / React 19.1 e dipendenze native.

## Problemi funzionali trovati nell'app React originale

1. **Magazzino non transazionale in modifica.** Modificare una produzione già salvata non riallineava correttamente il consumo precedente.
2. **Magazzino non ripristinato in eliminazione.** Eliminare una produzione non restituiva automaticamente il tessuto consumato.
3. **Backup non realmente circolare.** L'export produceva un PDF contenente JSON, mentre il restore chiedeva JSON grezzo incollato/importato.
4. **Foto persistite come URI del picker.** Non venivano copiate nello storage privato; alcuni URI possono diventare non validi nel tempo.
5. **AsyncStorage usato come database monolitico.** Mancavano transazioni, vincoli e una strategia di migrazione dati strutturata.
6. **Eccezioni silenziate.** Diversi `catch` non mostravano alcun errore all'utente, rendendo i guasti invisibili.
7. **Data ottenuta via UTC nell'app JS.** L'uso di `toISOString()` poteva spostare il giorno vicino alla mezzanotte locale.
8. **Storico cliente incompleto.** Lo stato `clientHistoryModal` veniva valorizzato ma non risultava renderizzato in una vera UI di dettaglio.
9. **Campo `disegni` presente nei dati ma non realmente sfruttato nel flusso UI.** Ora è un campo visibile e ricercabile.
10. **Import `LineChart` non usato** e dipendenza chart esterna non necessaria per il nuovo progetto.
11. **Componente `AssetExample.js` non usato** e con percorso asset incoerente.
12. **Workflow duplicati** e due generazioni Expo differenti nel pacchetto originario.
13. **Nessun blocco giacenza negativa.** Ora il salvataggio rifiuta consumi superiori alla disponibilità.
14. **Eliminazione tessuto storico rischiosa.** Ora un tessuto collegato a produzioni non può essere eliminato.
15. **Validazione campi ridotta.** La versione nativa valida date, valori negativi, cliente, tessuto e quantità.
16. **Ricerca limitata.** Nel nuovo dashboard vengono cercati anche i codici disegno.
17. **APK recente ancora Expo/RN.** L'ultimo `app-release(1).apk` contiene ancora Hermes, React Native ed Expo; quindi il crash del vecchio ramo non è collegato al nuovo codice Kotlin.

## Correzioni già integrate nel progetto Kotlin nativo
- Database SQLite separato dall'interfaccia.
- Repository con transazioni atomiche per produzione + magazzino.
- Ripristino del consumo precedente quando una produzione viene modificata.
- Ripristino giacenza quando una produzione viene eliminata.
- Controllo giacenza insufficiente.
- Protezione di clienti/tessuti già usati nello storico.
- Date locali tramite `LocalDate.now()`.
- Errori operativi mostrati tramite Snackbar/dialog invece di essere nascosti.
- Foto importate nello storage privato.
- Backup ZIP portabile con `data.json` e fotografie.
- Import del formato JSON legacy quando disponibile.
- PDF generato con API Android nativa.
- Grafici disegnati direttamente con Compose Canvas, senza libreria chart esterna.
- Storico cliente realmente visualizzato.
- Codici disegno inseribili e ricercabili.
- Viewer fotografie a tutto schermo.
- Splash/startup nativo scuro.
- UI Material 3, gradienti, transizioni tab, animazioni selettori e pressione pulsanti.

## Regole economiche mantenute intenzionalmente da confermare
Per non cambiare i risultati aziendali senza autorizzazione, `ProductionCalculator` conserva due comportamenti della versione React:

1. **Carta:** il costo carta usa `metri stampati - sfrido carta`.
2. **Tessuto nostro:** la giacenza scala `metri usati + sfrido`, ma il costo economico del tessuto usa soltanto `metri usati`.

Questi due punti meritano una decisione aziendale. Se lo sfrido rappresenta materiale realmente consumato/acquistato, il costo corretto potrebbe dover includere anche lo sfrido. Il codice è centralizzato apposta per rendere la modifica unica e testabile.

## Architettura nuova

`UI Compose → ViewModel → Repository → SQLite`

Le formule economiche sono isolate in `ProductionCalculator`; backup, allegati e PDF sono moduli separati. L'app non richiede Expo, React Native, Hermes, Metro, Node o un bridge JavaScript durante l'esecuzione sul telefono.

## Test e controlli inclusi
- Test unitari per il calcolo produzione senza tessuto.
- Test unitario per tessuto di proprietà con acquisto/vendita.
- Test unitario per evento negativo.
- Compilazione Kotlin pura dei modelli/calcolatore verificata separatamente.
- GitHub Actions esegue prima i test unitari e poi genera l'APK.

L'ambiente usato per preparare il progetto non dispone dell'intero Android SDK/Gradle scaricato, quindi la validazione finale di compilazione Android deve avvenire nel workflow GitHub incluso. Questo è intenzionalmente esplicitato: il progetto non viene dichiarato “testato sul Galaxy S25+” finché l'APK nativo non viene realmente installato e aperto sul dispositivo.
