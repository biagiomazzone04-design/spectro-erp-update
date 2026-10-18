import { createClient, type User } from '@supabase/supabase-js'
import './style.css'
import { renderAnalyticalModule, type CloudRecord } from './erp'
import { formatItalianDate, moduleLabel, translateCode } from './i18n'

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
  preview_name?: string
  preview_role?: string
}

let adminPreview: { user: User; access: AccessContext } | null = null
let activeUserId: string | null = null
let activeScrollKey = ''

const saveCurrentScroll = () => { if (activeScrollKey) sessionStorage.setItem(activeScrollKey, String(window.scrollY)) }
const activateScrollMemory = (userId: string, route: string) => { saveCurrentScroll(); activeScrollKey = `spectro-scroll:${userId}:${route}` }
const restoreRouteScroll = () => { const position = Number(sessionStorage.getItem(activeScrollKey) || 0); requestAnimationFrame(() => requestAnimationFrame(() => window.scrollTo({top:position,behavior:'auto'}))) }
window.addEventListener('scroll', saveCurrentScroll, { passive:true })

type UiPreferences = {
  preset: string; accent: string; secondary: string; background: string; surface: string; text: string
  font: string; textScale: number; density: number; radius: number; sidebar: number; motion: string
}

const defaultUiPreferences: UiPreferences = {
  preset: 'spectro', accent: '#ff2db2', secondary: '#ff8a00', background: '#050507', surface: '#111115', text: '#f7f7fa',
  font: 'Inter', textScale: 1, density: 1, radius: 1, sidebar: 248, motion: 'full'
}

const preferenceKey = (userId: string) => `spectro-ui-device-v1:${userId}`
const readUiPreferences = (userId: string): UiPreferences => {
  try { return { ...defaultUiPreferences, ...JSON.parse(localStorage.getItem(preferenceKey(userId)) || '{}') } }
  catch { return { ...defaultUiPreferences } }
}
const applyUiPreferences = (preferences: UiPreferences) => {
  const root = document.documentElement
  root.style.setProperty('--pink', preferences.accent); root.style.setProperty('--orange', preferences.secondary)
  root.style.setProperty('--app-bg', preferences.background); root.style.setProperty('--surface', preferences.surface)
  root.style.setProperty('--app-text', preferences.text); root.style.setProperty('--text-scale', String(preferences.textScale))
  root.style.setProperty('--density', String(preferences.density)); root.style.setProperty('--radius-scale', String(preferences.radius))
  root.style.setProperty('--sidebar-width', `${preferences.sidebar}px`); root.style.setProperty('--ui-font', `${preferences.font}, Inter, system-ui, sans-serif`)
  root.dataset.theme = preferences.preset; root.dataset.motion = preferences.motion
}
const saveUiPreferences = (userId: string, preferences: UiPreferences) => {
  localStorage.setItem(preferenceKey(userId), JSON.stringify(preferences)); applyUiPreferences(preferences)
}

function installInteractiveMotion() {
  if (document.documentElement.dataset.motionReady) return
  document.documentElement.dataset.motionReady = 'true'
  let frame = 0
  document.addEventListener('pointermove', event => {
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => { document.documentElement.style.setProperty('--pointer-x', `${event.clientX}px`); document.documentElement.style.setProperty('--pointer-y', `${event.clientY}px`) })
  }, { passive:true })
  document.addEventListener('pointerdown', event => {
    const button = (event.target as HTMLElement).closest('button'); if (!button || document.documentElement.dataset.motion === 'none') return
    const rect = button.getBoundingClientRect(); const ripple = document.createElement('i'); ripple.className = 'click-ripple'; ripple.style.left = `${event.clientX - rect.left}px`; ripple.style.top = `${event.clientY - rect.top}px`; button.appendChild(ripple); ripple.addEventListener('animationend', () => ripple.remove())
  })
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
  { key: 'issues', eyebrow: 'Controllo operativo', title: 'Timeline e criticità', description: 'Eventi, ritardi, responsabilità e risoluzioni.', permission: 'issues.view', accent: 'pink' },
  { key: 'cotton', eyebrow: 'Linea', title: 'Cotone', description: 'Ordini reattivi e pigmento.', permission: 'cotton.view', accent: 'pink' },
  { key: 'zentha', eyebrow: 'Business unit', title: 'Zentha', description: 'Catalogo, ordini sport e marginalità.', permission: 'zentha.view', accent: 'orange' },
  { key: 'finance', eyebrow: 'Controllo', title: 'Economia', description: 'Ricavi, costi, pagamenti e KPI.', permission: 'finance.view', accent: 'pink' },
  { key: 'tasks', eyebrow: 'Organizzazione', title: 'Attività', description: 'Scadenze e attività operative.', permission: 'tasks.view', accent: 'orange' },
  { key: 'sync', eyebrow: 'Sicurezza dati', title: 'Workspace cloud', description: 'Revisioni, integrità e stato dell’ultima pubblicazione.', permission: 'sync.use', accent: 'orange' },
  { key: 'users', eyebrow: 'Amministrazione', title: 'Utenti e accessi', description: 'Ruoli e permessi personalizzati.', permission: 'users.manage', accent: 'pink' }
]

const moduleIcons: Record<string, string> = {
  dashboard: '⌂', orders: '▤', production: '◫', inventory: '▦', clients: '◉', issues: '!', cotton: '◌',
  zentha: '◆', finance: '€', tasks: '✓', sync: '☁', users: '⚙'
}

function allowedModules(access: AccessContext) {
  return modules.filter(module => access.permissions.includes(module.permission))
}

