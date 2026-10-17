import { createClient, type User } from '@supabase/supabase-js'
import './style.css'

const projectUrl = import.meta.env.VITE_SUPABASE_URL || 'https://fbfxtyowcxgagseqdlyz.supabase.co'
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY || 'sb_publishable_7cUKVShHm2xZg96-2pvnOw_79AunZnU'
const app = document.querySelector<HTMLDivElement>('#app')!
const supabase = anonKey ? createClient(projectUrl, anonKey, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true }
}) : null

type AccessContext = {
  user_id: string
  display_name: string
  organization_id: string | null
  organization_name: string | null
  role_name: string | null
  permissions: string[]
}

type Module = {
  key: string
  eyebrow: string
  title: string
  description: string
  permission: string
  accent: 'pink' | 'orange'
}

const modules: Module[] = [
  { key: 'orders', eyebrow: 'Flusso operativo', title: 'Ordini', description: 'Commessa, produzione, timeline e consegna.', permission: 'orders.view', accent: 'pink' },
  { key: 'production', eyebrow: 'Reparto', title: 'Produzione', description: 'Lavorazioni giornaliere e avanzamento.', permission: 'production.view', accent: 'orange' },
  { key: 'inventory', eyebrow: 'Scorte', title: 'Magazzino', description: 'Tessuti, colli, lotti e movimenti.', permission: 'inventory.view', accent: 'pink' },
  { key: 'clients', eyebrow: 'CRM', title: 'Clienti', description: 'Anagrafiche, storico e criticità.', permission: 'clients.view', accent: 'orange' },
  { key: 'cotton', eyebrow: 'Linea', title: 'Cotone', description: 'Ordini reattivi e pigmento.', permission: 'cotton.view', accent: 'pink' },
  { key: 'zentha', eyebrow: 'Business unit', title: 'Zentha', description: 'Catalogo, ordini sport e marginalità.', permission: 'zentha.view', accent: 'orange' },
  { key: 'finance', eyebrow: 'Controllo', title: 'Economia', description: 'Ricavi, costi, pagamenti e KPI.', permission: 'finance.view', accent: 'pink' },
  { key: 'tasks', eyebrow: 'Organizzazione', title: 'Attività', description: 'Scadenze e attività operative.', permission: 'tasks.view', accent: 'orange' },
  { key: 'sync', eyebrow: 'Sicurezza dati', title: 'Workspace cloud', description: 'Revisioni, integrità e stato dell’ultima pubblicazione.', permission: 'sync.use', accent: 'orange' },
  { key: 'users', eyebrow: 'Amministrazione', title: 'Utenti e accessi', description: 'Ruoli e permessi personalizzati.', permission: 'users.manage', accent: 'pink' }
]

const escapeHtml = (value: unknown) => String(value ?? '')
  .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;').replaceAll("'", '&#039;')

const friendlyAuthMessage = (message: string) => {
  const normalized = message.toLowerCase()
  if (normalized.includes('email not confirmed')) return 'Email non ancora confermata. Apri l’ultimo link ricevuto oppure richiedine uno nuovo.'
  if (normalized.includes('invalid login credentials')) return 'Email o password non corrette. Se non hai ancora confermato l’email, richiedi un nuovo link.'
  if (normalized.includes('email rate limit exceeded') || normalized.includes('over_email_send_rate_limit')) return 'Hai richiesto troppi link in poco tempo. Attendi alcuni minuti e riprova.'
  if (normalized.includes('user already registered')) return 'Questo indirizzo è già registrato. Accedi oppure reinvia l’email di conferma.'
  return message
}

function renderSetup() {
  app.innerHTML = `<main class="center"><section class="panel auth-panel">
    <div class="brand-mark">S</div><p class="kicker">SPECTRO ERP CLOUD</p>
    <h1>Configurazione da completare</h1>
    <p>Manca <code>VITE_SUPABASE_ANON_KEY</code>. Copia <code>.env.example</code> in <code>.env.local</code> e inserisci la chiave pubblica anon del progetto.</p>
    <p class="muted">Il portale non richiede e non deve mai contenere la chiave service_role.</p>
  </section></main>`
}

