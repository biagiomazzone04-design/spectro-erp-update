import { formatItalianDate, isoDate, translateCode } from './i18n'

export type CloudRecord = {
  id: string
  module: string
  entity_type: string
  payload: Record<string, unknown>
  updated_at: string
  revision: number
}

export type ModuleView = { key: string; eyebrow: string; title: string; description: string }

const esc = (value: unknown) => String(value ?? '')
  .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;').replaceAll("'", '&#039;')
const num = (value: unknown, digits = 1) => new Intl.NumberFormat('it-IT', { maximumFractionDigits: digits }).format(Number(value || 0))
const euro = (value: unknown) => new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' }).format(Number(value || 0))
const date = (value: unknown) => formatItalianDate(value)
const text = (value: unknown, fallback = '—') => String(value || '').trim() || fallback
const label = (value: unknown) => translateCode(value)
const statusClass = (value: unknown) => {
  const status = String(value || '').toLowerCase()
  if (/(done|closed|delivered|completed|paid|ready|resolved)/.test(status)) return 'ok'
  if (/(blocked|cancelled|critical|negative|depleted)/.test(status)) return 'danger'
  if (/(waiting|hold|progress|production|open)/.test(status)) return 'warn'
  return 'neutral'
}
const badge = (value: unknown) => `<span class="status ${statusClass(value)}">${esc(label(value))}</span>`
const p = (row: CloudRecord) => row.payload
const has = (row: CloudRecord, key: string) => Object.prototype.hasOwnProperty.call(row.payload, key)
const terminalStatus = /(CLOSED|DELIVERED|COMPLETED|DONE|CANCELLED|PAID|RESOLVED|ARCHIVED)/

const moduleDate = (row: CloudRecord) => isoDate(
  p(row).date || p(row).dueDate || p(row).orderDate || p(row).receivedDate || p(row).createdDate || p(row).openedDate || row.updated_at
)

const isOverdue = (row: CloudRecord) => {
  const due = isoDate(p(row).dueDate || p(row).expectedDeliveryDate || p(row).revisedDeliveryDate)
  return Boolean(due && due < new Date().toISOString().slice(0, 10) && !terminalStatus.test(String(p(row).status || '')))
}

const primaryTypes: Record<string, string[]> = {
  orders: ['work_order'], production: ['production_record'], inventory: ['inventory_item'], clients: ['client'],
  cotton: ['cotton_order'], zentha: ['zentha_order'], finance: ['payment_deadline', 'economic_work'],
  tasks: ['operational_task'], issues: ['client_issue', 'order_timeline_event']
}

const columns: Record<string, string[]> = {
  orders: ['Ordine', 'Cliente', 'Consegna', 'Metri', 'Stato'],
  production: ['Data', 'Cliente / ordine', 'Disegni', 'Carta / tessuto', 'Plotter'],
  inventory: ['Tessuto', 'Proprietario', 'Disponibilità', 'Lotto', 'Stato scorta'],
  clients: ['Cliente', 'Ordini', 'Produzioni', 'Attività', 'Ultimo aggiornamento'],
  cotton: ['Ordine', 'Cliente', 'Tessuto', 'Avanzamento', 'Stato'],
  zentha: ['Ordine', 'Cliente', 'Consegna', 'Articoli / pezzi', 'Stato'],
  finance: ['Voce', 'Cliente', 'Scadenza / data', 'Importo', 'Stato'],
  tasks: ['Attività', 'Cliente', 'Scadenza', 'Priorità', 'Stato'],
  issues: ['Criticità / evento', 'Cliente', 'Ordine', 'Data', 'Stato']
}

function titleOf(row: CloudRecord) {
  const x = p(row)
  return text(x.orderCode || x.title || x.name || x.fabricName || x.clientName || x.productName || x.orderRef, label(row.entity_type))
}

function related(records: CloudRecord[], entity: string, key: string, value: unknown) {
  return records.filter(row => row.entity_type === entity && String(p(row)[key] ?? '') === String(value ?? ''))
}