function mountShell(user: User, access: AccessContext, activeKey: string, content: string) {
  document.body.classList.remove('drawer-open')
  activateScrollMemory(user.id, activeKey)
  localStorage.setItem(`spectro-last-route:${user.id}`, activeKey)
  let uiPreferences = readUiPreferences(user.id)
  applyUiPreferences(uiPreferences)
  const allowed = allowedModules(access)
  const navigation = [
    { key: 'dashboard', title: 'Dashboard', eyebrow: 'Panoramica', permission: 'dashboard.view' },
    ...allowed
  ].filter((item, index, list) => index === list.findIndex(candidate => candidate.key === item.key))
  app.innerHTML = `<div class="workspace control-tower">
    <aside class="sidebar">
      <div class="brand-lockup"><div class="brand-mark small">S</div><div><p class="kicker">SPECTRO</p><strong>CONTROL TOWER</strong></div></div>
      <div class="workspace-label"><span>AZIENDA</span><strong>${escapeHtml(access.organization_name || 'Spectro')}</strong></div>
      <nav>${navigation.map(item => `<button class="nav-item ${item.key === activeKey ? 'active' : ''}" data-route="${item.key}"><b>${moduleIcons[item.key] || '•'}</b><span>${escapeHtml(item.title)}</span><i></i></button>`).join('')}</nav>
      <div class="sidebar-status"><i></i><div><strong>Cloud operativo</strong><small>Dati protetti e sincronizzati</small></div></div>
      <div class="user-card"><span class="avatar">${escapeHtml((access.display_name || user.email || 'U').slice(0, 1).toUpperCase())}</span><div><strong>${escapeHtml(access.display_name || user.email)}</strong><small>${escapeHtml(access.role_name || 'Utente')}</small></div><button id="logout" title="Disconnetti" aria-label="Disconnetti">⏻</button></div>
    </aside>
    <div class="desktop-stage">
      ${access.preview_name ? `<div class="preview-banner"><strong>Anteprima utente: ${escapeHtml(access.preview_name)}</strong><span>Stai vedendo Spectro con i permessi di ${escapeHtml(access.preview_role || 'questo utente')}.</span><button id="exit-preview">Torna ad amministratore</button></div>` : ''}
      <header class="topbar"><div><p class="breadcrumb">Spectro / <strong>${escapeHtml(activeKey === 'dashboard' ? 'Dashboard' : modules.find(module => module.key === activeKey)?.title || 'Area di lavoro')}</strong></p><span>${formatItalianDate(new Date().toISOString())}</span></div><button class="global-search command-trigger" id="open-command" type="button">⌕<span>Cerca o apri un modulo…</span><kbd>Ctrl K</kbd></button><div class="topbar-actions"><button id="open-notifications" title="Notifiche" aria-label="Notifiche">♢<i id="notification-count" hidden>0</i></button><button id="open-customization" title="Personalizza interfaccia" aria-label="Personalizza interfaccia">◐</button><span class="role-badge">${escapeHtml(access.role_name || 'Utente')}</span></div></header>
      <main class="content control-content" id="view-host">${content}</main>
      <div id="notification-panel" class="ui-panel" hidden><button class="ui-panel-scrim" data-close-panel aria-label="Chiudi"></button><aside><header><div><p class="kicker">CENTRO NOTIFICHE</p><h2>Attenzione richiesta</h2></div><button data-close-panel>×</button></header><div id="notification-list" class="notification-center"><p class="muted">Caricamento notifiche…</p></div></aside></div>
      <div id="customization-panel" class="ui-panel" hidden><button class="ui-panel-scrim" data-close-panel aria-label="Chiudi"></button><aside class="customization-drawer"><header><div><p class="kicker">SOLO SU QUESTO DISPOSITIVO</p><h2>Personalizza Spectro</h2></div><button data-close-panel>×</button></header><div class="customization-content">
        <section><h3>Stile rapido</h3><div class="theme-presets"><button data-theme-preset="spectro">Spectro Neon</button><button data-theme-preset="ocean">Ocean</button><button data-theme-preset="emerald">Emerald</button><button data-theme-preset="light">Chiaro</button><button data-theme-preset="contrast">Alto contrasto</button></div></section>
        <section><h3>Colori completi</h3><div class="customization-grid"><label>Colore principale<input name="accent" type="color" value="${uiPreferences.accent}"></label><label>Colore secondario<input name="secondary" type="color" value="${uiPreferences.secondary}"></label><label>Sfondo<input name="background" type="color" value="${uiPreferences.background}"></label><label>Blocchi e pannelli<input name="surface" type="color" value="${uiPreferences.surface}"></label><label>Testo<input name="text" type="color" value="${uiPreferences.text}"></label></div></section>
        <section><h3>Testo e struttura</h3><label>Font<select name="font"><option value="Inter">Inter</option><option value="Arial">Arial</option><option value="Verdana">Verdana</option><option value="Georgia">Georgia</option><option value="Trebuchet MS">Trebuchet</option><option value="system-ui">Sistema</option></select></label><label>Grandezza testo <output id="text-scale-output">${Math.round(uiPreferences.textScale * 100)}%</output><input name="textScale" type="range" min="0.85" max="1.35" step="0.05" value="${uiPreferences.textScale}"></label><label>Spaziatura blocchi <output id="density-output">${Math.round(uiPreferences.density * 100)}%</output><input name="density" type="range" min="0.75" max="1.35" step="0.05" value="${uiPreferences.density}"></label><label>Arrotondamento <output id="radius-output">${Math.round(uiPreferences.radius * 100)}%</output><input name="radius" type="range" min="0" max="1.6" step="0.1" value="${uiPreferences.radius}"></label><label>Larghezza menu <output id="sidebar-output">${uiPreferences.sidebar}px</output><input name="sidebar" type="range" min="220" max="340" step="4" value="${uiPreferences.sidebar}"></label><label>Animazioni<select name="motion"><option value="full">Complete</option><option value="reduced">Ridotte</option><option value="none">Disattivate</option></select></label></section>
        <p class="local-preference-note">Queste impostazioni restano nel browser corrente e non modificano Android, altri PC o altri utenti.</p><button id="reset-customization" class="secondary">Ripristina impostazioni</button>
      </div></aside></div>
      <div id="command-panel" class="command-panel" hidden><button class="command-scrim" data-close-command aria-label="Chiudi"></button><section><label>⌕<input id="command-input" type="search" placeholder="Cerca un modulo, un cliente, un ordine…"></label><div id="command-results"><button data-command-action="search" data-command-search="cerca dati cliente ordine tessuto attività"><i>⌕</i><span><strong>Cerca in tutti i dati</strong><small>Scrivi una parola e premi Invio</small></span><b>Invio ↵</b></button>${navigation.map(item => `<button data-command-route="${item.key}" data-command-search="${escapeHtml(`${item.title} ${item.eyebrow}`.toLowerCase())}"><i>${moduleIcons[item.key] || '•'}</i><span><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(item.eyebrow)}</small></span><b>Apri →</b></button>`).join('')}<button data-command-action="customize" data-command-search="tema colori font personalizza interfaccia"><i>◐</i><span><strong>Personalizza Spectro</strong><small>Tema, colori, testo e blocchi</small></span><b>Apri →</b></button></div></section></div>
    </div>
  </div>`
  document.querySelector('#logout')?.addEventListener('click', () => supabase?.auth.signOut())
  document.querySelector('#exit-preview')?.addEventListener('click', () => {
    const original = adminPreview
    adminPreview = null
    if (original) void renderWorkspace(original.user, original.access)
  })
  document.querySelectorAll<HTMLElement>('[data-route]').forEach(element => element.addEventListener('click', () => {
    document.body.classList.remove('drawer-open')
    const key = element.dataset.route
    const navigate = () => { if (key === 'dashboard') void renderWorkspace(user, access)
      else { const module = modules.find(item => item.key === key); if (module?.key === 'users') void renderAccessManagement(user, access); else if (module?.key === 'sync') void renderCloudStatus(user, access); else if (module) void renderModule(user, access, module) } }
    const viewDocument = document as Document & { startViewTransition?: (callback: () => void) => void }
    if (viewDocument.startViewTransition && document.documentElement.dataset.motion === 'full') viewDocument.startViewTransition(navigate); else navigate()
  }))
  const customizationPanel = document.querySelector<HTMLElement>('#customization-panel')!
  const notificationPanel = document.querySelector<HTMLElement>('#notification-panel')!
  const commandPanel = document.querySelector<HTMLElement>('#command-panel')!
  const closeUiPanels = () => { customizationPanel.hidden = true; notificationPanel.hidden = true; commandPanel.hidden = true; document.body.classList.remove('drawer-open') }
  const openPanel = (panel: HTMLElement) => { closeUiPanels(); panel.hidden = false; document.body.classList.add('drawer-open') }
  document.querySelector('#open-customization')?.addEventListener('click', () => openPanel(customizationPanel))
  document.querySelector('#open-notifications')?.addEventListener('click', () => openPanel(notificationPanel))
  document.querySelectorAll('[data-close-panel],[data-close-command]').forEach(button => button.addEventListener('click', closeUiPanels))
  const commandInput = document.querySelector<HTMLInputElement>('#command-input')!
  const openCommand = () => { openPanel(commandPanel); requestAnimationFrame(() => commandInput.focus()) }
  document.querySelector('#open-command')?.addEventListener('click', openCommand)
  commandInput.addEventListener('input', () => { const term = commandInput.value.trim().toLowerCase(); document.querySelectorAll<HTMLElement>('[data-command-search]').forEach(item => { item.hidden = Boolean(term && !item.dataset.commandSearch?.includes(term)) }) })
  const searchAllData = () => { const query = commandInput.value.trim(); if (query) void renderGlobalSearch(user, access, query) }
  commandInput.addEventListener('keydown', event => { if (event.key === 'Enter') searchAllData() })
  document.querySelector('[data-command-action="search"]')?.addEventListener('click', searchAllData)
  document.querySelectorAll<HTMLElement>('[data-command-route]').forEach(element => element.addEventListener('click', () => document.querySelector<HTMLElement>(`[data-route="${element.dataset.commandRoute}"]`)?.click()))
  document.querySelector('[data-command-action="customize"]')?.addEventListener('click', () => openPanel(customizationPanel))
  const controls = customizationPanel.querySelectorAll<HTMLInputElement|HTMLSelectElement>('input[name],select[name]')
  customizationPanel.querySelector<HTMLSelectElement>('select[name="font"]')!.value = uiPreferences.font
  customizationPanel.querySelector<HTMLSelectElement>('select[name="motion"]')!.value = uiPreferences.motion
  const refreshPreferences = () => {
    const read = (name: string) => customizationPanel.querySelector<HTMLInputElement|HTMLSelectElement>(`[name="${name}"]`)!.value
    uiPreferences = { ...uiPreferences, accent:read('accent'), secondary:read('secondary'), background:read('background'), surface:read('surface'), text:read('text'), font:read('font'), textScale:Number(read('textScale')), density:Number(read('density')), radius:Number(read('radius')), sidebar:Number(read('sidebar')), motion:read('motion') }
    customizationPanel.querySelector<HTMLOutputElement>('#text-scale-output')!.value = `${Math.round(uiPreferences.textScale * 100)}%`; customizationPanel.querySelector<HTMLOutputElement>('#density-output')!.value = `${Math.round(uiPreferences.density * 100)}%`; customizationPanel.querySelector<HTMLOutputElement>('#radius-output')!.value = `${Math.round(uiPreferences.radius * 100)}%`; customizationPanel.querySelector<HTMLOutputElement>('#sidebar-output')!.value = `${uiPreferences.sidebar}px`
    saveUiPreferences(user.id, uiPreferences)
  }
  controls.forEach(control => control.addEventListener('input', refreshPreferences))
  const presetValues: Record<string, Partial<UiPreferences>> = { spectro:{accent:'#ff2db2',secondary:'#ff8a00',background:'#050507',surface:'#111115',text:'#f7f7fa'}, ocean:{accent:'#20c7ff',secondary:'#6f78ff',background:'#05080d',surface:'#101722',text:'#f4fbff'}, emerald:{accent:'#35e69a',secondary:'#d5ed52',background:'#050a08',surface:'#101813',text:'#f4fff9'}, light:{accent:'#c0167f',secondary:'#d96b00',background:'#f4f2f5',surface:'#ffffff',text:'#17131a'}, contrast:{accent:'#00e5ff',secondary:'#ffea00',background:'#000000',surface:'#080808',text:'#ffffff'} }
  const syncPreferenceControls = () => { Object.entries(uiPreferences).forEach(([name,value]) => { const control = customizationPanel.querySelector<HTMLInputElement|HTMLSelectElement>(`[name="${name}"]`); if (control) control.value = String(value) }); refreshPreferences() }
  customizationPanel.querySelectorAll<HTMLButtonElement>('[data-theme-preset]').forEach(button => button.addEventListener('click', () => { uiPreferences = { ...uiPreferences, preset:button.dataset.themePreset || 'spectro', ...presetValues[button.dataset.themePreset || 'spectro'] }; syncPreferenceControls() }))
  customizationPanel.querySelector('#reset-customization')?.addEventListener('click', () => { localStorage.removeItem(preferenceKey(user.id)); uiPreferences = { ...defaultUiPreferences }; syncPreferenceControls() })
  document.onkeydown = event => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); openCommand() }
    if (event.key === 'Escape') closeUiPanels()
  }
  return document.querySelector<HTMLElement>('#view-host')!
}