function renderLogin(message = '') {
  app.innerHTML = `<main class="center auth-bg"><section class="panel auth-panel">
    <div class="brand-lockup"><div class="brand-mark">S</div><div><p class="kicker">SPECTRO</p><strong>ERP CLOUD</strong></div></div>
    <h1>Il lavoro, finalmente condiviso.</h1>
    <p class="lead">Un solo accesso per Android, Windows e Mac. Ogni persona vede esclusivamente ciò che serve.</p>
    <form id="login-form" class="form-stack">
      <label>Email<input name="email" type="email" autocomplete="email" required placeholder="nome@azienda.it"></label>
      <label>Password<input name="password" type="password" autocomplete="current-password" minlength="8" required placeholder="••••••••"></label>
      ${message ? `<p class="form-message">${escapeHtml(message)}</p>` : ''}
      <button class="primary" type="submit">Accedi</button>
    </form>
    <button class="link" id="show-signup">Richiedi un nuovo account</button>
    <p class="security-note">Sessione protetta da Supabase Auth · dati isolati per organizzazione</p>
  </section></main>`
  document.querySelector('#login-form')?.addEventListener('submit', onLogin)
  document.querySelector('#show-signup')?.addEventListener('click', () => renderSignup())
}

function renderSignup(message = '', savedEmail = '') {
  app.innerHTML = `<main class="center auth-bg"><section class="panel auth-panel">
    <button class="back" id="back-login">← Torna all’accesso</button>
    <p class="kicker">NUOVO ACCOUNT</p><h1>Richiedi l’accesso</h1>
    <p class="lead">Dopo la conferma email, un amministratore assegnerà ruolo e moduli.</p>
    <form id="signup-form" class="form-stack">
      <label>Nome e cognome<input name="name" required autocomplete="name"></label>
      <label>Email<input name="email" type="email" required autocomplete="email" value="${escapeHtml(savedEmail)}"></label>
      <label>Password<input name="password" type="password" minlength="8" required autocomplete="new-password"></label>
      ${message ? `<p class="form-message">${escapeHtml(message)}</p>` : ''}
      <button class="primary" type="submit">Invia richiesta</button>
      <button class="link" id="resend-confirmation" type="button">Reinvia email di conferma</button>
    </form>
  </section></main>`
  document.querySelector('#back-login')?.addEventListener('click', () => renderLogin())
  document.querySelector('#signup-form')?.addEventListener('submit', onSignup)
  document.querySelector('#resend-confirmation')?.addEventListener('click', onResendConfirmation)
}

async function onLogin(event: Event) {
  event.preventDefault()
  if (!supabase) return
  const form = new FormData(event.currentTarget as HTMLFormElement)
  const { error } = await supabase.auth.signInWithPassword({
    email: String(form.get('email')), password: String(form.get('password'))
  })
  if (error) renderLogin(friendlyAuthMessage(error.message))
}

async function onSignup(event: Event) {
  event.preventDefault()
  if (!supabase) return
  const form = new FormData(event.currentTarget as HTMLFormElement)
  const email = String(form.get('email')).trim()
  const { error } = await supabase.auth.signUp({
    email,
    password: String(form.get('password')),
    options: { data: { display_name: String(form.get('name')) } }
  })
  renderSignup(
    error ? friendlyAuthMessage(error.message) : 'Richiesta inviata. Apri solo l’email più recente, poi attendi l’assegnazione del ruolo.',
    email
  )
}