function cells(module: string, row: CloudRecord, records: CloudRecord[]) {
  const x = p(row)
  switch (module) {
    case 'orders': return [
      `<strong>${esc(text(x.orderCode, `#${x.id}`))}</strong><small>${esc(text(x.title))}</small>`,
      `<strong>${esc(text(x.client))}</strong><small>${esc(text(x.designCode, 'Nessun codice disegno'))}</small>`,
      `<strong>${date(x.dueDate)}</strong><small>Ricevuto ${date(x.receivedDate)}</small>`,
      `<strong>${num(x.orderedMeters)} m</strong><small>${related(records, 'production_record', 'workOrderId', x.id).length} produzioni</small>`, badge(x.status)
    ]
    case 'production': return [
      `<strong>${date(x.date)}</strong><small>${esc(label(x.type))}</small>`,
      `<strong>${esc(text(x.clientName))}</strong><small>${esc(text(x.orderRef, 'Senza riferimento'))}</small>`,
      `<strong>${esc(Array.isArray(x.designs) ? x.designs.join(', ') || '—' : text(x.designs))}</strong><small>${esc(text(x.technicalNotes, 'Nessuna nota'))}</small>`,
      `<strong>${num(x.printedPaperMeters)} m</strong><small>Tessuto ${num(x.fabricUsedMeters)} m</small>`,
      `<strong>${esc(text(x.plotter))}</strong><small>${esc(text(x.fabricDisplayName, 'Nessun tessuto'))}</small>`
    ]
    case 'inventory': {
      const low = Number(x.quantityMeters || 0) <= Number(x.minimumQuantityMeters || 0)
      return [`<strong>${esc(text(x.fabricName))}</strong><small>${esc(text(x.variant, x.composition as string))}</small>`,
        `<strong>${esc(text(x.ownerName))}</strong><small>${esc(label(x.ownerType))}</small>`,
        `<strong>${num(x.quantityMeters)} m</strong><small>Soglia ${num(x.minimumQuantityMeters)} m</small>`,
        `<strong>${esc(text(x.lotCode))}</strong><small>${related(records, 'fabric_roll', 'inventoryId', x.id).length} colli</small>`,
        low ? '<span class="status danger">Sotto soglia</span>' : '<span class="status ok">Disponibile</span>']
    }
    case 'clients': {
      const id = x.id
      return [`<strong>${esc(text(x.name))}</strong><small>Cliente #${esc(id)}</small>`,
        `<strong>${related(records, 'work_order', 'clientId', id).length}</strong><small>commesse</small>`,
        `<strong>${related(records, 'production_record', 'clientId', id).length}</strong><small>registrazioni</small>`,
        `<strong>${related(records, 'operational_task', 'clientId', id).length}</strong><small>attività</small>`,
        `<strong>${new Date(row.updated_at).toLocaleString('it-IT')}</strong>`]
    }
    case 'cotton': {
      const ordered = Number(x.orderedMeters || 0); const produced = Number(x.actualProducedMeters || 0)
      const progress = ordered > 0 ? Math.min(100, produced / ordered * 100) : 0
      return [`<strong>${esc(text(x.orderCode, `#${x.id}`))}</strong><small>${esc(text(x.title))}</small>`,
        `<strong>${esc(text(x.clientName))}</strong><small>${date(x.dueDate)}</small>`,
        `<strong>${esc(text(x.fabricName))}</strong><small>${esc(label(x.printType))}</small>`,
        `<strong>${num(produced)} / ${num(ordered)} m</strong><div class="mini-progress"><i style="width:${progress}%"></i></div>`, badge(x.status)]
    }
    case 'zentha': {
      const items = related(records, 'zentha_order_item', 'orderId', x.id)
      return [`<strong>${esc(text(x.orderCode, `#${x.id}`))}</strong><small>${date(x.orderDate)}</small>`,
        `<strong>${esc(text(x.clientName))}</strong><small>${esc(text(x.notes, 'Nessuna nota'))}</small>`,
        `<strong>${date(x.dueDate)}</strong>`, `<strong>${items.length} righe · ${items.reduce((s, i) => s + Number(p(i).quantity || 0), 0)} pz</strong>`, badge(x.status)]
    }
    case 'finance': return row.entity_type === 'payment_deadline'
      ? [`<strong>Scadenza ${esc(text(x.orderRef))}</strong><small>${esc(label(row.entity_type))}</small>`, `<strong>${esc(text(x.clientName))}</strong>`, `<strong>${date(x.dueDate)}</strong>`, `<strong>${has(row, 'amount') ? euro(x.amount) : 'Riservato'}</strong>`, badge(x.paid ? 'PAID' : 'OPEN')]
      : [`<strong>${esc(text(x.title))}</strong><small>Lavoro economico</small>`, `<strong>${esc(text(x.clientName))}</strong>`, `<strong>${date(x.date)}</strong>`, `<strong>${has(row, 'unitPrice') ? euro(Number(x.manualGrossRevenue || 0) || Number(x.quantity || 0) * Number(x.unitPrice || 0)) : 'Riservato'}</strong>`, badge(x.status)]
    case 'tasks': return [`<strong>${esc(text(x.title))}</strong><small>${esc(label(x.type))}</small>`, `<strong>${esc(text(x.clientName))}</strong>`, `<strong>${date(x.dueDate)}</strong>`, badge(x.priority), badge(x.status)]
    case 'issues': return [`<strong>${esc(text(x.title))}</strong><small>${esc(label(row.entity_type))}</small>`, `<strong>${esc(text(x.clientName))}</strong>`, `<strong>${esc(text(x.orderRef))}</strong>`, `<strong>${date(x.openedDate || x.date)}</strong>`, badge(x.status)]
    default: return [`<strong>${esc(titleOf(row))}</strong>`]
  }
}

