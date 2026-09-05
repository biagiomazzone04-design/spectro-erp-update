# Come aggiornare Spectro ERP alla 2.1.0

## Prima di fare qualsiasi cosa sul telefono
La versione che hai già installato è stata compilata come APK DEBUG su GitHub Actions. Il certificato DEBUG del runner è temporaneo.

Per creare da ora in poi veri aggiornamenti Android senza perdere i dati, la 2.1.0 usa una chiave RELEASE permanente.

### UNA SOLA VOLTA
1. Apri la Spectro attualmente installata.
2. Impostazioni > Backup e sicurezza dati > **Esporta backup**.
3. Salva il file `.spectrobackup` in Download / Drive / PC.
4. Solo dopo aver verificato di avere il backup, potrai disinstallare la vecchia app.
5. Installa la nuova `app-release.apk` 2.1.0.
6. Apri Spectro > Impostazioni > **Ripristina da file** e seleziona il backup.

Dalla 2.1.0 in poi NON dovrai più disinstallare l'app, purché non cambi i secrets della firma.

---

## 1. Aggiorna i file GitHub
Il metodo più facile è usare lo ZIP completo `SpectroERP-Native-v2.1.0-UPDATE.zip` e sostituire il contenuto del repository con quello dello ZIP.

Se preferisci aggiornare soltanto i file modificati, usa `SpectroERP-v2.1.0-PATCH.zip` mantenendo esattamente le cartelle contenute nello ZIP.

Dentro `.github/workflows/` tieni **solo `main.yml`** per questa build; se nel repository è rimasto un vecchio `build-apk.yml`, eliminalo per non avviare due workflow.

File principali modificati:

```text
.github/workflows/main.yml
.gitignore
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values-v31/styles.xml
app/src/main/res/drawable/spectro_app_icon.png
app/src/main/res/drawable/spectro_header_logo.png
app/src/main/java/com/spectro/app/data/Models.kt
app/src/main/java/com/spectro/app/data/SpectroDatabase.kt
app/src/main/java/com/spectro/app/data/SpectroRepository.kt
app/src/main/java/com/spectro/app/ui/Components.kt
app/src/main/java/com/spectro/app/ui/CreateScreen.kt
app/src/main/java/com/spectro/app/ui/ClientsScreen.kt
app/src/main/java/com/spectro/app/ui/WorkOrdersScreen.kt
app/src/main/java/com/spectro/app/ui/InventoryScreen.kt
app/src/main/java/com/spectro/app/ui/DashboardScreen.kt
app/src/main/java/com/spectro/app/ui/SpectroApp.kt
app/src/main/java/com/spectro/app/ui/SpectroViewModel.kt
app/src/main/java/com/spectro/app/ui/GlobalSearchSheet.kt
app/src/main/java/com/spectro/app/ui/theme/Theme.kt
app/src/main/java/com/spectro/app/util/BackupManager.kt
app/src/main/java/com/spectro/app/util/ReportExporter.kt
```

NON caricare nel repository file `.jks`, `.keystore` o `SPECTRO_SIGNING_SECRETS.txt`.

---

## 2. Configura la firma permanente su GitHub
Apri il file separato `SPECTRO_SIGNING_SECRETS.txt` che ti è stato fornito.

Nel repository GitHub:

**Settings > Secrets and variables > Actions > New repository secret**

Crea i due secrets indicati nel file:

- `SPECTRO_KEYSTORE_B64`
- `SPECTRO_KEY_PASSWORD`

Copia i valori esattamente, senza spazi prima/dopo.

Non mettere questi valori dentro `main.yml`, nel codice o in un commit.

---

## 3. Avvia la build
Vai in:

**Actions > Build Spectro Native APK > Run workflow > main > Run workflow**

Dovrebbero diventare verdi:

```text
Checkout
Java 17
Android SDK
Installa Android API 36
Gradle 8.13
Verifica secrets firma
Test logica economica
Compila APK RELEASE firmato
Verifica APK
Salva APK
```

A fine build scarica l'artifact:

`SpectroERP-v2.1.0-RELEASE`

Il file da installare è:

`app-release.apk`

---

## 4. Installazione sul Samsung S25+
Per questa sola transizione DEBUG -> RELEASE:

1. esporta il backup dalla vecchia app;
2. disinstalla la vecchia app;
3. installa `app-release.apk` 2.1.0;
4. ripristina il backup.

Per la 2.1.1, 2.2.0, ecc. basterà invece installare il nuovo APK sopra quello esistente.

---

## 5. Cosa NON devi modificare
Non cambiare:

- `applicationId = "com.spectro.app"`
- alias firma `spectro`
- `SPECTRO_KEYSTORE_B64`
- `SPECTRO_KEY_PASSWORD`

Cambiare uno di questi elementi può impedire ad Android di riconoscere l'APK come aggiornamento della stessa app.