async function onResendConfirmation() {
  if (!supabase) return
  const input = document.querySelector<HTMLInputElement>('#signup-form input[name="email"]')
  const email = input?.value.trim() || ''
  if (!email) return renderSignup('Inserisci prima l’indirizzo email usato per registrarti.')
  const { error } = await supabase.auth.resend({ type: 'signup', email })
  renderSignup(
    error ? friendlyAuthMessage(error.message) : 'Nuovo link inviato. Apri solo l’email più recente: i link precedenti diventano obsoleti.',
    email
  )
}

async function loadAccess(user: User): Promise<AccessContext | null> {
  if (!supabase) return null
  const { data, error } = await supabase.rpc('my_access_context')
  if (error) throw error
  return data ? { ...data, permissions: data.permissions || [], user_id: data.user_id || user.id } : null
}

function renderPending(user: User, access: AccessContext | null, message = '') {
  app.innerHTML = `<main class="center"><section class="panel auth-panel">
    <p class="kicker">ACCOUNT VERIFICATO</p><h1>Ruolo in attesa</h1>
    <p>${escapeHtml(user.email)}</p>
    <p class="lead">L’account è valido, ma non è ancora associato a Spectro. Un amministratore può assegnarti un ruolo.</p>
    ${message ? `<p class="form-message">${escapeHtml(message)}</p>` : ''}
    <div class="button-row"><button class="primary" id="retry">Ricontrolla</button><button class="secondary" id="bootstrap">Configura prima azienda</button></div>
    <button class="link" id="logout">Disconnetti</button>
  </section></main>`
  document.querySelector('#retry')?.addEventListener('click', () => renderForUser(user))
  document.querySelector('#bootstrap')?.addEventListener('click', async () => {
    if (!supabase) return
    const { error } = await supabase.rpc('bootstrap_spectro_organization', { p_name: 'Spectro' })
    if (error) renderPending(user, access, error.message)
    else renderForUser(user)
  })
  document.querySelector('#logout')?.addEventListener('click', () => supabase?.auth.signOut())
}

async function renderWorkspace(user: User, access: AccessContext) {
  const allowed = modules.filter(module => access.permissions.includes(module.permission))
  let recent: Array<{ id: string; module: string; entity_type: string; payload: Record<string, unknown>; updated_at: string }> = []
  if (supabase) {
    const response = await supabase.rpc('list_erp_records_safe', {
      p_organization_id: access.organization_id, p_module: null, p_limit: 8
    })
    recent = response.data || []
  }
  app.innerHTML = `<div class="workspace">
    <aside class="sidebar">
      <div class="brand-lockup"><div class="brand-mark small">S</div><div><p class="kicker">SPECTRO</p><strong>ERP CLOUD</strong></div></div>
      <nav>${allowed.map((module, index) => `<button class="nav-item ${index === 0 ? 'active' : ''}" data-module="${module.key}"><span>${module.title}</span><b>↗</b></button>`).join('')}</nav>
      <div class="user-card"><span class="avatar">${escapeHtml((access.display_name || user.email || 'U').slice(0, 1).toUpperCase())}</span><div><strong>${escapeHtml(access.display_name || user.email)}</strong><small>${escapeHtml(access.role_name)}</small></div><button id="logout" title="Disconnetti">⏻</button></div>
    </aside>
    <main class="content">
      <header><div><p class="kicker">${escapeHtml(access.organization_name)}</p><h1>Buon lavoro, ${escapeHtml((access.display_name || '').split(' ')[0])}.</h1></div><div class="live"><i></i> Cloud attivo</div></header>
      <section class="hero"><div><span class="pill">MULTIUTENTE</span><h2>Ogni reparto allineato.<br><em>Con accessi controllati.</em></h2><p>Ruoli, dati e attività nello stesso spazio protetto e versionato.</p></div><div class="hero-stat"><strong>${allowed.length}</strong><span>moduli disponibili</span></div></section>
      <section><div class="section-head"><div><p class="kicker">IL TUO SPAZIO</p><h2>Moduli autorizzati</h2></div><span>${escapeHtml(access.role_name)}</span></div>
        <div class="module-grid">${allowed.map(module => `<article class="module-card ${module.accent}" data-module="${module.key}"><p>${module.eyebrow}</p><h3>${module.title}</h3><span>${module.description}</span><button>Apri modulo <b>→</b></button></article>`).join('') || '<p class="empty">Nessun modulo assegnato a questo ruolo.</p>'}</div>
      </section>
      <section class="recent"><div class="section-head"><div><p class="kicker">AGGIORNAMENTI</p><h2>Attività recenti</h2></div></div>
        <div class="recent-list">${recent.length ? recent.map(row => `<div class="recent-row"><span class="dot ${row.module}"></span><div><strong>${escapeHtml(String(row.payload.title || row.payload.name || row.payload.orderCode || row.entity_type))}</strong><small>${escapeHtml(row.module)} · ${new Date(row.updated_at).toLocaleString('it-IT')}</small></div></div>`).join('') : '<p class="empty">Lo spazio cloud è pronto. Le prime attività condivise appariranno qui.</p>'}</div>
      </section>
    </main>
  </div>`
  document.querySelector('#logout')?.addEventListener('click', () => supabase?.auth.signOut())
  document.querySelectorAll<HTMLElement>('[data-module]').forEach(element => element.addEventListener('click', () => {
    const module = modules.find(item => item.key === element.dataset.module)
    if (module?.key === 'users') void renderAccessManagement(user, access)
    else if (module?.key === 'sync') void renderCloudStatus(user, access)
    else if (module) void renderModule(user, access, module)
  }))
}