const escapeHtml = (value: unknown) => String(value ?? '')
  .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;').replaceAll("'", '&#039;')

const recordDate = (row: CloudRecord) => String(
  row.payload.date || row.payload.orderDate || row.payload.receivedDate || row.payload.dueDate || row.payload.openedDate || row.updated_at || ''
).slice(0, 10)

function updateNotificationCenter(records: CloudRecord[]) {
  const list = document.querySelector<HTMLElement>('#notification-list'); const badge = document.querySelector<HTMLElement>('#notification-count')
  if (!list || !badge) return
  const today = new Date().toISOString().slice(0, 10); const terminal = /COMPLETED|DELIVERED|CLOSED|CANCELLED|DONE|RESOLVED|PAID/
  const due = (row: CloudRecord) => String(row.payload.dueDate || row.payload.expectedDeliveryDate || '').slice(0, 10)
  const notices = [
    ...records.filter(row => row.entity_type === 'operational_task' && due(row) && due(row) < today && !terminal.test(String(row.payload.status))).map(row => ({ tone:'danger', title:String(row.payload.title || 'Attività scaduta'), detail:`Scadenza ${formatItalianDate(due(row))} · ${String(row.payload.clientName || 'Attività interna')}` })),
    ...records.filter(row => row.entity_type === 'work_order' && due(row) && due(row) < today && !terminal.test(String(row.payload.status))).map(row => ({ tone:'warn', title:String(row.payload.orderCode || row.payload.title || 'Ordine in ritardo'), detail:`Consegna ${formatItalianDate(due(row))} · ${String(row.payload.client || 'Cliente non indicato')}` })),
    ...records.filter(row => row.entity_type === 'inventory_item' && Number(row.payload.quantityMeters || 0) <= Number(row.payload.minimumQuantityMeters || 0)).map(row => ({ tone:'info', title:String(row.payload.fabricName || 'Scorta sotto soglia'), detail:`Disponibili ${Number(row.payload.quantityMeters || 0).toLocaleString('it-IT')} m` }))
  ].slice(0, 30)
  badge.textContent = String(notices.length); badge.hidden = notices.length === 0
  list.innerHTML = notices.length ? notices.map(notice => `<article class="notification-item ${notice.tone}"><i></i><div><strong>${escapeHtml(notice.title)}</strong><span>${escapeHtml(notice.detail)}</span></div></article>`).join('') : '<div class="notification-empty"><b>✓</b><strong>Tutto sotto controllo</strong><span>Non ci sono notifiche urgenti.</span></div>'
}

