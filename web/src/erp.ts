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

function productionAnalytics(rows: CloudRecord[]) {
  const clientMeters = new Map<string, number>()
  const plotters = new Map<string, { count: number; paper: number }>()
  rows.forEach(row => {
    const x = p(row); const client = text(x.clientName, 'Senza cliente'); const plotter = text(x.plotter, 'Non indicato')
    clientMeters.set(client, (clientMeters.get(client) || 0) + Number(x.printedPaperMeters || 0))
    const current = plotters.get(plotter) || { count: 0, paper: 0 }; current.count += 1; current.paper += Number(x.printedPaperMeters || 0); plotters.set(plotter, current)
  })
  const clients = [...clientMeters.entries()].sort((a,b) => b[1] - a[1]).slice(0, 7)
  const maxClient = Math.max(...clients.map(([,value]) => value), 1)
  const paper = rows.reduce((sum,row) => sum + Number(p(row).printedPaperMeters || 0), 0)
  const fabric = rows.reduce((sum,row) => sum + Number(p(row).fabricUsedMeters || 0), 0)
  const protection = rows.reduce((sum,row) => sum + Number(p(row).protectionPaperMeters || 0), 0)
  return `<section class="production-analytics"><article><p class="kicker">VOLUMI</p><h3>Materiali lavorati</h3><div class="production-volume"><div><span>Carta stampata</span><strong class="cyan">${num(paper)} m</strong></div><div><span>Tessuto usato</span><strong class="pink">${num(fabric)} m</strong></div><div><span>Carta protezione</span><strong class="orange">${num(protection)} m</strong></div></div></article><article><p class="kicker">CLIENTI</p><h3>Metri carta per cliente</h3><div class="client-meter-bars">${clients.map(([name,value]) => `<div><span>${esc(name)}</span><i><b style="width:${Math.max(3,value/maxClient*100)}%"></b></i><strong>${num(value)} m</strong></div>`).join('') || '<p class="muted">Nessuna produzione nel periodo.</p>'}</div></article><article><p class="kicker">MACCHINE</p><h3>Utilizzo plotter</h3><div class="plotter-list">${[...plotters.entries()].sort((a,b)=>b[1].paper-a[1].paper).map(([name,value]) => `<div><span><b>${esc(name)}</b><small>${value.count} sessioni</small></span><strong>${num(value.paper)} m</strong></div>`).join('') || '<p class="muted">Nessun plotter indicato.</p>'}</div></article></section>`
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
  else if (module === 'zentha' && row.entity_type === 'zentha_product') fields = field('Codice articolo', x.sku) + field('Prodotto', x.name) + field('Categoria', x.category) + field('Descrizione', x.description) + field('Listino', x.priceListName) + (has(row, 'standardPrice') ? field('Prezzo', x.standardPrice, 'money') + field('Costo', x.standardCost, 'money') + field('Ricarico %', x.markupPercent, 'number') : '') + field('Stato', x.active === false ? 'Non attivo' : 'Attivo')
  else if (module === 'zentha') fields = field('Ordine', x.orderCode) + field('Cliente', x.clientName) + field('Stato', x.status) + field('Data ordine', x.orderDate, 'date') + field('Consegna', x.dueDate, 'date')
  else fields = Object.entries(x).filter(([key, value]) => !key.startsWith('_') && !['id', 'attachmentCount', 'materials'].includes(key) && typeof value !== 'object').slice(0, 14).map(([key, value]) => field(label(key), value)).join('')

  let relatedHtml = ''
  if (module === 'orders') {
    const productions = related(records, 'production_record', 'workOrderId', x.id)
    const timeline = related(records, 'order_timeline_event', 'workOrderId', x.id)
    relatedHtml = `<section class="detail-section"><h3>Produzioni collegate <span>${productions.length}</span></h3>${productions.map(item => `<div class="linked-row"><b>${date(p(item).date)}</b><span>${esc(text(p(item).clientName))} · ${num(p(item).printedPaperMeters)} m carta · ${num(p(item).fabricUsedMeters)} m tessuto</span></div>`).join('') || '<p class="muted">Nessuna produzione collegata.</p>'}</section>
      <section class="detail-section"><h3>Timeline ordine <span>${timeline.length}</span></h3>${timeline.map(item => `<div class="linked-row"><b>${date(p(item).date)} · ${esc(text(p(item).title))}</b><span>${esc(text(p(item).description, 'Nessuna descrizione'))}</span></div>`).join('') || '<p class="muted">Nessun evento visibile per il tuo ruolo.</p>'}</section>`
  }
  if (module === 'zentha' && row.entity_type === 'zentha_order') {
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

function zenthaCatalog(products: CloudRecord[], canFinance: boolean) {
  const lists = new Map<string, number>()
  products.forEach(row => { const name = text(p(row).priceListName, 'Listino standard'); lists.set(name, (lists.get(name) || 0) + 1) })
  return `<section class="zentha-catalog"><div class="catalog-heading"><div><p class="kicker">CATALOGO ZENTHA</p><h2>Prodotti e listini</h2><p>${products.length} articoli sincronizzati · ${lists.size || 1} listini rilevati</p></div><div class="price-list-pills">${[...lists.entries()].map(([name, count]) => `<span><b>${esc(name)}</b>${count} prodotti</span>`).join('') || '<span><b>Listino standard</b>In attesa di prodotti</span>'}</div></div><div class="product-grid">${products.map(row => { const x = p(row); return `<button class="product-card" data-product="${esc(row.id)}"><span class="product-sku">${esc(text(x.sku, 'S/C'))}</span><h3>${esc(text(x.name, 'Prodotto Zentha'))}</h3><p>${esc(text(x.category, 'Senza categoria'))}</p><div><span>${esc(text(x.priceListName, 'Listino standard'))}</span>${canFinance && has(row, 'standardPrice') ? `<strong>${euro(x.standardPrice)}</strong>` : '<strong>Prezzo riservato</strong>'}</div></button>` }).join('') || '<div class="empty-state"><h3>Catalogo non ancora sincronizzato</h3><p>Pubblica una revisione Android aggiornata per trasferire i prodotti Zentha nel cloud.</p></div>'}</div></section>`
}

function printZenthaOrder(order: CloudRecord, records: CloudRecord[], profile: 'production'|'accounting'|'complete') {
  const x = p(order); const items = related(records, 'zentha_order_item', 'orderId', x.id)
  const showPrices = profile !== 'production'; const showCosts = profile === 'complete'
  const title = profile === 'production' ? 'Scheda di produzione' : profile === 'accounting' ? 'Riepilogo contabilità' : 'Dossier completo'
  const total = items.reduce((sum, item) => sum + Number(p(item).quantity || 0) * Number(p(item).unitPrice || 0), 0)
  const page = window.open('', '_blank')
  if (!page) return
  page.document.write(`<!doctype html><html lang="it"><head><meta charset="utf-8"><title>${esc(title)} · ${esc(text(x.orderCode))}</title><style>body{font-family:Arial,sans-serif;color:#17171b;margin:32px;font-size:14px}header{display:flex;justify-content:space-between;border-bottom:4px solid #ff2db2;padding-bottom:18px}h1{font-size:28px;margin:5px 0}.brand{font-weight:900;letter-spacing:.12em}.meta{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:22px 0}.meta div{border:1px solid #ddd;padding:12px}.meta small{display:block;color:#666;margin-bottom:5px}table{width:100%;border-collapse:collapse}th{background:#17171b;color:white;text-align:left;padding:10px}td{padding:12px 10px;border-bottom:1px solid #ddd;vertical-align:top}.qty{font-size:18px;font-weight:800}.notes{margin-top:22px;padding:15px;background:#f5f5f7}.total{text-align:right;font-size:22px;font-weight:900;margin-top:18px}@media print{body{margin:15mm}@page{size:A4;margin:12mm}}</style></head><body><header><div><div class="brand">SPECTRO · ZENTHA</div><h1>${esc(title)}</h1><div>Documento operativo</div></div><div><b>${esc(text(x.orderCode, 'Ordine Zentha'))}</b><br>${formatItalianDate(new Date().toISOString())}</div></header><section class="meta"><div><small>Cliente</small><b>${esc(text(x.clientName))}</b></div><div><small>Data ordine</small><b>${date(x.orderDate)}</b></div><div><small>Consegna</small><b>${date(x.dueDate)}</b></div><div><small>Stato</small><b>${esc(label(x.status))}</b></div></section><table><thead><tr><th>Articolo</th><th>Dettagli produzione</th><th>Quantità</th>${showPrices ? '<th>Prezzo</th><th>Totale</th>' : ''}${showCosts ? '<th>Costo</th>' : ''}</tr></thead><tbody>${items.map(item => { const y = p(item); const lineTotal = Number(y.quantity || 0) * Number(y.unitPrice || 0); return `<tr><td><b>${esc(text(y.productName))}</b><br><small>${esc(text(y.skuSnapshot))} · ${esc(text(y.variant))} · ${esc(text(y.color))}</small></td><td><b>Taglie:</b> ${esc(text(y.sizeBreakdown))}<br><b>Personalizzazione:</b> ${esc(text(y.personalization))}<br><b>Note:</b> ${esc(text(y.productionNotes))}</td><td class="qty">${num(y.quantity, 0)} pz</td>${showPrices ? `<td>${euro(y.unitPrice)}</td><td>${euro(lineTotal)}</td>` : ''}${showCosts ? `<td>${euro(y.unitCost)}</td>` : ''}</tr>` }).join('')}</tbody></table>${showPrices ? `<div class="total">Totale articoli: ${euro(total)}</div>` : ''}<div class="notes"><b>Note ordine</b><br>${esc(text(x.notes, 'Nessuna nota aggiuntiva.'))}</div><script>window.onload=()=>window.print()<\/script></body></html>`)
  page.document.close()
}

function printProductionReport(rows: CloudRecord[], from: string, to: string, profile: 'executive'|'operations'|'complete', canFinance: boolean) {
  const paper = rows.reduce((sum, row) => sum + Number(p(row).printedPaperMeters || 0), 0)
  const fabric = rows.reduce((sum, row) => sum + Number(p(row).fabricUsedMeters || 0), 0)
  const revenue = rows.reduce((sum, row) => sum + Number(p(row).grossRevenue || 0), 0)
  const profit = rows.reduce((sum, row) => sum + Number(p(row).grossProfit || 0), 0)
  const clients = new Map<string, { paper: number; fabric: number; revenue: number; count: number }>()
  rows.forEach(row => { const x = p(row); const name = text(x.clientName, 'Senza cliente'); const current = clients.get(name) || { paper: 0, fabric: 0, revenue: 0, count: 0 }; current.paper += Number(x.printedPaperMeters || 0); current.fabric += Number(x.fabricUsedMeters || 0); current.revenue += Number(x.grossRevenue || 0); current.count += 1; clients.set(name, current) })
  const sortedClients = [...clients.entries()].sort((a, b) => b[1].paper - a[1].paper)
  const maximum = Math.max(...sortedClients.map(([, value]) => value.paper), 1)
  const showMoney = canFinance && profile !== 'operations'
  const title = profile === 'operations' ? 'Report operativo produzione' : profile === 'complete' ? 'Analisi completa produzione' : 'Report direzionale produzione'
  const page = window.open('', '_blank')
  if (!page) return
  page.document.write(`<!doctype html><html lang="it"><head><meta charset="utf-8"><title>${esc(title)}</title><style>*{box-sizing:border-box}body{margin:0;background:#08090d;color:#f8f8fa;font-family:Arial,sans-serif;font-size:13px;-webkit-print-color-adjust:exact;print-color-adjust:exact}.page{width:210mm;min-height:297mm;margin:auto;padding:14mm;background:#08090d;page-break-after:always}.page:last-child{page-break-after:auto}header{display:flex;justify-content:space-between;align-items:end;border-top:7px solid #ff7b19;border-bottom:2px solid #ff2db2;padding:18px 0}header h1{font-size:30px;margin:4px 0}.brand{color:#ff8a25;font-weight:900;letter-spacing:.12em}.period{font-size:16px}.kpis{display:grid;grid-template-columns:repeat(${showMoney ? 4 : 3},1fr);gap:10px;margin:22px 0}.kpi,.panel,.production{background:#171920;border:1px solid #30333d;border-radius:15px;padding:16px}.kpi small{color:#9298a8;text-transform:uppercase}.kpi strong{display:block;font-size:27px;margin-top:12px}.cyan{color:#12d9f3}.pink{color:#ff2db2}.orange{color:#ff8a25}.green{color:#29d87d}h2{font-size:22px;margin:26px 0 13px}.client{display:grid;grid-template-columns:160px 1fr 95px;gap:12px;align-items:center;margin:11px 0}.bar{height:12px;background:#282b34;border-radius:8px;overflow:hidden}.bar i{display:block;height:100%;background:linear-gradient(90deg,#ff2db2,#ff8a25);border-radius:8px}.client b{text-align:right}.production{margin:10px 0;display:grid;grid-template-columns:1fr auto;gap:8px}.production h3{margin:0;font-size:16px}.production p{margin:5px 0 0;color:#b8bcc8;line-height:1.45}.production>strong{font-size:17px;color:#12d9f3}.money{color:#29d87d!important}.footer{margin-top:22px;border-top:1px solid #343743;padding-top:10px;color:#858b9b;display:flex;justify-content:space-between}@media print{body{background:#08090d}@page{size:A4;margin:0}.page{margin:0}}</style></head><body><section class="page"><header><div><div class="brand">SPECTRO ERP · CONTROLLO PRODUZIONE</div><h1>${esc(title)}</h1><span>Analisi gestionale e operativa</span></div><div class="period">${formatItalianDate(from)} – ${formatItalianDate(to)}</div></header><div class="kpis"><article class="kpi"><small>Registrazioni</small><strong>${rows.length}</strong></article><article class="kpi"><small>Carta stampata</small><strong class="cyan">${num(paper)} m</strong></article><article class="kpi"><small>Tessuto lavorato</small><strong class="pink">${num(fabric)} m</strong></article>${showMoney ? `<article class="kpi"><small>Fatturato</small><strong class="green">${euro(revenue)}</strong></article>` : ''}</div><section class="panel"><h2>Metri carta per cliente</h2>${sortedClients.map(([name, value]) => `<div class="client"><span>${esc(name)}<br><small>${value.count} produzioni</small></span><div class="bar"><i style="width:${Math.max(2, value.paper / maximum * 100)}%"></i></div><b>${num(value.paper)} m</b></div>`).join('') || '<p>Nessun dato nel periodo.</p>'}</section>${showMoney ? `<section class="panel"><h2>Riepilogo economico</h2><div class="kpis"><article><small>Fatturato rilevato</small><strong class="green">${euro(revenue)}</strong></article><article><small>Utile lordo rilevato</small><strong class="${profit >= 0 ? 'green' : 'orange'}">${euro(profit)}</strong></article></div></section>` : ''}<div class="footer"><span>Spectro ERP · documento generato dal portale</span><span>Pagina 1</span></div></section><section class="page"><header><div><div class="brand">SPECTRO ERP</div><h1>Dettaglio produzioni</h1></div><div class="period">${rows.length} registrazioni</div></header><h2>Produzioni dal ${formatItalianDate(from)} al ${formatItalianDate(to)}</h2>${rows.sort((a,b) => moduleDate(a).localeCompare(moduleDate(b))).map(row => { const x=p(row); return `<article class="production"><div><h3>${date(x.date)} · ${esc(text(x.clientName))}</h3><p><b>${esc(text(x.orderRef, 'Senza riferimento ordine'))}</b> · ${esc(Array.isArray(x.designs) ? x.designs.join(', ') : text(x.designs, 'Nessun disegno'))}<br>Carta ${num(x.printedPaperMeters)} m · Tessuto ${num(x.fabricUsedMeters)} m · Plotter ${esc(text(x.plotter))}<br>${esc(text(x.technicalNotes, 'Nessuna nota tecnica'))}</p></div><strong class="${showMoney ? 'money' : ''}">${showMoney ? euro(x.grossRevenue) : `${num(x.printedPaperMeters)} m`}</strong></article>` }).join('') || '<p>Nessuna produzione nel periodo.</p>'}<div class="footer"><span>Spectro ERP · ${esc(profile === 'operations' ? 'uso produzione' : 'uso gestionale')}</span><span>Dettaglio</span></div></section><script>window.onload=()=>window.print()<\/script></body></html>`)
  page.document.close()
}

type EditableField = { key: string; label: string; kind?: 'text'|'number'|'date'|'textarea' }
function editableFields(module: string, row: CloudRecord): EditableField[] {
  if (row.entity_type === 'zentha_product') return [{key:'sku',label:'Codice articolo'},{key:'name',label:'Nome prodotto'},{key:'category',label:'Categoria'},{key:'description',label:'Descrizione',kind:'textarea'},{key:'standardCost',label:'Costo standard',kind:'number'},{key:'standardPrice',label:'Prezzo listino',kind:'number'},{key:'priceListName',label:'Nome listino'}]
  const fields: Record<string, EditableField[]> = {
    orders:[{key:'orderCode',label:'Codice ordine'},{key:'client',label:'Cliente'},{key:'title',label:'Titolo'},{key:'status',label:'Stato'},{key:'priority',label:'Priorità'},{key:'receivedDate',label:'Ricezione',kind:'date'},{key:'dueDate',label:'Consegna',kind:'date'},{key:'orderedMeters',label:'Metri ordinati',kind:'number'},{key:'designCode',label:'Codice disegno'},{key:'notes',label:'Note',kind:'textarea'}],
    production:[{key:'date',label:'Data',kind:'date'},{key:'clientName',label:'Cliente'},{key:'orderRef',label:'Riferimento ordine'},{key:'plotter',label:'Plotter'},{key:'printedPaperMeters',label:'Carta stampata (m)',kind:'number'},{key:'protectionPaperMeters',label:'Carta protezione (m)',kind:'number'},{key:'fabricUsedMeters',label:'Tessuto usato (m)',kind:'number'},{key:'fabricDisplayName',label:'Tessuto'},{key:'technicalNotes',label:'Note tecniche',kind:'textarea'}],
    inventory:[{key:'fabricName',label:'Tessuto'},{key:'ownerName',label:'Proprietario'},{key:'quantityMeters',label:'Disponibilità (m)',kind:'number'},{key:'minimumQuantityMeters',label:'Soglia minima (m)',kind:'number'},{key:'supplierName',label:'Fornitore'},{key:'composition',label:'Composizione'},{key:'variant',label:'Variante'},{key:'lotCode',label:'Lotto'}],
    clients:[{key:'name',label:'Ragione sociale / nome'},{key:'email',label:'Email'},{key:'phone',label:'Telefono'},{key:'address',label:'Indirizzo'},{key:'notes',label:'Note',kind:'textarea'}],
    cotton:[{key:'orderCode',label:'Codice ordine'},{key:'clientName',label:'Cliente'},{key:'fabricName',label:'Tessuto'},{key:'orderedMeters',label:'Metri ordinati',kind:'number'},{key:'actualProducedMeters',label:'Metri prodotti',kind:'number'},{key:'dueDate',label:'Consegna',kind:'date'},{key:'status',label:'Stato'},{key:'notes',label:'Note',kind:'textarea'}],
    zentha:[{key:'orderCode',label:'Codice ordine'},{key:'clientName',label:'Cliente'},{key:'orderDate',label:'Data ordine',kind:'date'},{key:'dueDate',label:'Consegna',kind:'date'},{key:'status',label:'Stato'},{key:'notes',label:'Note',kind:'textarea'}],
    tasks:[{key:'title',label:'Attività'},{key:'clientName',label:'Cliente'},{key:'dueDate',label:'Scadenza',kind:'date'},{key:'priority',label:'Priorità'},{key:'status',label:'Stato'},{key:'description',label:'Descrizione',kind:'textarea'}],
    issues:[{key:'title',label:'Titolo'},{key:'clientName',label:'Cliente'},{key:'orderRef',label:'Ordine'},{key:'status',label:'Stato'},{key:'severity',label:'Gravità'},{key:'description',label:'Descrizione',kind:'textarea'}],
    finance:[{key:'title',label:'Voce'},{key:'clientName',label:'Cliente'},{key:'date',label:'Data',kind:'date'},{key:'dueDate',label:'Scadenza',kind:'date'},{key:'amount',label:'Importo',kind:'number'},{key:'status',label:'Stato'},{key:'notes',label:'Note',kind:'textarea'}]
  }
  return fields[module] || []
}

export function renderAnalyticalModule(
  root: HTMLElement,
  module: ModuleView,
  records: CloudRecord[],
  canFinance: boolean,
  onBack: () => void,
  errorMessage = '',
  canManage = false,
  canExport = false,
  onUpdate?: (row: CloudRecord, patch: Record<string, unknown>) => Promise<string | null>
) {
  const types = primaryTypes[module.key] || []
  const primary = records.filter(row => types.includes(row.entity_type))
  const products = module.key === 'zentha' ? records.filter(row => row.entity_type === 'zentha_product') : []
  const today = new Date().toISOString().slice(0, 10)
  let rangeStart = `${today.slice(0, 7)}-01`
  let rangeEnd = today
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
    <section class="metric-grid" id="module-metrics">${statistics(module.key, primary.filter(row => moduleDate(row) >= rangeStart && moduleDate(row) <= rangeEnd), records, canFinance)}</section>
    <div id="module-insights">${moduleInsights(primary.filter(row => moduleDate(row) >= rangeStart && moduleDate(row) <= rangeEnd))}</div>
    ${module.key === 'production' ? `<div id="production-analysis">${productionAnalytics(primary.filter(row => moduleDate(row) >= rangeStart && moduleDate(row) <= rangeEnd))}</div>` : ''}
    ${module.key === 'zentha' ? zenthaCatalog(products, canFinance) : ''}
    <section class="data-panel"><div class="data-toolbar"><div><p class="kicker">CENTRO OPERATIVO</p><h2>${esc(module.title)}</h2></div><div class="toolbar-actions">${canExport ? '<button id="open-export-center" class="primary compact-action">Centro esportazioni</button><button id="quick-csv" class="secondary compact-action">CSV rapido</button>' : '<span class="permission-chip">Esportazione non autorizzata</span>'}</div></div>
      <div class="view-tabs" role="tablist"><button class="active" data-view="all">Tutti <b>${primary.length}</b></button><button data-view="operative">Operativi <b>${primary.filter(row => !terminalStatus.test(String(p(row).status || ''))).length}</b></button><button data-view="attention">Da attenzionare <b>${primary.filter(row => isOverdue(row) || /HIGH|URGENT|CRITICAL|BLOCKING/.test(String(p(row).priority || p(row).severity || p(row).status))).length}</b></button><button data-view="archive">Archivio <b>${primary.filter(row => terminalStatus.test(String(p(row).status || ''))).length}</b></button></div>
      <div class="advanced-filters"><label class="search-box">⌕<input id="record-search" type="search" placeholder="${esc(searchPlaceholder[module.key] || 'Cerca nei dati…')}"></label>${statuses.length ? `<select id="status-filter" aria-label="Filtra per stato"><option value="">Tutti gli stati</option>${statuses.map(status => `<option value="${esc(status)}">${esc(label(status))}</option>`).join('')}</select>` : ''}<select id="period-filter" aria-label="Scegli periodo"><option value="current_month">Mese corrente</option><option value="previous_month">Mese precedente</option><option value="7">Ultimi 7 giorni</option><option value="30">Ultimi 30 giorni</option><option value="90">Ultimi 90 giorni</option><option value="custom">Personalizzato</option></select><label class="date-filter">Dal<input id="period-from" type="date" value="${rangeStart}"></label><label class="date-filter">Al<input id="period-to" type="date" value="${rangeEnd}"></label><select id="group-filter" aria-label="Raggruppa risultati"><option value="none">Nessun raggruppamento</option><option value="date">Raggruppa per mese</option><option value="status">Raggruppa per stato</option><option value="client">Raggruppa per cliente</option></select></div>
      <div class="table-wrap"><table><thead><tr>${(columns[module.key] || ['Record']).map(value => `<th>${esc(value)}</th>`).join('')}</tr></thead><tbody id="record-body"></tbody></table></div>
      <div id="record-empty" class="table-empty" hidden>Nessun elemento corrisponde ai filtri.</div>
      <footer class="table-footer"><span id="result-count"></span><small>Seleziona una riga per aprire il dossier completo</small></footer>
    </section>
    <div id="detail-overlay" class="detail-overlay" hidden><button class="detail-scrim" aria-label="Chiudi"></button><aside class="detail-drawer"><header><div><p class="kicker">DOSSIER COMPLETO</p><h2 id="detail-title"></h2></div><button id="detail-close" aria-label="Chiudi">×</button></header><nav class="dossier-tabs"><button class="active">Riepilogo</button><button>Collegamenti</button><button>Timeline</button><button>Allegati</button>${canFinance ? '<button>Economia</button>' : ''}</nav><div id="detail-actions" class="detail-actions"></div><div id="detail-content"></div></aside></div>
    <div id="export-overlay" class="export-overlay" hidden><button class="export-scrim" aria-label="Chiudi"></button><section class="export-center"><header><div><p class="kicker">CENTRO ESPORTAZIONI</p><h2>Prepara il documento</h2><p>Il report userà il periodo e i filtri attualmente selezionati.</p></div><button id="export-close" aria-label="Chiudi">×</button></header>${module.key === 'production' ? `<div class="export-options"><label class="export-choice"><input type="radio" name="report-profile" value="executive" checked><span><b>Report direzionale</b><small>KPI, metri per cliente, dati economici autorizzati e dettaglio produzioni.</small></span></label><label class="export-choice"><input type="radio" name="report-profile" value="operations"><span><b>Report operativo</b><small>Produzioni, ordini, disegni, carta, tessuto e note senza prezzi.</small></span></label><label class="export-choice"><input type="radio" name="report-profile" value="complete"><span><b>Analisi completa</b><small>Documento analitico con indicatori economici per amministrazione.</small></span></label></div>` : `<div class="export-options"><div class="export-choice static"><span><b>Elenco filtrato ${esc(module.title)}</b><small>${primary.length} elementi disponibili prima dei filtri.</small></span></div></div>`}<div class="export-summary"><span>Periodo</span><strong id="export-period-label">${formatItalianDate(rangeStart)} – ${formatItalianDate(rangeEnd)}</strong><span>Elementi</span><strong id="export-count-label">0</strong></div><footer><button id="export-csv" class="secondary">Scarica Excel / CSV</button><button id="generate-report" class="primary">${module.key === 'production' ? 'Apri PDF professionale' : 'Stampa elenco filtrato'}</button></footer></section></div>
    <div id="edit-overlay" class="export-overlay" hidden><button class="edit-scrim export-scrim" aria-label="Chiudi"></button><form id="edit-form" class="export-center edit-center"><header><div><p class="kicker">MODIFICA RECORD</p><h2 id="edit-title">Aggiorna dati</h2><p>La modifica viene salvata nel cloud come livello protetto.</p></div><button id="edit-close" type="button" aria-label="Chiudi">×</button></header><div id="edit-fields" class="edit-fields"></div><p id="edit-message" class="form-message" hidden></p><footer><button id="edit-cancel" type="button" class="secondary">Annulla</button><button id="edit-save" type="submit" class="primary">Salva modifiche</button></footer></form></div>
  </div>`
  const body = root.querySelector<HTMLTableSectionElement>('#record-body')!
  const empty = root.querySelector<HTMLElement>('#record-empty')!
  const search = root.querySelector<HTMLInputElement>('#record-search')!
  const status = root.querySelector<HTMLSelectElement>('#status-filter')
  const period = root.querySelector<HTMLSelectElement>('#period-filter')!
  const periodFrom = root.querySelector<HTMLInputElement>('#period-from')!
  const periodTo = root.querySelector<HTMLInputElement>('#period-to')!
  const group = root.querySelector<HTMLSelectElement>('#group-filter')!
  const resultCount = root.querySelector<HTMLElement>('#result-count')!
  let currentView = 'all'
  let visibleRows: CloudRecord[] = []
  let editingRow: CloudRecord | null = null
  const editOverlay = root.querySelector<HTMLElement>('#edit-overlay')!
  const closeEdit = () => { editOverlay.hidden = true; editingRow = null; document.body.classList.remove('drawer-open') }
  const openEdit = (row: CloudRecord) => {
    const fields = editableFields(module.key, row)
    if (!fields.length || !onUpdate) return
    editingRow = row
    root.querySelector<HTMLElement>('#edit-title')!.textContent = `Modifica ${titleOf(row)}`
    root.querySelector<HTMLElement>('#edit-fields')!.innerHTML = fields.map(field => {
      const value = p(row)[field.key] ?? ''
      return field.kind === 'textarea' ? `<label>${esc(field.label)}<textarea name="${esc(field.key)}">${esc(value)}</textarea></label>` : `<label>${esc(field.label)}<input name="${esc(field.key)}" type="${field.kind === 'number' ? 'number' : field.kind === 'date' ? 'date' : 'text'}" ${field.kind === 'number' ? 'step="0.01"' : ''} value="${esc(value)}"></label>`
    }).join('')
    const message = root.querySelector<HTMLElement>('#edit-message')!; message.hidden = true; message.textContent = ''
    editOverlay.hidden = false; document.body.classList.add('drawer-open')
  }
  const draw = () => {
    const term = search.value.trim().toLowerCase(); const selected = status?.value || ''
    const periodRows = primary.filter(row => { const value = moduleDate(row); return value && value >= rangeStart && value <= rangeEnd })
    root.querySelector<HTMLElement>('#module-metrics')!.innerHTML = statistics(module.key, periodRows, records, canFinance)
    root.querySelector<HTMLElement>('#module-insights')!.innerHTML = moduleInsights(periodRows)
    if (module.key === 'production') root.querySelector<HTMLElement>('#production-analysis')!.innerHTML = productionAnalytics(periodRows)
    visibleRows = periodRows.filter(row => {
      const rowStatus = String(p(row).status || (p(row).paid ? 'PAID' : 'OPEN'))
      const viewMatch = currentView === 'all' || (currentView === 'operative' && !terminalStatus.test(rowStatus)) ||
        (currentView === 'archive' && terminalStatus.test(rowStatus)) ||
        (currentView === 'attention' && (isOverdue(row) || /HIGH|URGENT|CRITICAL|BLOCKING/.test(String(p(row).priority || p(row).severity || rowStatus))))
      return viewMatch && JSON.stringify(p(row)).toLowerCase().includes(term) && (!selected || rowStatus === selected)
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
    resultCount.textContent = `${visibleRows.length} elementi visualizzati su ${periodRows.length} nel periodo ${formatItalianDate(rangeStart)} – ${formatItalianDate(rangeEnd)}`
    root.querySelector<HTMLElement>('#export-period-label')!.textContent = `${formatItalianDate(rangeStart)} – ${formatItalianDate(rangeEnd)}`
    root.querySelector<HTMLElement>('#export-count-label')!.textContent = String(visibleRows.length)
    body.querySelectorAll<HTMLTableRowElement>('tr').forEach(element => element.addEventListener('click', () => {
      if (!element.dataset.record) return
      const row = records.find(item => item.id === element.dataset.record); if (!row) return
      root.querySelector<HTMLElement>('#detail-title')!.textContent = titleOf(row)
      root.querySelector<HTMLElement>('#detail-content')!.innerHTML = detailContent(module.key, row, records)
      const actions = root.querySelector<HTMLElement>('#detail-actions')!
      actions.innerHTML = `${canManage && onUpdate ? '<button id="edit-record">Modifica dati</button>' : ''}${module.key === 'zentha' && row.entity_type === 'zentha_order' ? `<button data-export-profile="production">PDF produzione</button>${canFinance ? '<button data-export-profile="accounting">PDF contabilità</button><button data-export-profile="complete">Dossier completo</button>' : ''}` : ''}`
      actions.querySelector('#edit-record')?.addEventListener('click', () => openEdit(row))
      actions.querySelectorAll<HTMLButtonElement>('[data-export-profile]').forEach(button => button.addEventListener('click', () => printZenthaOrder(row, records, button.dataset.exportProfile as 'production'|'accounting'|'complete')))
      root.querySelector<HTMLElement>('#detail-overlay')!.hidden = false
      document.body.classList.add('drawer-open')
    }))
  }
  const setPreset = () => {
    const mode = period.value
    const now = new Date(); const end = now.toISOString().slice(0, 10)
    if (mode === 'custom') return
    if (mode === 'current_month') rangeStart = `${end.slice(0, 7)}-01`
    else if (mode === 'previous_month') { rangeStart = new Date(now.getFullYear(), now.getMonth() - 1, 1).toISOString().slice(0, 10); rangeEnd = new Date(now.getFullYear(), now.getMonth(), 0).toISOString().slice(0, 10); periodFrom.value = rangeStart; periodTo.value = rangeEnd; draw(); return }
    else { const start = new Date(now); start.setDate(start.getDate() - Number(mode) + 1); rangeStart = start.toISOString().slice(0, 10) }
    rangeEnd = end; periodFrom.value = rangeStart; periodTo.value = rangeEnd; draw()
  }
  const setCustom = () => { if (periodFrom.value && periodTo.value && periodFrom.value <= periodTo.value) { period.value = 'custom'; rangeStart = periodFrom.value; rangeEnd = periodTo.value; draw() } }
  draw(); search.addEventListener('input', draw); status?.addEventListener('change', draw); period.addEventListener('change', setPreset); periodFrom.addEventListener('change', setCustom); periodTo.addEventListener('change', setCustom); group.addEventListener('change', draw)
  root.querySelectorAll<HTMLButtonElement>('[data-view]').forEach(button => button.addEventListener('click', () => {
    currentView = button.dataset.view || 'all'
    root.querySelectorAll('[data-view]').forEach(item => item.classList.toggle('active', item === button))
    draw()
  }))
  root.querySelector('#quick-csv')?.addEventListener('click', () => downloadCsv(module, visibleRows))
  const exportOverlay = root.querySelector<HTMLElement>('#export-overlay')!
  const closeExport = () => { exportOverlay.hidden = true; document.body.classList.remove('drawer-open') }
  root.querySelector('#open-export-center')?.addEventListener('click', () => { exportOverlay.hidden = false; document.body.classList.add('drawer-open') })
  root.querySelector('#export-close')?.addEventListener('click', closeExport)
  root.querySelector('.export-scrim')?.addEventListener('click', closeExport)
  root.querySelector('#export-csv')?.addEventListener('click', () => downloadCsv(module, visibleRows))
  root.querySelector('#generate-report')?.addEventListener('click', () => {
    if (module.key === 'production') {
      const profile = root.querySelector<HTMLInputElement>('input[name="report-profile"]:checked')?.value as 'executive'|'operations'|'complete' || 'executive'
      printProductionReport([...visibleRows], rangeStart, rangeEnd, profile, canFinance)
    } else window.print()
  })
  root.querySelectorAll<HTMLButtonElement>('[data-product]').forEach(button => button.addEventListener('click', () => {
    const row = products.find(item => item.id === button.dataset.product); if (!row) return
    root.querySelector<HTMLElement>('#detail-title')!.textContent = titleOf(row)
    root.querySelector<HTMLElement>('#detail-actions')!.innerHTML = canManage && onUpdate ? '<button id="edit-product">Modifica prodotto</button>' : ''
    root.querySelector('#edit-product')?.addEventListener('click', () => openEdit(row))
    root.querySelector<HTMLElement>('#detail-content')!.innerHTML = detailContent(module.key, row, records)
    root.querySelector<HTMLElement>('#detail-overlay')!.hidden = false; document.body.classList.add('drawer-open')
  }))
  const close = () => { root.querySelector<HTMLElement>('#detail-overlay')!.hidden = true; document.body.classList.remove('drawer-open') }
  root.querySelector('#detail-close')?.addEventListener('click', close)
  root.querySelector('.detail-scrim')?.addEventListener('click', close)
  root.querySelector('#edit-close')?.addEventListener('click', closeEdit)
  root.querySelector('#edit-cancel')?.addEventListener('click', closeEdit)
  root.querySelector('.edit-scrim')?.addEventListener('click', closeEdit)
  root.querySelector<HTMLFormElement>('#edit-form')?.addEventListener('submit', async event => {
    event.preventDefault(); if (!editingRow || !onUpdate) return
    const button = root.querySelector<HTMLButtonElement>('#edit-save')!; button.disabled = true; button.textContent = 'Salvataggio…'
    const form = new FormData(event.currentTarget as HTMLFormElement); const patch: Record<string, unknown> = {}
    editableFields(module.key, editingRow).forEach(field => { const raw = String(form.get(field.key) ?? ''); patch[field.key] = field.kind === 'number' ? Number(raw || 0) : raw })
    const error = await onUpdate(editingRow, patch)
    if (error) { const message = root.querySelector<HTMLElement>('#edit-message')!; message.textContent = error; message.hidden = false; button.disabled = false; button.textContent = 'Salva modifiche' }
  })
  root.querySelector('#home')?.addEventListener('click', onBack)
}
