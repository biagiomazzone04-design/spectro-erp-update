# Spectro ERP multiutente — avvio operativo

Questa versione introduce la base cloud comune per Android, Windows e Mac.

## Cosa è già pronto

- login email/password Supabase nell’app Android;
- sessione Android cifrata con Android Keystore;
- ruoli e permessi caricati dal backend;
- moduli nascosti quando l’utente non è autorizzato;
- blocco centralizzato delle scritture non autorizzate;
- portale web/PWA installabile da browser su Windows e Mac;
- PostgreSQL multi-tenant con RLS, ruoli, override per utente, soft-delete, audit e storage privato;
- workspace Android completo versionato, con allegati, checksum SHA-256 e controllo conflitti;
- mirror web dei record privo di percorsi file locali, filtrato modulo per modulo e sanificato lato server per i ruoli senza accesso economico;
- backup locale di recupero automatico prima di ogni pubblicazione o download;
- ruoli iniziali Admin, Produzione, Commerciale, Zentha e Sola lettura;
- preset **Supervisore** (tutto in sola lettura) e **Attività e Zentha senza prezzi**.

## Attivazione Supabase

1. Eseguire, nell’ordine, `supabase/migrations/202610100001_multi_user_foundation.sql`, `supabase/migrations/202610100002_workspace_sync.sql` e `supabase/migrations/202610170003_sync_roles_and_safe_web.sql` nel SQL Editor del progetto Supabase. La terza migrazione è ripetibile, ripara anche `sync.use` per gli Admin già esistenti e non cancella dati.
2. In Authentication abilitare il provider Email.
3. La chiave pubblica `sb_publishable_` del progetto è già configurata nei client. Se viene ruotata, sostituirla tramite secret GitHub `SPECTRO_SUPABASE_ANON_KEY` e variabile web `VITE_SUPABASE_ANON_KEY`.
4. Creare il primo account.
5. Dall’app Android o dal portale web, nella schermata “Ruolo in attesa”, scegliere **Configura prima azienda**. La funzione funziona solo se non esiste ancora alcuna organizzazione e assegna il ruolo Admin al primo utente.

### Se il link di conferma risulta scaduto o obsoleto

1. Tornare alla schermata di accesso o registrazione.
2. Inserire l’email usata per l’account e scegliere **Reinvia email di conferma**.
3. Aprire soltanto l’ultimo messaggio ricevuto: un nuovo invio invalida i link precedenti.
4. Dopo la conferma, tornare nell’app e accedere con email e password.

Supabase può limitare temporaneamente gli invii ripetuti. Se compare il messaggio relativo al limite email, attendere alcuni minuti prima di riprovare. In **Authentication → URL Configuration** mantenere aggiornati il Site URL del portale pubblicato e le Redirect URLs autorizzate.

Non usare mai la chiave `service_role` nei client.

## Avvio portale Windows/Mac

```bash
cd web
cp .env.example .env.local
# La chiave publishable è già presente; aggiornarla solo in caso di rotazione.
npm install
npm run dev
```

Per la pubblicazione su Cloudflare Pages:

- directory progetto: `web`;
- comando build: `npm run build`;
- directory output: `dist`;
- variabili: `VITE_SUPABASE_URL` e `VITE_SUPABASE_ANON_KEY`.

Il portale è una PWA: da Edge/Chrome su Windows o Chrome/Safari su macOS può essere aggiunto al Dock/Desktop.

## Prima migrazione e uso quotidiano

1. Accedere su Android con l’utente Admin.
2. Aprire **Account e impostazioni → Workspace cloud → Verifica stato cloud**.
3. Se il cloud è vuoto, scegliere **Pubblica**. L’app crea prima un backup locale, comprime database e allegati, calcola SHA-256 e pubblica la revisione 1.
4. Su una seconda postazione Android scegliere **Verifica** e poi **Scarica**. Anche in questo caso la copia locale precedente viene salvata prima della sostituzione.
5. Dopo le modifiche, verificare lo stato e pubblicare una nuova revisione. Se un’altra postazione ha già pubblicato, Spectro blocca l’operazione e richiede di scaricare la revisione più recente.

Se la scheda **Workspace cloud** non compare per l’Admin, rieseguire la terza migrazione, disconnettersi e accedere di nuovo: la sessione ricaricherà `sync.use`.

## Profili richiesti

- Enzo: assegnare il ruolo **Supervisore**.
- Maria: assegnare il ruolo **Attività e Zentha senza prezzi**.
- Biagio: assegnare il ruolo **Admin**.

Ogni persona deve prima registrarsi con un indirizzo email reale e una password di almeno 8 caratteri, confermare l’email e poi essere associata dall’Admin nella sezione **Utenti e accessi**. I ruoli non contengono password e possono essere usati dallo stesso account su Android, Windows e Mac.

La pubblicazione aggiorna nello stesso commit anche `erp_records`: il portale Windows/Mac mostra quindi soltanto i moduli autorizzati al singolo utente. Foto e archivio completo restano nel bucket privato e non sono scaricabili dai ruoli senza `sync.use`.

## Modello di conflitto intenzionalmente conservativo

La sincronizzazione attuale è a revisione dell’intero workspace, non una fusione automatica record-per-record. Due utenti possono consultare il gestionale e usare il portale con permessi diversi, ma prima di pubblicare da Android devono partire dalla stessa revisione cloud. Questa scelta evita duplicazioni degli ID locali, perdite di allegati e sovrascritture silenziose. La futura sincronizzazione incrementale userà gli UUID già presenti nell’envelope `erp_records`.