function statistics(module: string, primary: CloudRecord[], records: CloudRecord[], canFinance: boolean) {
  const sum = (key: string, rows = primary) => rows.reduce((total, row) => total + Number(p(row)[key] || 0), 0)
  const open = primary.filter(row => !/(CLOSED|DELIVERED|COMPLETED|DONE|CANCELLED|PAID|RESOLVED)/.test(String(p(row).status || (p(row).paid ? 'PAID' : '')))).length
  const configs: Record<string, Array<[string, string, string]>> = {
    orders: [['Totale ordini', String(primary.length), 'Archivio sincronizzato'], ['In lavorazione', String(open), 'Ordini operativi'], ['Metri ordinati', `${num(sum('orderedMeters'))} m`, 'Volume complessivo'], ['Produzioni collegate', String(records.filter(r => r.entity_type === 'production_record' && p(r).workOrderId).length), 'Tracciabilità']],
    production: [['Registrazioni', String(primary.length), 'Sessioni produttive'], ['Carta stampata', `${num(sum('printedPaperMeters'))} m`, 'Totale lavorato'], ['Tessuto usato', `${num(sum('fabricUsedMeters'))} m`, 'Consumo rilevato'], ['Clienti', String(new Set(primary.map(r => p(r).clientName).filter(Boolean)).size), 'Clienti lavorati']],
    inventory: [['Tessuti', String(primary.length), 'Articoli censiti'], ['Disponibilità', `${num(sum('quantityMeters'))} m`, 'Giacenza totale'], ['Sotto soglia', String(primary.filter(r => Number(p(r).quantityMeters || 0) <= Number(p(r).minimumQuantityMeters || 0)).length), 'Da riordinare'], ['Colli', String(records.filter(r => r.entity_type === 'fabric_roll').length), 'Unità tracciate']],
    clients: [['Clienti', String(primary.length), 'Anagrafiche'], ['Ordini', String(records.filter(r => r.entity_type === 'work_order').length), 'Commesse collegate'], ['Produzioni', String(records.filter(r => r.entity_type === 'production_record').length), 'Storico lavorazioni'], ['Attività', String(records.filter(r => r.entity_type === 'operational_task').length), 'Promemoria']],
    cotton: [['Ordini cotone', String(primary.length), 'Totale'], ['Aperti', String(open), 'In gestione'], ['Metri ordinati', `${num(sum('orderedMeters'))} m`, 'Volume'], ['Metri prodotti', `${num(sum('actualProducedMeters'))} m`, 'Avanzamento']],
    zentha: [['Ordini Zentha', String(primary.length), 'Commesse sportive'], ['Aperti', String(open), 'In gestione'], ['Articoli', String(records.filter(r => r.entity_type === 'zentha_product').length), 'Catalogo'], ['Pezzi ordinati', String(sum('quantity', records.filter(r => r.entity_type === 'zentha_order_item'))), 'Totale quantità']],
    finance: [['Movimenti', String(primary.length), 'Voci economiche'], ['Aperti', String(open), 'Da gestire'], ['Scadenze', String(primary.filter(r => r.entity_type === 'payment_deadline').length), 'Pagamenti'], ['Valore', canFinance ? euro(sum('amount') + sum('manualGrossRevenue')) : 'Riservato', 'Accesso controllato']],
    tasks: [['Attività', String(primary.length), 'Totale'], ['Da completare', String(open), 'Operative'], ['Alta priorità', String(primary.filter(r => p(r).priority === 'HIGH').length), 'Attenzione'], ['Completate', String(primary.filter(r => p(r).status === 'DONE').length), 'Archiviate']],
    issues: [['Eventi', String(primary.length), 'Timeline e criticità'], ['Aperti', String(open), 'Da seguire'], ['Critici', String(primary.filter(r => /CRITICAL|BLOCKING/.test(String(p(r).severity))).length), 'Priorità'], ['Risolti', String(primary.filter(r => /RESOLVED|CLOSED/.test(String(p(r).status))).length), 'Completati']]
  }
  return (configs[module] || [['Record', String(primary.length), 'Totale']]).map(([k, v, s]) => `<article><span>${esc(k)}</span><strong>${esc(v)}</strong><small>${esc(s)}</small></article>`).join('')
}

