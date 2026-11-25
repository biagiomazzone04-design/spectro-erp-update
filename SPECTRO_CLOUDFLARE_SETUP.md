# Spectro ERP su Cloudflare Workers

Questa configurazione rende il portale web indipendente da ChatGPT Sites. Database,
autenticazione e allegati restano nel progetto Supabase esistente.

## Creazione del portale

1. Accedere a Cloudflare e aprire **Workers & Pages**.
2. Selezionare **Create application → Import a repository**.
3. Collegare GitHub e scegliere il repository privato `spectro-erp-update`.
4. Impostare il branch di produzione `spectro-3.0-beta`.
5. Usare queste impostazioni di build:
   - Framework preset: `Vite`
   - Root directory: `web`
   - Build command: `npm run build`
   - Deploy command: `npx wrangler deploy`
6. Aggiungere le variabili di produzione:
   - `VITE_SUPABASE_URL` = `https://fbfxtyowcxgagseqdlyz.supabase.co`
   - `VITE_SUPABASE_ANON_KEY` = la chiave publishable Supabase
7. Avviare il primo deploy. Cloudflare assegna un indirizzo del tipo
   `https://spectro-erp-cloud.<account>.workers.dev`.

Il fallback delle pagine interne è già gestito da `wrangler.toml` tramite
`not_found_handling = "single-page-application"`. Non aggiungere un secondo file
`_redirects`: sui Workers produrrebbe un ciclo di redirect.

Ogni push futuro sul branch `spectro-3.0-beta` pubblicherà automaticamente il
portale senza interventi manuali.

## Autenticazione Supabase

Dopo il primo deploy, in Supabase aprire **Authentication → URL Configuration**:

- impostare **Site URL** sull'indirizzo Cloudflare definitivo;
- aggiungere lo stesso indirizzo con `/**` tra i **Redirect URLs**;
- mantenere temporaneamente il vecchio indirizzo ChatGPT tra i redirect finché
  la migrazione non è stata collaudata.

Il codice web usa sempre l'origine corrente come destinazione delle email di
conferma, quindi eventuali domini personalizzati funzioneranno senza ricompilare.

## Dominio personalizzato opzionale

Nel progetto Cloudflare aprire **Domains → Add a custom domain** e collegare,
per esempio, `cloud.spectroerp.it`. Dopo la verifica DNS, sostituire il Site URL e
aggiungere il nuovo dominio ai Redirect URLs di Supabase.

## Collaudo prima della dismissione del vecchio portale

- accesso Admin e verifica permessi;
- apertura dossier Attività e Produzione;
- anteprime degli allegati protetti;
- modifica di un record autorizzato;
- registrazione di prova e conferma email;
- installazione PWA su Windows e Mac;
- aggiornamento automatico dopo una sincronizzazione Android.

Solo dopo questi controlli il sito ChatGPT può essere considerato dismesso.
