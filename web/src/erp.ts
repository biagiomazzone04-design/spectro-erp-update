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
const date = (value: unknown) => {
  const raw = String(value || '')
  if (!raw) return '—'
  const parsed = new Date(raw.length === 10 ? `${raw}T12:00:00` : raw)
  return Number.isNaN(parsed.getTime()) ? raw : parsed.toLocaleDateString('it-IT')
}
const text = (value: unknown, fallback = '—') => String(value || '').trim() || fallback
const label = (value: unknown) => text(value).replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase())
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

export function renderAnalyticalModule(root: HTMLElement, module: ModuleView, records: CloudRecord[], canFinance: boolean, onBack: () => void, errorMessage = '') {
  const types = primaryTypes[module.key] || []
  const primary = records.filter(row => types.includes(row.entity_type))
  const statuses = [...new Set(primary.map(row => String(p(row).status || (p(row).paid === true ? 'PAID' : p(row).paid === false ? 'OPEN' : ''))).filter(Boolean))].sort()
  root.innerHTML = `<main class="module-page analytical-page">
    <header class="module-header"><button id="home" class="back">← Dashboard</button><div><p class="kicker">${esc(module.eyebrow)}</p><h1>${esc(module.title)}</h1><p>${esc(module.description)}</p></div><div class="live"><i></i>${primary.length} elementi</div></header>
    ${errorMessage ? `<p class="form-message">${esc(errorMessage)}</p>` : ''}
    <section class="metric-grid">${statistics(module.key, primary, records, canFinance)}</section>
    <section class="data-panel"><div class="data-toolbar"><div><p class="kicker">ARCHIVIO OPERATIVO</p><h2>${esc(module.title)}</h2></div><div class="filters"><label class="search-box">⌕<input id="record-search" type="search" placeholder="Cerca cliente, ordine, tessuto…"></label>${statuses.length ? `<select id="status-filter"><option value="">Tutti gli stati</option>${statuses.map(status => `<option value="${esc(status)}">${esc(label(status))}</option>`).join('')}</select>` : ''}</div></div>
      <div class="table-wrap"><table><thead><tr>${(columns[module.key] || ['Record']).map(value => `<th>${esc(value)}</th>`).join('')}</tr></thead><tbody id="record-body"></tbody></table></div>
      <div id="record-empty" class="table-empty" hidden>Nessun elemento corrisponde ai filtri.</div>
    </section>
    <div id="detail-overlay" class="detail-overlay" hidden><button class="detail-scrim" aria-label="Chiudi"></button><aside class="detail-drawer"><header><div><p class="kicker">DOSSIER COMPLETO</p><h2 id="detail-title"></h2></div><button id="detail-close" aria-label="Chiudi">×</button></header><div id="detail-content"></div></aside></div>
  </main>`
  const body = root.querySelector<HTMLTableSectionElement>('#record-body')!
  const empty = root.querySelector<HTMLElement>('#record-empty')!
  const search = root.querySelector<HTMLInputElement>('#record-search')!
  const status = root.querySelector<HTMLSelectElement>('#status-filter')
  const draw = () => {
    const term = search.value.trim().toLowerCase(); const selected = status?.value || ''
    const filtered = primary.filter(row => JSON.stringify(p(row)).toLowerCase().includes(term) && (!selected || String(p(row).status || (p(row).paid ? 'PAID' : 'OPEN')) === selected))
    body.innerHTML = filtered.map(row => `<tr data-record="${esc(row.id)}">${cells(module.key, row, records).map(value => `<td>${value}</td>`).join('')}</tr>`).join('')
    empty.hidden = filtered.length > 0
    body.querySelectorAll<HTMLTableRowElement>('tr').forEach(element => element.addEventListener('click', () => {
      const row = records.find(item => item.id === element.dataset.record); if (!row) return
      root.querySelector<HTMLElement>('#detail-title')!.textContent = titleOf(row)
      root.querySelector<HTMLElement>('#detail-content')!.innerHTML = detailContent(module.key, row, records)
      root.querySelector<HTMLElement>('#detail-overlay')!.hidden = false
      document.body.classList.add('drawer-open')
    }))
  }
  draw(); search.addEventListener('input', draw); status?.addEventListener('change', draw)
  const close = () => { root.querySelector<HTMLElement>('#detail-overlay')!.hidden = true; document.body.classList.remove('drawer-open') }
  root.querySelector('#detail-close')?.addEventListener('click', close)
  root.querySelector('.detail-scrim')?.addEventListener('click', close)
  root.querySelector('#home')?.addEventListener('click', onBack)
}