function moduleInsights(primary: CloudRecord[]) {
  const today = new Date()
  const days = Array.from({ length: 14 }, (_, index) => {
    const current = new Date(today)
    current.setDate(today.getDate() - (13 - index))
    return current.toISOString().slice(0, 10)
  })
  const counts = days.map(day => primary.filter(row => moduleDate(row) === day).length)
  const maximum = Math.max(...counts, 1)
  const bars = counts.map((count, index) => `<i title="${formatItalianDate(days[index])}: ${count}" style="height:${Math.max(7, count / maximum * 100)}%"><b>${count || ''}</b></i>`).join('')

  const groups = new Map<string, number>()
  primary.forEach(row => {
    const status = String(p(row).status || (p(row).paid === true ? 'PAID' : p(row).paid === false ? 'OPEN' : 'SENZA_STATO'))
    groups.set(status, (groups.get(status) || 0) + 1)
  })
  const statusRows = [...groups.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
  const total = Math.max(primary.length, 1)
  return `<section class="module-insights">
    <article class="chart-card"><div class="chart-heading"><div><p class="kicker">ANDAMENTO</p><h3>Ultimi 14 giorni</h3></div><span>${counts.reduce((sum, value) => sum + value, 0)} movimenti</span></div><div class="bar-chart">${bars}</div><div class="chart-axis"><span>${formatItalianDate(days[0])}</span><span>Oggi</span></div></article>
    <article class="chart-card"><div class="chart-heading"><div><p class="kicker">DISTRIBUZIONE</p><h3>Stati operativi</h3></div><span>${primary.length} totali</span></div><div class="status-distribution">${statusRows.map(([status, count]) => `<div><span>${esc(translateCode(status))}</span><div><i class="${statusClass(status)}" style="width:${Math.max(3, count / total * 100)}%"></i></div><strong>${count}</strong></div>`).join('') || '<p class="muted">Nessuno stato disponibile.</p>'}</div></article>
  </section>`
}

function csvValue(value: unknown) {
  const normalized = Array.isArray(value) ? value.join(', ') : typeof value === 'object' && value ? JSON.stringify(value) : String(value ?? '')
  return `"${normalized.replaceAll('"', '""')}"`
}

function downloadCsv(module: ModuleView, rows: CloudRecord[]) {
  const headers = ['Tipo', 'Titolo', 'Stato', 'Cliente', 'Data', 'Ultimo aggiornamento']
  const lines = rows.map(row => {
    const value = p(row)
    return [translateCode(row.entity_type), titleOf(row), translateCode(value.status), value.clientName || value.client || value.ownerName, moduleDate(row), formatItalianDate(row.updated_at, true)].map(csvValue).join(';')
  })
  const blob = new Blob([`\uFEFF${headers.map(csvValue).join(';')}\n${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `Spectro_${module.title.replaceAll(' ', '_')}_${new Date().toISOString().slice(0, 10)}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}

function field(labelText: string, value: unknown, kind: 'text'|'date'|'number'|'money' = 'text') {
  if (value === undefined || value === null || value === '') return ''
  const formatted = kind === 'date' ? date(value) : kind === 'number' ? num(value) : kind === 'money' ? euro(value) : label(value)
  return `<div class="detail-field"><span>${esc(labelText)}</span><strong>${esc(formatted)}</strong></div>`
}

function detailContent(module: string, row: CloudRecord, records: CloudRecord[]) {
  const x = p(row)
  let fields = ''
  if (module === 'orders') fields = field('Codice ordine', x.orderCode) + field('Cliente', x.client) + field('Stato', x.status) + field('Priorità', x.priority) + field('Ricezione', x.receivedDate, 'date') + field('Consegna', x.dueDate, 'date') + field('Metri ordinati', x.orderedMeters, 'number') + field('Disegno', x.designCode) + (has(row, 'printPricePerMeter') ? field('Prezzo stampa / m', x.printPricePerMeter, 'money') : '')
  else if (module === 'production') fields = field('Data', x.date, 'date') + field('Cliente', x.clientName) + field('Ordine', x.orderRef) + field('Plotter', x.plotter) + field('Carta stampata', x.printedPaperMeters, 'number') + field('Carta protezione', x.protectionPaperMeters, 'number') + field('Tessuto usato', x.fabricUsedMeters, 'number') + field('Tessuto', x.fabricDisplayName) + (has(row, 'grossRevenue') ? field('Ricavo', x.grossRevenue, 'money') + field('Margine', x.grossProfit, 'money') : '')
  else if (module === 'inventory') fields = field('Tessuto', x.fabricName) + field('Proprietario', x.ownerName) + field('Disponibilità metri', x.quantityMeters, 'number') + field('Soglia minima', x.minimumQuantityMeters, 'number') + field('Fornitore', x.supplierName) + field('Composizione', x.composition) + field('Peso', x.weightGsm) + field('Variante', x.variant) + field('Lotto', x.lotCode)
  else if (module === 'zentha') fields = field('Ordine', x.orderCode) + field('Cliente', x.clientName) + field('Stato', x.status) + field('Data ordine', x.orderDate, 'date') + field('Consegna', x.dueDate, 'date')
  else fields = Object.entries(x).filter(([key, value]) => !key.startsWith('_') && !['id', 'attachmentCount', 'materials'].includes(key) && typeof value !== 'object').slice(0, 14).map(([key, value]) => field(label(key), value)).join('')

  let relatedHtml = ''
  if (module === 'orders') {
    const productions = related(records, 'production_record', 'workOrderId', x.id)
    const timeline = related(records, 'order_timeline_event', 'workOrderId', x.id)
    relatedHtml = `<section class="detail-section"><h3>Produzioni collegate <span>${productions.length}</span></h3>${productions.map(item => `<div class="linked-row"><b>${date(p(item).date)}</b><span>${esc(text(p(item).clientName))} · ${num(p(item).printedPaperMeters)} m carta · ${num(p(item).fabricUsedMeters)} m tessuto</span></div>`).join('') || '<p class="muted">Nessuna produzione collegata.</p>'}</section>
      <section class="detail-section"><h3>Timeline ordine <span>${timeline.length}</span></h3>${timeline.map(item => `<div class="linked-row"><b>${date(p(item).date)} · ${esc(text(p(item).title))}</b><span>${esc(text(p(item).description, 'Nessuna descrizione'))}</span></div>`).join('') || '<p class="muted">Nessun evento visibile per il tuo ruolo.</p>'}</section>`
  }
  if (module === 'zentha') {
    const items = related(records, 'zentha_order_item', 'orderId', x.id)
    relatedHtml = `<section class="detail-section"><h3>Articoli ordinati <span>${items.length}</span></h3><div class="detail-lines">${items.map(item => { const y = p(item); return `<div class="order-line"><div><b>${esc(text(y.productName))}</b><small>${esc(text(y.skuSnapshot))} · ${esc(text(y.variant))} · ${esc(text(y.color))}</small></div><strong>${num(y.quantity, 0)} pz</strong><p>Taglie: ${esc(text(y.sizeBreakdown))}<br>Personalizzazione: ${esc(text(y.personalization))}<br>Note: ${esc(text(y.productionNotes))}</p></div>` }).join('') || '<p class="muted">Nessuna riga articolo.</p>'}</div></section>`
  }
  if (module === 'inventory') {
    const rolls = related(records, 'fabric_roll', 'inventoryId', x.id)
    relatedHtml = `<section class="detail-section"><h3>Colli e disponibilità <span>${rolls.length}</span></h3>${rolls.map(item => `<div class="linked-row"><b>${esc(text(p(item).rollCode))}</b><span>${num(p(item).availableMeters)} m disponibili su ${num(p(item).initialMeters)} m · ${esc(label(p(item).status))}</span></div>`).join('') || '<p class="muted">Nessun collo collegato.</p>'}</section>`
  }
  const notes = x.description || x.technicalNotes || x.notes
  return `<div class="detail-grid">${fields}</div>${notes ? `<section class="detail-section"><h3>Note</h3><p>${esc(notes)}</p></section>` : ''}${relatedHtml}`
}

export function renderAnalyticalModule(
  root: HTMLElement,
  module: ModuleView,
  records: CloudRecord[],
  canFinance: boolean,
  onBack: () => void,
  errorMessage = '',
  canManage = false,
  canExport = false
) {
  const types = primaryTypes[module.key] || []
  const primary = records.filter(row => types.includes(row.entity_type))
  const statuses = [...new Set(primary.map(row => String(p(row).status || (p(row).paid === true ? 'PAID' : p(row).paid === false ? 'OPEN' : ''))).filter(Boolean))].sort()
  const searchPlaceholder: Record<string, string> = {
    orders: 'Cerca ordine, cliente o disegno…', production: 'Cerca cliente, ordine, plotter o tessuto…',
    inventory: 'Cerca tessuto, lotto, collo o proprietario…', clients: 'Cerca cliente…', cotton: 'Cerca ordine, cliente o tessuto…',
    zentha: 'Cerca ordine, cliente, articolo o codice…', finance: 'Cerca cliente, scadenza o lavorazione…',
    tasks: 'Cerca attività, cliente o descrizione…', issues: 'Cerca criticità, cliente o ordine…'
  }
  root.innerHTML = `<div class="analytical-page">
    <header class="module-header compact-header"><div><button id="home" class="back">← Dashboard</button><p class="kicker">${esc(module.eyebrow)}</p><h1>${esc(module.title)}</h1><p>${esc(module.description)}</p></div><div class="header-actions">${canManage ? '<span class="permission-chip manage">● Gestione autorizzata</span>' : '<span class="permission-chip">Sola visualizzazione</span>'}<div class="live"><i></i>${primary.length} elementi</div></div></header>
    ${errorMessage ? `<p class="form-message">${esc(errorMessage)}</p>` : ''}
    <section class="metric-grid">${statistics(module.key, primary, records, canFinance)}</section>
    ${moduleInsights(primary)}
    <section class="data-panel"><div class="data-toolbar"><div><p class="kicker">CENTRO OPERATIVO</p><h2>${esc(module.title)}</h2></div><div class="toolbar-actions">${canExport ? '<button id="export-csv" class="secondary compact-action">Esporta CSV</button>' : ''}<button id="print-view" class="secondary compact-action">Stampa</button></div></div>
      <div class="view-tabs" role="tablist"><button class="active" data-view="all">Tutti <b>${primary.length}</b></button><button data-view="operative">Operativi <b>${primary.filter(row => !terminalStatus.test(String(p(row).status || ''))).length}</b></button><button data-view="attention">Da attenzionare <b>${primary.filter(row => isOverdue(row) || /HIGH|URGENT|CRITICAL|BLOCKING/.test(String(p(row).priority || p(row).severity || p(row).status))).length}</b></button><button data-view="archive">Archivio <b>${primary.filter(row => terminalStatus.test(String(p(row).status || ''))).length}</b></button></div>
      <div class="advanced-filters"><label class="search-box">⌕<input id="record-search" type="search" placeholder="${esc(searchPlaceholder[module.key] || 'Cerca nei dati…')}"></label>${statuses.length ? `<select id="status-filter" aria-label="Filtra per stato"><option value="">Tutti gli stati</option>${statuses.map(status => `<option value="${esc(status)}">${esc(label(status))}</option>`).join('')}</select>` : ''}<select id="period-filter" aria-label="Filtra per periodo"><option value="all">Tutto il periodo</option><option value="30">Ultimi 30 giorni</option><option value="90">Ultimi 90 giorni</option><option value="365">Ultimo anno</option></select><select id="group-filter" aria-label="Raggruppa risultati"><option value="none">Nessun raggruppamento</option><option value="date">Raggruppa per mese</option><option value="status">Raggruppa per stato</option><option value="client">Raggruppa per cliente</option></select></div>
      <div class="table-wrap"><table><thead><tr>${(columns[module.key] || ['Record']).map(value => `<th>${esc(value)}</th>`).join('')}</tr></thead><tbody id="record-body"></tbody></table></div>
      <div id="record-empty" class="table-empty" hidden>Nessun elemento corrisponde ai filtri.</div>
      <footer class="table-footer"><span id="result-count"></span><small>Seleziona una riga per aprire il dossier completo</small></footer>
    </section>
    <div id="detail-overlay" class="detail-overlay" hidden><button class="detail-scrim" aria-label="Chiudi"></button><aside class="detail-drawer"><header><div><p class="kicker">DOSSIER COMPLETO</p><h2 id="detail-title"></h2></div><button id="detail-close" aria-label="Chiudi">×</button></header><nav class="dossier-tabs"><button class="active">Riepilogo</button><button>Collegamenti</button><button>Timeline</button><button>Allegati</button>${canFinance ? '<button>Economia</button>' : ''}</nav><div id="detail-content"></div></aside></div>
  </div>`
  const body = root.querySelector<HTMLTableSectionElement>('#record-body')!
  const empty = root.querySelector<HTMLElement>('#record-empty')!
  const search = root.querySelector<HTMLInputElement>('#record-search')!
  const status = root.querySelector<HTMLSelectElement>('#status-filter')
  const period = root.querySelector<HTMLSelectElement>('#period-filter')!
  const group = root.querySelector<HTMLSelectElement>('#group-filter')!
  const resultCount = root.querySelector<HTMLElement>('#result-count')!
  let currentView = 'all'
  let visibleRows: CloudRecord[] = []
  const draw = () => {
    const term = search.value.trim().toLowerCase(); const selected = status?.value || ''
    const cutoff = period.value === 'all' ? '' : new Date(Date.now() - Number(period.value) * 86400000).toISOString().slice(0, 10)
    visibleRows = primary.filter(row => {
      const rowStatus = String(p(row).status || (p(row).paid ? 'PAID' : 'OPEN'))
      const viewMatch = currentView === 'all' || (currentView === 'operative' && !terminalStatus.test(rowStatus)) ||
        (currentView === 'archive' && terminalStatus.test(rowStatus)) ||
        (currentView === 'attention' && (isOverdue(row) || /HIGH|URGENT|CRITICAL|BLOCKING/.test(String(p(row).priority || p(row).severity || rowStatus))))
      return viewMatch && JSON.stringify(p(row)).toLowerCase().includes(term) && (!selected || rowStatus === selected) && (!cutoff || moduleDate(row) >= cutoff)
    })
    const groupValue = (row: CloudRecord) => group.value === 'date' ? moduleDate(row).slice(0, 7) || 'Senza data' : group.value === 'status' ? translateCode(p(row).status || 'SENZA_STATO') : group.value === 'client' ? text(p(row).clientName || p(row).client || p(row).ownerName, 'Senza cliente') : ''
    let previousGroup = ''
    body.innerHTML = visibleRows.map(row => {
      const currentGroup = groupValue(row)
      const heading = currentGroup && currentGroup !== previousGroup ? `<tr class="group-row"><td colspan="${(columns[module.key] || ['Record']).length}">${esc(group.value === 'date' && /^\d{4}-\d{2}$/.test(currentGroup) ? new Date(`${currentGroup}-01T12:00:00`).toLocaleDateString('it-IT', { month: 'long', year: 'numeric' }) : currentGroup)}</td></tr>` : ''
      previousGroup = currentGroup
      return `${heading}<tr data-record="${esc(row.id)}">${cells(module.key, row, records).map(value => `<td>${value}</td>`).join('')}</tr>`
    }).join('')
    empty.hidden = visibleRows.length > 0
    resultCount.textContent = `${visibleRows.length} elementi visualizzati su ${primary.length}`
    body.querySelectorAll<HTMLTableRowElement>('tr').forEach(element => element.addEventListener('click', () => {
      if (!element.dataset.record) return
      const row = records.find(item => item.id === element.dataset.record); if (!row) return
      root.querySelector<HTMLElement>('#detail-title')!.textContent = titleOf(row)
      root.querySelector<HTMLElement>('#detail-content')!.innerHTML = detailContent(module.key, row, records)
      root.querySelector<HTMLElement>('#detail-overlay')!.hidden = false
      document.body.classList.add('drawer-open')
    }))
  }
  draw(); search.addEventListener('input', draw); status?.addEventListener('change', draw); period.addEventListener('change', draw); group.addEventListener('change', draw)
  root.querySelectorAll<HTMLButtonElement>('[data-view]').forEach(button => button.addEventListener('click', () => {
    currentView = button.dataset.view || 'all'
    root.querySelectorAll('[data-view]').forEach(item => item.classList.toggle('active', item === button))
    draw()
  }))
  root.querySelector('#export-csv')?.addEventListener('click', () => downloadCsv(module, visibleRows))
  root.querySelector('#print-view')?.addEventListener('click', () => window.print())
  const close = () => { root.querySelector<HTMLElement>('#detail-overlay')!.hidden = true; document.body.classList.remove('drawer-open') }
  root.querySelector('#detail-close')?.addEventListener('click', close)
  root.querySelector('.detail-scrim')?.addEventListener('click', close)
  root.querySelector('#home')?.addEventListener('click', onBack)
}