async function hydrateAttachmentUrls(records: CloudRecord[]) {
  if (!supabase) return
  const paths = [...new Set(records.flatMap(row => Array.isArray(row.payload.attachmentPaths) ? row.payload.attachmentPaths.map(String) : []))]
  if (!paths.length) return
  const { data } = await supabase.storage.from('spectro-attachments').createSignedUrls(paths, 3600)
  const urls = new Map((data || []).filter(item => item.signedUrl).map(item => [item.path,item.signedUrl]))
  records.forEach(row => { const rowPaths = Array.isArray(row.payload.attachmentPaths) ? row.payload.attachmentPaths.map(String) : []; row.payload._attachmentUrls = rowPaths.map(path => urls.get(path)).filter(Boolean) })
}

function periodDates(mode: string) {
  const now = new Date()
  const end = now.toISOString().slice(0, 10)
  if (mode === 'current_month') return { start: `${end.slice(0, 7)}-01`, end }
  if (mode === 'previous_month') {
    const first = new Date(now.getFullYear(), now.getMonth() - 1, 1)
    const last = new Date(now.getFullYear(), now.getMonth(), 0)
    return { start: first.toISOString().slice(0, 10), end: last.toISOString().slice(0, 10) }
  }
  const days = Number(mode || 30)
  const start = new Date(now); start.setDate(start.getDate() - Math.max(days - 1, 0))
  return { start: start.toISOString().slice(0, 10), end }
}

const financeFields = new Set(['amount','dailyFixedCost','defaultPrintPrice','fabricCost','fabricCostPerMeter','fabricLossCost','fabricSalePricePerMeter','grossProfit','grossRevenue','labelCost','liningCost','manualGrossRevenue','markupPercent','materialCostTotal','otherCost','otherCosts','packagingCost','paperPrintCost','priceListName','printCost','printPricePerMeter','productionCostPerMeter','protectionPaperCost','purchasePricePerMeter','refundCost','ribCost','sewingCost','splitPercentA','splitPercentB','standardCost','standardPrice','unitCost','unitPrice','accessoryCost','cuttingCost','fabricUsedCost'])