async function renderCloudStatus(user: User, access: AccessContext) {
  if (!supabase || !access.organization_id) return
  app.innerHTML = '<main class="center"><div class="loader"></div></main>'
  const { data, error } = await supabase.rpc('get_workspace_sync_state', { p_organization_id: access.organization_id })
  const workspace = data as { exists?: boolean; revision?: number; record_count?: number; size_bytes?: number; updated_at?: string; updated_by?: string } | null
  const size = workspace?.size_bytes || 0
  const sizeLabel = size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${(size / 1024).toFixed(1)} KB`
  app.innerHTML = `<main class="module-page">
    <header class="module-header"><button id="home" class="back">← Dashboard</button><div><p class="kicker">SICUREZZA DATI</p><h1>Workspace cloud</h1><p>Stato della copia Android completa e del mirror consultabile dal portale.</p></div><div class="live"><i></i>${workspace?.exists ? `Rev. ${workspace.revision}` : 'In attesa'}</div></header>
    ${error ? `<p class="form-message">${escapeHtml(error.message)}</p>` : ''}
    <section class="hero"><div><span class="pill">SHA-256 + REVISIONI</span><h2>${workspace?.exists ? `Revisione ${workspace.revision} protetta.` : 'Nessun workspace pubblicato.'}</h2><p>${workspace?.exists ? `${workspace.record_count || 0} record · ${sizeLabel} · pubblicata da ${escapeHtml(workspace.updated_by || 'utente Spectro')}` : 'Apri l’app Android con un account autorizzato per creare la prima revisione.'}</p>${workspace?.updated_at ? `<p class="security-note">Ultimo aggiornamento: ${new Date(workspace.updated_at).toLocaleString('it-IT')}</p>` : ''}</div><div class="hero-stat"><strong>${workspace?.record_count || 0}</strong><span>record verificati</span></div></section>
    <section class="empty-state"><h2>Protezione conflitti attiva</h2><p>Una postazione può pubblicare soltanto se parte dalla stessa revisione presente nel cloud. In caso contrario l’operazione viene fermata senza sovrascritture.</p><button id="refresh-sync" class="primary">Aggiorna stato</button></section>
  </main>`
  document.querySelector('#home')?.addEventListener('click', () => renderWorkspace(user, access))
  document.querySelector('#refresh-sync')?.addEventListener('click', () => renderCloudStatus(user, access))
}

type MemberRow = {
  user_id: string
  email: string
  display_name: string
  status: string
  role_id: string
  role_name: string
  permission_overrides: Record<string, boolean>
}

type RoleRow = { id: string; name: string; description: string }
type PermissionRow = { key: string; module: string; label: string; description: string }
type CloudRecord = { id: string; module: string; entity_type: string; payload: Record<string, unknown>; updated_at: string; revision: number }

async function renderAccessManagement(user: User, access: AccessContext, message = '') {
  if (!supabase || !access.organization_id) return
  app.innerHTML = '<main class="center"><div class="loader"></div></main>'
  const [membersResult, rolesResult, permissionsResult] = await Promise.all([
    supabase.rpc('admin_list_members', { p_organization_id: access.organization_id }),
    supabase.from('roles').select('id,name,description').eq('organization_id', access.organization_id).order('name'),
    supabase.from('permissions').select('key,module,label,description').order('module').order('key')
  ])
  const error = membersResult.error || rolesResult.error || permissionsResult.error
  const members = (membersResult.data || []) as MemberRow[]
  const roles = (rolesResult.data || []) as RoleRow[]
  const permissions = (permissionsResult.data || []) as PermissionRow[]
  const roleOptions = (selected: string) => roles.map(role => `<option value="${role.id}" ${role.id === selected ? 'selected' : ''}>${escapeHtml(role.name)}</option>`).join('')

  app.innerHTML = `<main class="module-page access-page">
    <header class="module-header"><button id="home" class="back">← Dashboard</button><div><p class="kicker">AMMINISTRAZIONE</p><h1>Utenti e accessi</h1><p>Assegna un ruolo o personalizza singoli permessi.</p></div><div class="live"><i></i>${members.length} utenti</div></header>
    ${message || error ? `<p class="form-message">${escapeHtml(message || error?.message)}</p>` : ''}
    <section class="invite-panel"><div><p class="kicker">AGGIUNGI PERSONA</p><h2>Associa un account registrato</h2><p>L’utente deve essersi già registrato e aver confermato l’email.</p></div>
      <form id="add-member"><input name="email" type="email" required placeholder="nome@azienda.it"><select name="role" required>${roleOptions(roles[0]?.id || '')}</select><button class="primary" type="submit">Assegna accesso</button></form>
    </section>
    <section class="member-list">${members.map(member => `<article class="member-card" data-user="${member.user_id}">
      <div class="member-summary"><span class="avatar">${escapeHtml((member.display_name || member.email).slice(0, 1).toUpperCase())}</span><div><h3>${escapeHtml(member.display_name || member.email)}</h3><p>${escapeHtml(member.email)} · ${escapeHtml(member.status)}</p></div><label>Ruolo<select class="role-select">${roleOptions(member.role_id)}</select></label></div>
      <details><summary>Personalizza permessi <span>${Object.keys(member.permission_overrides || {}).length} override</span></summary>
        <div class="permission-grid">${permissions.map(permission => {
          const override = member.permission_overrides?.[permission.key]
          return `<label class="permission-row"><div><strong>${escapeHtml(permission.label)}</strong><small>${escapeHtml(permission.description)}</small></div><select class="override-select" data-permission="${permission.key}">
            <option value="default" ${override === undefined ? 'selected' : ''}>Dal ruolo</option>
            <option value="allow" ${override === true ? 'selected' : ''}>Consenti</option>
            <option value="deny" ${override === false ? 'selected' : ''}>Nega</option>
          </select></label>`
        }).join('')}</div>
      </details>
    </article>`).join('') || '<p class="empty">Nessun utente associato.</p>'}</section>
  </main>`
  document.querySelector('#home')?.addEventListener('click', () => renderWorkspace(user, access))
  document.querySelector<HTMLFormElement>('#add-member')?.addEventListener('submit', async event => {
    event.preventDefault()
    const form = new FormData(event.currentTarget as HTMLFormElement)
    const { error: assignError } = await supabase.rpc('admin_assign_member_by_email', {
      p_organization_id: access.organization_id,
      p_email: String(form.get('email')),
      p_role_id: String(form.get('role'))
    })
    await renderAccessManagement(user, access, assignError?.message || 'Accesso assegnato correttamente.')
  })
  document.querySelectorAll<HTMLElement>('.member-card').forEach(card => {
    const userId = card.dataset.user!
    card.querySelector<HTMLSelectElement>('.role-select')?.addEventListener('change', async event => {
      const roleId = (event.currentTarget as HTMLSelectElement).value
      const { error: roleError } = await supabase.rpc('assign_member_role', {
        p_organization_id: access.organization_id, p_user_id: userId, p_role_id: roleId
      })
      await renderAccessManagement(user, access, roleError?.message || 'Ruolo aggiornato.')
    })
    card.querySelectorAll<HTMLSelectElement>('.override-select').forEach(select => select.addEventListener('change', async event => {
      const control = event.currentTarget as HTMLSelectElement
      const permission = control.dataset.permission!
      const params = { p_organization_id: access.organization_id, p_user_id: userId, p_permission_key: permission }
      const result = control.value === 'default'
        ? await supabase.rpc('admin_clear_permission_override', params)
        : await supabase.rpc('admin_set_permission_override', { ...params, p_allowed: control.value === 'allow' })
      await renderAccessManagement(user, access, result.error?.message || 'Permesso aggiornato.')
    }))
  })
}

async function renderModule(user: User, access: AccessContext, module: Module) {
  if (!supabase) return
  app.innerHTML = '<main class="center"><div class="loader"></div></main>'
  const { data, error } = await supabase.rpc('list_erp_records_safe', {
    p_organization_id: access.organization_id, p_module: module.key, p_limit: 100
  })
  const records = (data || []) as CloudRecord[]
  app.innerHTML = `<main class="module-page"><header class="module-header"><button id="home" class="back">← Dashboard</button><div><p class="kicker">${module.eyebrow}</p><h1>${module.title}</h1><p>${module.description}</p></div><div class="live"><i></i>${data?.length || 0} record</div></header>
    ${error ? `<p class="form-message">${escapeHtml(error.message)}</p>` : ''}
    <div class="record-grid">${records.length ? records.map(row => `<article class="record-card"><p>${escapeHtml(row.entity_type)}</p><h3>${escapeHtml(String(row.payload.title || row.payload.name || row.payload.clientName || row.payload.orderCode || 'Record Spectro'))}</h3><span>Aggiornato ${new Date(row.updated_at).toLocaleString('it-IT')} · rev. ${row.revision}</span></article>`).join('') : `<section class="empty-state"><div class="brand-mark small">S</div><h2>Modulo pronto</h2><p>I dati condivisi di ${module.title} compariranno qui dopo la prima sincronizzazione.</p></section>`}</div>
  </main>`
  document.querySelector('#home')?.addEventListener('click', () => renderWorkspace(user, access))
}

async function renderForUser(user: User) {
  app.innerHTML = '<main class="center"><div class="loader"></div></main>'
  try {
    const access = await loadAccess(user)
    if (!access?.organization_id) renderPending(user, access)
    else await renderWorkspace(user, access)
  } catch (error) {
    renderPending(user, null, error instanceof Error ? error.message : 'Impossibile verificare i permessi.')
  }
}

async function start() {
  if (!supabase) return renderSetup()
  const { data } = await supabase.auth.getSession()
  if (data.session?.user) await renderForUser(data.session.user)
  else renderLogin()
  supabase.auth.onAuthStateChange((event, session) => {
    if (event === 'SIGNED_OUT') renderLogin()
    if (event === 'SIGNED_IN' && session?.user) void renderForUser(session.user)
  })
  if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'))
}

void start()