function recordsForAccess(records: CloudRecord[], access: AccessContext) {
  const visibleModules = new Set(allowedModules(access).map(module => module.key))
  return records.filter(row => visibleModules.has(row.module)).map(row => {
    if (access.permissions.includes('finance.view')) return row
    return { ...row, payload: Object.fromEntries(Object.entries(row.payload).filter(([key]) => !financeFields.has(key))) }
  })
}

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
      <label>Password<input name="password" type="password" autocomplete="current-password" minlength="6" required placeholder="••••••••"></label>
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
    options: {
      data: { display_name: String(form.get('name')) },
      emailRedirectTo: `${window.location.origin}/`
    }
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
  const { error } = await supabase.auth.resend({
    type: 'signup',
    email,
    options: { emailRedirectTo: `${window.location.origin}/` }
  })
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
  const allowed = allowedModules(access)
  mountShell(user, access, 'dashboard', '<div class="loader-shell"><div class="loader"></div></div>')
  let allRecords: CloudRecord[] = []
  if (supabase) {
    const response = await supabase.rpc('list_erp_records_safe', { p_organization_id: access.organization_id, p_module: null, p_limit: 500 })
    allRecords = recordsForAccess(response.data || [], access)
  }
  updateNotificationCenter(allRecords)
  const host = document.querySelector<HTMLElement>('#view-host')!
  const terminal = /COMPLETED|DELIVERED|CLOSED|CANCELLED|DONE|RESOLVED|PAID/
  const today = new Date().toISOString().slice(0, 10)
  const stock = allRecords.filter(row => row.entity_type === 'inventory_item')
  const stockMeters = stock.reduce((sum, row) => sum + Number(row.payload.quantityMeters || 0), 0)
  const lowStock = stock.filter(row => Number(row.payload.quantityMeters || 0) <= Number(row.payload.minimumQuantityMeters || 0))
  let periodMode = 'current_month'
  let range = periodDates(periodMode)

  const drawDashboard = () => {
    const periodRecords = allRecords.filter(row => { const value = recordDate(row); return value && value >= range.start && value <= range.end })
    const recent = periodRecords.slice(0, 8)
    const byType = (type: string) => periodRecords.filter(row => row.entity_type === type)
    const dueDate = (row: CloudRecord) => String(row.payload.dueDate || row.payload.expectedDeliveryDate || '')
    const orders = byType('work_order'); const production = byType('production_record'); const tasks = byType('operational_task')
    const activeOrders = orders.filter(row => !terminal.test(String(row.payload.status))).length
    const openTasks = tasks.filter(row => !terminal.test(String(row.payload.status))).length
    const overdueOrders = orders.filter(row => dueDate(row) && dueDate(row) < today && !terminal.test(String(row.payload.status))).length
    const overdueTasks = tasks.filter(row => dueDate(row) && dueDate(row) < today && !terminal.test(String(row.payload.status))).length
    const openIssues = periodRecords.filter(row => ['client_issue','order_timeline_event'].includes(row.entity_type) && !terminal.test(String(row.payload.status)))
    const dayCount = Math.max(1, Math.min(31, Math.round((new Date(range.end).getTime() - new Date(range.start).getTime()) / 86400000) + 1))
    const chartDays = Array.from({ length: dayCount }, (_, index) => { const day = new Date(`${range.start}T12:00:00`); day.setDate(day.getDate() + index); return day.toISOString().slice(0, 10) })
    const productionTotals = chartDays.map(day => production.filter(row => String(row.payload.date).slice(0, 10) === day).reduce((sum, row) => sum + Number(row.payload.printedPaperMeters || 0), 0))
    const maximumProduction = Math.max(...productionTotals, 1)
    const printedPaper = production.reduce((sum, row) => sum + Number(row.payload.printedPaperMeters || 0), 0)
    const fabricUsed = production.reduce((sum, row) => sum + Number(row.payload.fabricUsedMeters || 0), 0)
    const revenue = byType('economic_work').reduce((sum, row) => sum + Number(row.payload.manualGrossRevenue || Number(row.payload.quantity || 0) * Number(row.payload.unitPrice || 0)), 0) + production.reduce((sum, row) => sum + Number(row.payload.grossRevenue || 0), 0)
    const periodLabel = `${formatItalianDate(range.start)} – ${formatItalianDate(range.end)}`
    host.innerHTML = `<section class="dashboard-heading"><div><p class="kicker">CENTRO DI CONTROLLO</p><h1>Buon lavoro, ${escapeHtml((access.display_name || 'Biagio').split(' ')[0])}.</h1><p>Indicatori calcolati esclusivamente nel periodo selezionato.</p></div><div class="period-controller"><label>Periodo<select id="dashboard-period"><option value="current_month" ${periodMode === 'current_month' ? 'selected' : ''}>Mese corrente</option><option value="previous_month" ${periodMode === 'previous_month' ? 'selected' : ''}>Mese precedente</option><option value="7" ${periodMode === '7' ? 'selected' : ''}>Ultimi 7 giorni</option><option value="30" ${periodMode === '30' ? 'selected' : ''}>Ultimi 30 giorni</option><option value="90" ${periodMode === '90' ? 'selected' : ''}>Ultimi 90 giorni</option><option value="custom" ${periodMode === 'custom' ? 'selected' : ''}>Intervallo personalizzato</option></select></label><label>Dal<input id="dashboard-from" type="date" value="${range.start}"></label><label>Al<input id="dashboard-to" type="date" value="${range.end}"></label><strong>${periodLabel}</strong></div></section>
      <section class="executive-metrics"><article><div><span>Ordini del periodo</span><strong>${orders.length}</strong></div><b class="trend ${overdueOrders ? 'negative' : 'positive'}">${activeOrders} operativi · ${overdueOrders} in ritardo</b><small>${periodLabel}</small></article><article class="meter-kpi paper"><div><span>Carta stampata</span><strong>${printedPaper.toLocaleString('it-IT', { maximumFractionDigits: 1 })} m</strong></div><b class="trend positive">${production.length} registrazioni</b><small>metri effettivi nel periodo</small></article><article class="meter-kpi fabric"><div><span>Tessuto lavorato</span><strong>${fabricUsed.toLocaleString('it-IT', { maximumFractionDigits: 1 })} m</strong></div><b class="trend neutral">${production.length ? (fabricUsed / production.length).toLocaleString('it-IT', { maximumFractionDigits: 1 }) : '0'} m medi</b><small>consumo rilevato nel periodo</small></article><article><div><span>Magazzino attuale</span><strong>${stockMeters.toLocaleString('it-IT', { maximumFractionDigits: 0 })} m</strong></div><b class="trend ${lowStock.length ? 'negative' : 'positive'}">${lowStock.length} sotto soglia</b><small>dato corrente, non storico</small></article><article><div><span>Attività del periodo</span><strong>${tasks.length}</strong></div><b class="trend ${overdueTasks ? 'negative' : 'positive'}">${openTasks} aperte · ${overdueTasks} scadute</b><small>${periodLabel}</small></article>${access.permissions.includes('finance.view') ? `<article><div><span>Fatturato</span><strong>${revenue.toLocaleString('it-IT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })}</strong></div><b class="trend neutral">Periodo selezionato</b><small>produzioni e altri ricavi</small></article>` : ''}</section>
      <section class="dashboard-grid"><article class="dashboard-card production-chart"><div class="card-heading"><div><p class="kicker">PRODUZIONE</p><h2>Carta stampata · ${periodLabel}</h2></div><button data-open-module="production">Apri reparto →</button></div><div class="large-bars ${chartDays.length > 14 ? 'dense' : ''}">${productionTotals.map((value, index) => `<div title="${formatItalianDate(chartDays[index])}: ${Math.round(value)} m"><b>${value ? Math.round(value) : ''}</b><i style="height:${Math.max(4, value / maximumProduction * 100)}%"></i><span>${new Date(`${chartDays[index]}T12:00:00`).toLocaleDateString('it-IT', { day:'2-digit', month:'2-digit' })}</span></div>`).join('')}</div></article><article class="dashboard-card attention-card"><div class="card-heading"><div><p class="kicker">ATTENZIONE RICHIESTA</p><h2>Priorità nel periodo</h2></div><span>${overdueOrders + overdueTasks + lowStock.length + openIssues.length}</span></div><div class="attention-list"><button data-open-module="orders"><i class="danger"></i><div><strong>Ordini in ritardo</strong><small>Nel periodo selezionato</small></div><b>${overdueOrders}</b></button><button data-open-module="tasks"><i class="warn"></i><div><strong>Attività scadute</strong><small>Nel periodo selezionato</small></div><b>${overdueTasks}</b></button><button data-open-module="inventory"><i class="danger"></i><div><strong>Tessuti sotto soglia</strong><small>Situazione attuale</small></div><b>${lowStock.length}</b></button><button data-open-module="issues"><i class="info"></i><div><strong>Criticità aperte</strong><small>Nel periodo selezionato</small></div><b>${openIssues.length}</b></button></div></article></section>
      <section class="dashboard-lower"><article><div class="section-head"><div><p class="kicker">AREE DI LAVORO</p><h2>Moduli autorizzati</h2></div><span>${allowed.length} disponibili</span></div><div class="compact-module-grid">${allowed.map(module => `<button data-open-module="${module.key}"><i>${moduleIcons[module.key] || '•'}</i><span><strong>${module.title}</strong><small>${module.description}</small></span><b>→</b></button>`).join('')}</div></article><article><div class="section-head"><div><p class="kicker">MOVIMENTI NEL PERIODO</p><h2>Attività recenti</h2></div></div><div class="recent-list">${recent.length ? recent.map(row => `<div class="recent-row"><span class="dot ${row.module}"></span><div><strong>${escapeHtml(String(row.payload.title || row.payload.name || row.payload.orderCode || translateCode(row.entity_type)))}</strong><small>${escapeHtml(moduleLabel(row.module))} · ${formatItalianDate(row.updated_at, true)}</small></div></div>`).join('') : '<p class="empty">Nessun movimento nel periodo selezionato.</p>'}</div></article></section>`
    host.querySelector<HTMLSelectElement>('#dashboard-period')?.addEventListener('change', event => { periodMode = (event.currentTarget as HTMLSelectElement).value; if (periodMode !== 'custom') range = periodDates(periodMode); drawDashboard() })
    const setCustomRange = () => { periodMode = 'custom'; range = { start: host.querySelector<HTMLInputElement>('#dashboard-from')!.value, end: host.querySelector<HTMLInputElement>('#dashboard-to')!.value }; if (range.start && range.end && range.start <= range.end) drawDashboard() }
    host.querySelector('#dashboard-from')?.addEventListener('change', setCustomRange); host.querySelector('#dashboard-to')?.addEventListener('change', setCustomRange)
    host.querySelectorAll<HTMLElement>('[data-open-module]').forEach(element => element.addEventListener('click', () => { const module = modules.find(item => item.key === element.dataset.openModule); if (module?.key === 'users') void renderAccessManagement(user, access); else if (module?.key === 'sync') void renderCloudStatus(user, access); else if (module) void renderModule(user, access, module) }))
  }
  drawDashboard()
  restoreRouteScroll()
}

async function renderGlobalSearch(user: User, access: AccessContext, query: string) {
  if (!supabase || !access.organization_id) return
  const host = mountShell(user, access, 'dashboard', '<div class="loader-shell"><div class="loader"></div></div>')
  const { data, error } = await supabase.rpc('list_erp_records_safe', { p_organization_id: access.organization_id, p_module: null, p_limit: 500 })
  const term = query.toLocaleLowerCase('it-IT')
  const results = ((data || []) as CloudRecord[]).filter(row => JSON.stringify(row.payload).toLocaleLowerCase('it-IT').includes(term)).slice(0, 100)
  host.innerHTML = `<section class="search-results-page"><button id="search-back" class="back">← Dashboard</button><p class="kicker">RICERCA GLOBALE</p><h1>Risultati per “${escapeHtml(query)}”</h1><p>${results.length} elementi trovati nei moduli autorizzati.</p>${error ? `<p class="form-message">${escapeHtml(error.message)}</p>` : ''}<div class="global-results">${results.map(row => `<button data-result-module="${escapeHtml(row.module)}"><i>${moduleIcons[row.module] || '•'}</i><div><strong>${escapeHtml(String(row.payload.title || row.payload.name || row.payload.orderCode || row.payload.clientName || translateCode(row.entity_type)))}</strong><span>${escapeHtml(moduleLabel(row.module))} · ${escapeHtml(translateCode(row.entity_type))}</span></div><small>${formatItalianDate(row.updated_at, true)}</small><b>→</b></button>`).join('') || '<div class="empty-state"><h2>Nessun risultato</h2><p>Prova con cliente, codice ordine, tessuto o attività.</p></div>'}</div></section>`
  host.querySelector('#search-back')?.addEventListener('click', () => renderWorkspace(user, access))
  host.querySelectorAll<HTMLElement>('[data-result-module]').forEach(element => element.addEventListener('click', () => {
    const module = modules.find(item => item.key === element.dataset.resultModule)
    if (module) void renderModule(user, access, module)
  }))
}

async function renderCloudStatus(user: User, access: AccessContext) {
  if (!supabase || !access.organization_id) return
  const host = mountShell(user, access, 'sync', '<div class="loader-shell"><div class="loader"></div></div>')
  const { data, error } = await supabase.rpc('get_workspace_sync_state', { p_organization_id: access.organization_id })
  const workspace = data as { exists?: boolean; revision?: number; record_count?: number; size_bytes?: number; updated_at?: string; updated_by?: string } | null
  const size = workspace?.size_bytes || 0
  const sizeLabel = size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${(size / 1024).toFixed(1)} KB`
  host.innerHTML = `<div class="analytical-page cloud-page">
    <header class="module-header compact-header"><div><button id="home" class="back">← Dashboard</button><p class="kicker">SICUREZZA DATI</p><h1>Workspace cloud</h1><p>Stato della copia Android completa e del mirror consultabile dal portale.</p></div><div class="live"><i></i>${workspace?.exists ? `Rev. ${workspace.revision}` : 'In attesa'}</div></header>
    ${error ? `<p class="form-message">${escapeHtml(error.message)}</p>` : ''}
    <section class="hero"><div><span class="pill">SHA-256 + REVISIONI</span><h2>${workspace?.exists ? `Revisione ${workspace.revision} protetta.` : 'Nessun workspace pubblicato.'}</h2><p>${workspace?.exists ? `${workspace.record_count || 0} record · ${sizeLabel} · pubblicata da ${escapeHtml(workspace.updated_by || 'utente Spectro')}` : 'Apri l’app Android con un account autorizzato per creare la prima revisione.'}</p>${workspace?.updated_at ? `<p class="security-note">Ultimo aggiornamento: ${new Date(workspace.updated_at).toLocaleString('it-IT')}</p>` : ''}</div><div class="hero-stat"><strong>${workspace?.record_count || 0}</strong><span>record verificati</span></div></section>
    <section class="empty-state"><h2>Protezione conflitti attiva</h2><p>Una postazione può pubblicare soltanto se parte dalla stessa revisione presente nel cloud. In caso contrario l’operazione viene fermata senza sovrascritture.</p><button id="refresh-sync" class="primary">Aggiorna stato</button></section>
  </div>`
  host.querySelector('#home')?.addEventListener('click', () => renderWorkspace(user, access))
  host.querySelector('#refresh-sync')?.addEventListener('click', () => renderCloudStatus(user, access))
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
async function renderAccessManagement(user: User, access: AccessContext, message = '') {
  if (!supabase || !access.organization_id) return
  const host = mountShell(user, access, 'users', '<div class="loader-shell"><div class="loader"></div></div>')
  const [membersResult, rolesResult, permissionsResult, rolePermissionsResult] = await Promise.all([
    supabase.rpc('admin_list_members', { p_organization_id: access.organization_id }),
    supabase.from('roles').select('id,name,description').eq('organization_id', access.organization_id).order('name'),
    supabase.from('permissions').select('key,module,label,description').order('module').order('key'),
    supabase.from('role_permissions').select('role_id,permission_key')
  ])
  const error = membersResult.error || rolesResult.error || permissionsResult.error || rolePermissionsResult.error
  const members = (membersResult.data || []) as MemberRow[]
  const roles = (rolesResult.data || []) as RoleRow[]
  const permissions = (permissionsResult.data || []) as PermissionRow[]
  const rolePermissionMap = new Map<string, Set<string>>()
  ;((rolePermissionsResult.data || []) as Array<{ role_id: string; permission_key: string }>).forEach(row => {
    const values = rolePermissionMap.get(row.role_id) || new Set<string>()
    values.add(row.permission_key)
    rolePermissionMap.set(row.role_id, values)
  })
  const permissionGroups = permissions.reduce<Record<string, PermissionRow[]>>((groups, permission) => {
    ;(groups[permission.module] ||= []).push(permission)
    return groups
  }, {})
  const roleOptions = (selected: string) => roles.map(role => `<option value="${role.id}" ${role.id === selected ? 'selected' : ''}>${escapeHtml(role.name)}</option>`).join('')

  host.innerHTML = `<div class="analytical-page access-page">
    <header class="module-header compact-header"><div><button id="home" class="back">← Dashboard</button><p class="kicker">AMMINISTRAZIONE</p><h1>Utenti e accessi</h1><p>Ruoli e personalizzazioni vengono preparati in bozza e salvati insieme.</p></div><div class="live"><i></i>${members.length} utenti</div></header>
    ${message || error ? `<p class="form-message">${escapeHtml(message || error?.message)}</p>` : ''}
    <section class="access-overview"><article><span>Utenti attivi</span><strong>${members.filter(member => member.status === 'active').length}</strong><small>su ${members.length} associati</small></article><article><span>Ruoli disponibili</span><strong>${roles.length}</strong><small>preset aziendali</small></article><article><span>Permessi gestiti</span><strong>${permissions.length}</strong><small>raggruppati per modulo</small></article><article><span>Modalità</span><strong>Bozza</strong><small>un solo salvataggio finale</small></article></section>
    <section class="role-preview-panel"><div><p class="kicker">ANTEPRIMA GLOBALE</p><h2>Visualizza Spectro come un altro ruolo</h2><p>Funziona anche se Maria, Enzo o un altro utente non hanno ancora completato la registrazione.</p></div><div class="role-preview-grid">${roles.map(role => `<button data-preview-role="${role.id}"><span>${moduleIcons.users}</span><div><strong>${escapeHtml(role.name)}</strong><small>${escapeHtml(role.description || 'Ruolo Spectro')}</small></div><b>Apri anteprima →</b></button>`).join('')}</div></section>
    <section class="invite-panel"><div><p class="kicker">AGGIUNGI PERSONA</p><h2>Associa un account registrato</h2><p>L’utente deve essersi già registrato e aver confermato l’email.</p></div>
      <form id="add-member"><input name="email" type="email" required placeholder="nome@azienda.it"><select name="role" required>${roleOptions(roles[0]?.id || '')}</select><button class="primary" type="submit">Assegna accesso</button></form>
    </section>
    <div class="permission-help"><div><strong>Matrice permessi</strong><span>Il ruolo definisce la base; le eccezioni valgono soltanto per il singolo utente.</span></div><label class="permission-search">⌕<input id="permission-search" type="search" placeholder="Cerca utente o permesso…"></label></div>
    <section class="member-list">${members.map(member => `<article class="member-card" data-user="${member.user_id}">
      <div class="member-summary"><span class="avatar">${escapeHtml((member.display_name || member.email).slice(0, 1).toUpperCase())}</span><div><h3>${escapeHtml(member.display_name || member.email)}</h3><p>${escapeHtml(member.email)} · ${escapeHtml(translateCode(member.status))}</p></div><button class="preview-user" type="button">Anteprima utente</button><label>Ruolo<select class="role-select" data-original="${member.role_id}">${roleOptions(member.role_id)}</select></label><span class="effective-count">${rolePermissionMap.get(member.role_id)?.size || 0} permessi base</span></div>
      <details><summary><span>Configura accesso</span><b>${Object.keys(member.permission_overrides || {}).length} personalizzazioni</b></summary>
        <div class="permission-tools"><button data-bulk="default">Ripristina ruolo</button><button data-bulk="view">Solo visualizzazione</button><button data-bulk="allow">Consenti tutto</button><button data-bulk="deny">Nega tutto</button></div>
        <div class="permission-matrix">${Object.entries(permissionGroups).map(([moduleName, modulePermissions]) => `<section><header><div><i>${moduleIcons[moduleName] || '•'}</i><strong>${escapeHtml(moduleLabel(moduleName))}</strong></div><span>${modulePermissions.length} funzioni</span></header>${modulePermissions.map(permission => {
          const override = member.permission_overrides?.[permission.key]
          const selected = override === undefined ? 'default' : override ? 'allow' : 'deny'
          const baseAllowed = rolePermissionMap.get(member.role_id)?.has(permission.key) || false
          return `<label class="permission-row" data-search="${escapeHtml(`${member.display_name} ${member.email} ${permission.label} ${permission.description}`.toLowerCase())}"><div><strong>${escapeHtml(permission.label)}</strong><small>${escapeHtml(permission.description)}</small><em>Ruolo: ${baseAllowed ? 'consentito' : 'non consentito'}</em></div><select class="override-select" data-permission="${permission.key}" data-original="${selected}"><option value="default" ${selected === 'default' ? 'selected' : ''}>Dal ruolo</option><option value="allow" ${selected === 'allow' ? 'selected' : ''}>Consenti</option><option value="deny" ${selected === 'deny' ? 'selected' : ''}>Nega</option></select></label>`
        }).join('')}</section>`).join('')}</div>
        <footer class="permission-savebar"><div><strong>Nessuna modifica da salvare</strong><span>Le selezioni restano in bozza finché non confermi.</span></div><button class="secondary reset-member" disabled>Annulla</button><button class="primary save-member" disabled>Salva permessi</button></footer>
      </details>
    </article>`).join('') || '<p class="empty">Nessun utente associato.</p>'}</section>
  </div>`
  host.querySelector('#home')?.addEventListener('click', () => renderWorkspace(user, access))
  host.querySelectorAll<HTMLButtonElement>('[data-preview-role]').forEach(button => button.addEventListener('click', () => {
    const role = roles.find(item => item.id === button.dataset.previewRole)
    if (!role) return
    adminPreview = { user, access }
    void renderWorkspace(user, { ...access, display_name: role.name, role_name: role.name, permissions: [...(rolePermissionMap.get(role.id) || [])], preview_name: role.name, preview_role: role.name })
  }))
  host.querySelector<HTMLFormElement>('#add-member')?.addEventListener('submit', async event => {
    event.preventDefault()
    const form = new FormData(event.currentTarget as HTMLFormElement)
    const { error: assignError } = await supabase.rpc('admin_assign_member_by_email', {
      p_organization_id: access.organization_id,
      p_email: String(form.get('email')),
      p_role_id: String(form.get('role'))
    })
    await renderAccessManagement(user, access, assignError?.message || 'Accesso assegnato correttamente.')
  })
  const markDirty = (card: HTMLElement) => {
    const role = card.querySelector<HTMLSelectElement>('.role-select')!
    const changed = role.value !== role.dataset.original || [...card.querySelectorAll<HTMLSelectElement>('.override-select')].some(select => select.value !== select.dataset.original)
    card.classList.toggle('dirty', changed)
    card.querySelector<HTMLButtonElement>('.save-member')!.disabled = !changed
    card.querySelector<HTMLButtonElement>('.reset-member')!.disabled = !changed
    card.querySelector<HTMLElement>('.permission-savebar strong')!.textContent = changed ? 'Modifiche pronte da salvare' : 'Nessuna modifica da salvare'
  }
  host.querySelectorAll<HTMLElement>('.member-card').forEach(card => {
    const userId = card.dataset.user!
    const member = members.find(item => item.user_id === userId)
    card.querySelector<HTMLButtonElement>('.preview-user')?.addEventListener('click', () => {
      if (!member) return
      const effective = new Set(rolePermissionMap.get(member.role_id) || [])
      Object.entries(member.permission_overrides || {}).forEach(([key, allowed]) => allowed ? effective.add(key) : effective.delete(key))
      adminPreview = { user, access }
      void renderWorkspace(user, { ...access, display_name: member.display_name || member.email, role_name: member.role_name, permissions: [...effective], preview_name: member.display_name || member.email, preview_role: member.role_name })
    })
    card.querySelector<HTMLSelectElement>('.role-select')?.addEventListener('change', () => markDirty(card))
    card.querySelectorAll<HTMLSelectElement>('.override-select').forEach(select => select.addEventListener('change', () => markDirty(card)))
    card.querySelectorAll<HTMLButtonElement>('[data-bulk]').forEach(button => button.addEventListener('click', () => {
      const mode = button.dataset.bulk || 'default'
      card.querySelectorAll<HTMLSelectElement>('.override-select').forEach(select => { select.value = mode === 'view' ? (select.dataset.permission?.endsWith('.view') || select.dataset.permission === 'reports.export' ? 'allow' : 'deny') : mode })
      markDirty(card)
    }))
    card.querySelector<HTMLButtonElement>('.reset-member')?.addEventListener('click', () => {
      const role = card.querySelector<HTMLSelectElement>('.role-select')!; role.value = role.dataset.original || ''
      card.querySelectorAll<HTMLSelectElement>('.override-select').forEach(select => { select.value = select.dataset.original || 'default' })
      markDirty(card)
    })
    card.querySelector<HTMLButtonElement>('.save-member')?.addEventListener('click', async event => {
      const saveButton = event.currentTarget as HTMLButtonElement
      saveButton.disabled = true; saveButton.textContent = 'Salvataggio…'
      const role = card.querySelector<HTMLSelectElement>('.role-select')!
      let saveError: { message: string } | null = null
      if (role.value !== role.dataset.original) saveError = (await supabase.rpc('assign_member_role', { p_organization_id: access.organization_id, p_user_id: userId, p_role_id: role.value })).error
      for (const select of card.querySelectorAll<HTMLSelectElement>('.override-select')) {
        if (saveError || select.value === select.dataset.original) continue
        const params = { p_organization_id: access.organization_id, p_user_id: userId, p_permission_key: select.dataset.permission! }
        saveError = (select.value === 'default' ? await supabase.rpc('admin_clear_permission_override', params) : await supabase.rpc('admin_set_permission_override', { ...params, p_allowed: select.value === 'allow' })).error
      }
      await renderAccessManagement(user, access, saveError?.message || `Accesso di ${card.querySelector('h3')?.textContent || 'utente'} aggiornato.`)
    })
  })
  host.querySelector<HTMLInputElement>('#permission-search')?.addEventListener('input', event => {
    const term = (event.currentTarget as HTMLInputElement).value.trim().toLowerCase()
    host.querySelectorAll<HTMLElement>('.permission-row').forEach(row => { row.hidden = Boolean(term && !row.dataset.search?.includes(term)) })
  })
}

async function renderModule(user: User, access: AccessContext, module: Module) {
  if (!supabase) return
  const host = mountShell(user, access, module.key, '<div class="loader-shell"><div class="loader"></div></div>')
  const relatedModules: Record<string, string[]> = {
    orders: ['orders', 'production', 'issues'], clients: ['clients', 'orders', 'production', 'tasks'],
    inventory: ['inventory'], zentha: ['zentha'], production: ['production'], cotton: ['cotton'],
    finance: ['finance'], tasks: ['tasks'], issues: ['issues']
  }
  const permitted = (relatedModules[module.key] || [module.key]).filter(key => {
    const definition = modules.find(item => item.key === key)
    return definition ? access.permissions.includes(definition.permission) : true
  })
  const results = await Promise.all(permitted.map(key => supabase.rpc('list_erp_records_safe', {
    p_organization_id: access.organization_id, p_module: key, p_limit: 500
  })))
  const records = recordsForAccess(results.flatMap(result => (result.data || []) as CloudRecord[]), access)
  await hydrateAttachmentUrls(records)
  updateNotificationCenter(records)
  const error = results.find(result => result.error)?.error
  renderAnalyticalModule(
    host,
    module,
    records,
    access.permissions.includes('finance.view'),
    () => { void renderWorkspace(user, access) },
    error?.message || '',
    access.permissions.includes(`${module.key}.manage`),
    access.permissions.includes('reports.export'),
    async (row, patch) => {
      if (!access.organization_id) return 'Organizzazione non disponibile.'
      const { error: updateError } = await supabase.rpc('web_update_record', {
        p_organization_id: access.organization_id,
        p_record_id: row.id,
        p_patch: patch
      })
      if (updateError) {
        if (updateError.message.toLowerCase().includes('web_update_record')) return 'Aggiornamento cloud 005 non ancora installato su Supabase. Esegui la migrazione SQL fornita con questa versione.'
        return updateError.message
      }
      await renderModule(user, access, module)
      return null
    }
  )
  restoreRouteScroll()
}

async function renderForUser(user: User) {
  activeUserId = user.id
  app.innerHTML = '<main class="center"><div class="loader"></div></main>'
  try {
    const access = await loadAccess(user)
    if (!access?.organization_id) renderPending(user, access)
    else {
      const lastRoute = localStorage.getItem(`spectro-last-route:${user.id}`) || 'dashboard'
      const module = modules.find(item => item.key === lastRoute && access.permissions.includes(item.permission))
      if (lastRoute === 'users' && module) await renderAccessManagement(user, access)
      else if (lastRoute === 'sync' && module) await renderCloudStatus(user, access)
      else if (module) await renderModule(user, access, module)
      else await renderWorkspace(user, access)
    }
  } catch (error) {
    renderPending(user, null, error instanceof Error ? error.message : 'Impossibile verificare i permessi.')
  }
}

async function start() {
  installInteractiveMotion()
  if (!supabase) return renderSetup()
  const { data } = await supabase.auth.getSession()
  if (data.session?.user) { activeUserId = data.session.user.id; await renderForUser(data.session.user) }
  else renderLogin()
  supabase.auth.onAuthStateChange((event, session) => {
    if (event === 'SIGNED_OUT') { activeUserId = null; renderLogin() }
    if (event === 'SIGNED_IN' && session?.user && session.user.id !== activeUserId) { activeUserId = session.user.id; void renderForUser(session.user) }
  })
  if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'))
}

void start()
