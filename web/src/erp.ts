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
  p(row).date || p(row).orderDate || p(row).receivedDate || p(row).dueDate || p(row).createdDate || p(row).openedDate || row.updated_at
)

const isOverdue = (row: CloudRecord) => {
  const due = isoDate(p(row).dueDate || p(row).expectedDeliveryDate || p(row).revisedDeliveryDate)
  return Boolean(due && due < new Date().toISOString().slice(0, 10) && !terminalStatus.test(String(p(row).status || '')))
}

const primaryTypes: Record<string, string[]> = {
  orders: ['work_order', 'zentha_order'], production: ['production_record'], inventory: ['inventory_item'], clients: ['client'],
  cotton: ['cotton_order'], zentha: ['zentha_order'], finance: ['payment_deadline', 'economic_work'],
  tasks: ['operational_task'], issues: ['client_issue', 'order_timeline_event']
}

const columns: Record<string, string[]> = {
  orders: ['Ordine', 'Cliente', 'Consegna', 'Metri', 'Stato'],
  production: ['Cliente', 'Data', 'Carta / tessuto', 'Plotter', 'Disegni'],
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
      `<strong>${esc(text(x.orderCode, `#${x.id}`))}</strong><small><span class="order-origin ${row.entity_type === 'zentha_order' ? 'zentha' : 'spectro'}">${row.entity_type === 'zentha_order' ? 'ZENTHA' : 'SPECTRO'}</span> ${esc(text(x.title, row.entity_type === 'zentha_order' ? 'Ordine sportivo' : 'Commessa'))}</small>`,
      `<strong>${esc(text(x.client || x.clientName))}</strong><small>${esc(text(x.designCode || x.sport, 'Nessun riferimento'))}</small>`,
      `<strong>${date(x.dueDate)}</strong><small>Ricevuto ${date(x.receivedDate || x.orderDate)}</small>`,
      row.entity_type === 'zentha_order'
        ? `<strong>${related(records, 'zentha_order_item', 'orderId', x.id).reduce((sum,item)=>sum+Number(p(item).quantity||0),0)} pz</strong><small>${related(records, 'zentha_order_item', 'orderId', x.id).length} prodotti</small>`
        : `<strong>${num(x.orderedMeters)} m</strong><small>${related(records, 'production_record', 'workOrderId', x.id).length} produzioni</small>`, badge(x.status)
    ]
    case 'production': return [
      `<strong>${esc(text(x.clientName))}</strong><small>${esc(text(x.orderRef, 'Senza riferimento'))}${x.productionRating ? ` · ${'★'.repeat(Math.max(1,Math.min(5,Number(x.productionRating))))}` : ''}</small>`,
      `<strong>${date(x.date)}</strong><small>${esc(label(x.type))}</small>`,
      `<strong>${num(x.printedPaperMeters)} m</strong><small>Tessuto ${num(x.fabricUsedMeters)} m</small>`,
      `<strong>${esc(text(x.plotter))}</strong><small>${esc(text(x.fabricDisplayName, 'Nessun tessuto'))}</small>`,
      `<strong>${esc(Array.isArray(x.designs) ? x.designs.join(', ') || '—' : text(x.designs))}</strong><small>${esc(text(x.technicalNotes, 'Nessuna nota'))}</small>`
    ]
    case 'inventory': {
      const low = Number(x.quantityMeters || 0) <= Number(x.minimumQuantityMeters || 0)
      return [`<strong>${esc(text(x.fabricName))}</strong><small>${esc(text(x.variant, x.composition as string))}</small>`,
        `<strong>${esc(text(x.ownerName))}</strong><small>${esc(label(x.ownerType))}</small>`,
        `<strong>${num(x.quantityMeters)} m</strong><small>Soglia ${num(x.minimumQuantityMeters)} m</small>`,
        `<strong>${esc(text(x.lotCode))}</strong><small>${related(records, 'fabric_roll', 'inventoryId', x.id).length} colli</small>`,
        x.archived === true ? '<span class="status neutral">In archivio</span>' : low ? '<span class="status danger">Sotto soglia</span>' : '<span class="status ok">Disponibile</span>']
    }
    case 'clients': {
      const id = x.id
      return [`<strong>${esc(text(x.name))}</strong><small>Cliente #${esc(id)}</small>`,
        `<strong>${records.filter(item => ['work_order', 'cotton_order', 'zentha_order'].includes(item.entity_type) && matchesClient(x, p(item))).length}</strong><small>commesse</small>`,
        `<strong>${records.filter(item => ['production_record', 'economic_work'].includes(item.entity_type) && matchesClient(x, p(item))).length}</strong><small>registrazioni</small>`,
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

function taskFocusBoard(rows: CloudRecord[], canManage: boolean) {
  const today = new Date().toISOString().slice(0, 10)
  const active = rows.filter(row => !terminalStatus.test(String(p(row).status || '')))
  const priorityRank = (row: CloudRecord) => /URGENT|CRITICAL/.test(String(p(row).priority || '')) ? 0 : p(row).priority === 'HIGH' ? 1 : 2
  const dueOf = (row: CloudRecord) => isoDate(p(row).dueDate)
  const sorted = [...active].sort((a, b) => {
    const aDue = dueOf(a); const bDue = dueOf(b)
    const aUrgency = aDue && aDue <= today ? 0 : /PROGRESS|STARTED|PRODUCTION/.test(String(p(a).status || '')) ? 1 : 2
    const bUrgency = bDue && bDue <= today ? 0 : /PROGRESS|STARTED|PRODUCTION/.test(String(p(b).status || '')) ? 1 : 2
    return aUrgency - bUrgency || priorityRank(a) - priorityRank(b) || (aDue || '9999').localeCompare(bDue || '9999')
  })
  const buckets = [
    { key: 'work', state: 'OPEN', title: 'Attività da lavorare', subtitle: 'Tutto ciò che richiede il tuo intervento', rows: sorted },
    { key: 'completed', state: 'DONE', title: 'Completate', subtitle: 'Ultime attività concluse', rows: rows.filter(row => terminalStatus.test(String(p(row).status || ''))).sort((a,b) => b.updated_at.localeCompare(a.updated_at)) }
  ]
  const card = (row: CloudRecord) => {
    const x = p(row); const due = dueOf(row); const overdue = Boolean(due && due < today)
    return `<button class="focus-task-card ${overdue ? 'overdue' : ''}" data-task="${esc(row.id)}" ${canManage ? 'draggable="true"' : ''}>
      <span class="task-card-top"><b>${esc(label(x.priority || 'NORMAL'))}</b><i>${overdue ? 'Scaduta' : due === today ? 'Oggi' : esc(label(x.status || 'OPEN'))}</i></span>
      <strong>${esc(text(x.title, 'Attività senza titolo'))}</strong>
      <span class="task-client">${esc(text(x.clientName, 'Attività interna'))}</span>
      ${x.description ? `<small>${esc(text(x.description))}</small>` : ''}
      <span class="task-card-footer"><b>${due ? date(due) : 'Nessuna scadenza'}</b><i>Apri scheda →</i></span>
    </button>`
  }
  const calendarDays = Array.from({length:7},(_,index) => { const value = new Date(`${today}T12:00:00`); value.setDate(value.getDate()+index); return value.toISOString().slice(0,10) })
  return `<section class="task-focus-board">
    <header><div><p class="kicker">PRIMO PIANO OPERATIVO</p><h2>Attività da lavorare</h2><p>Le attività aperte sono tutte in primo piano, ordinate per urgenza. ${canManage ? 'Trascinale su Completate quando hai finito.' : ''}</p></div><span><b>${active.length}</b> da lavorare</span></header>
    <div class="task-focus-columns">${buckets.map(bucket => `<article class="task-focus-column ${bucket.key}" ${bucket.state ? `data-drop-state="${bucket.state}"` : ''}><div class="task-column-heading"><span><i></i><strong>${bucket.title}</strong><small>${bucket.subtitle}</small></span><b>${bucket.rows.length}</b></div><div class="task-card-list">${bucket.rows.slice(0, 6).map(card).join('') || '<div class="task-column-empty">Nessuna attività in questa area</div>'}</div></article>`).join('')}</div>
    <div class="task-week"><div class="task-week-heading"><p class="kicker">CALENDARIO RAPIDO</p><h3>Prossimi 7 giorni</h3></div><div class="task-week-grid">${calendarDays.map(day => { const dayRows = active.filter(row => dueOf(row) === day); return `<article class="${day === today ? 'today' : ''}"><span>${new Date(`${day}T12:00:00`).toLocaleDateString('it-IT',{weekday:'short'})}</span><strong>${new Date(`${day}T12:00:00`).toLocaleDateString('it-IT',{day:'2-digit',month:'2-digit'})}</strong><b>${dayRows.length}</b><small>${dayRows.slice(0,2).map(row => esc(titleOf(row))).join(' · ') || 'Nessuna scadenza'}</small></article>` }).join('')}</div></div>
  </section>`
}

function orderFocusBoard(rows: CloudRecord[], allRecords: CloudRecord[]) {
  const today = new Date().toISOString().slice(0, 10)
  const dueOf = (row: CloudRecord) => isoDate(p(row).dueDate || p(row).expectedDeliveryDate)
  const active = rows.filter(row => !terminalStatus.test(String(p(row).status || ''))).sort((a,b) => {
    const aDue = dueOf(a); const bDue = dueOf(b)
    const aLate = aDue && aDue < today ? 0 : 1; const bLate = bDue && bDue < today ? 0 : 1
    return aLate - bLate || (aDue || '9999-12-31').localeCompare(bDue || '9999-12-31') || b.updated_at.localeCompare(a.updated_at)
  })
  const card = (row: CloudRecord) => {
    const x = p(row); const due = dueOf(row); const overdue = Boolean(due && due < today)
    const zentha = row.entity_type === 'zentha_order'
    const pieces = zentha ? related(allRecords, 'zentha_order_item', 'orderId', x.id).reduce((sum,item)=>sum+Number(p(item).quantity||0),0) : 0
    return `<button class="kanban-order-card ${overdue ? 'overdue' : ''}" data-order="${esc(row.id)}">
      <span class="task-card-top"><b class="order-origin ${zentha ? 'zentha' : 'spectro'}">${zentha ? 'ZENTHA' : 'SPECTRO'}</b><i>${overdue ? 'In ritardo' : due === today ? 'Consegna oggi' : due ? date(due) : 'Senza scadenza'}</i></span>
      <strong>${esc(text(x.client || x.clientName, 'Cliente non indicato'))}</strong>
      <span class="task-client">${esc(text(x.orderCode || x.title, 'Commessa senza riferimento'))}</span>
      <small>${esc(text(x.designCode || x.sport || x.description || x.notes, 'Nessuna indicazione tecnica'))}</small>
      <span class="task-card-footer"><b>${zentha ? `${pieces || '—'} pezzi` : `${num(x.orderedMeters)} m`}</b><i>Apri dossier</i></span>
    </button>`
  }
  const phases = [
    ['blocked','Da sbloccare',(s:string)=>/BLOCKED|WAITING_MATERIAL|WAITING_CLIENT|ON_HOLD/.test(s)],
    ['received','Ricevuto',(s:string)=>/DRAFT|RECEIVED|CONFIRMED/.test(s)],
    ['graphics','Grafica',(s:string)=>/GRAPHICS/.test(s)],
    ['approval','Approvazione',(s:string)=>/WAITING_APPROVAL|APPROVED/.test(s)],
    ['production','In produzione',(s:string)=>/IN_PROCESS|IN_PRODUCTION|PRODUCTION|PRINT/.test(s)],
    ['ready','Pronto',(s:string)=>/READY/.test(s)]
  ] as Array<[string,string,(status:string)=>boolean]>
  return `<section class="order-kanban">
    <header><div><p class="kicker">COMMESSE UNIFICATE</p><h2>Ordini Spectro e Zentha, fase per fase</h2><p>Urgenze, approvazioni e produzione in un’unica lettura. Seleziona una card per modificare il dossier.</p></div><span><b>${active.length}</b> aperti</span></header>
    <div class="kanban-scroll">${phases.map(([key,title,match])=>{const phaseRows=active.filter(row=>match(String(p(row).status||'')));return `<article class="kanban-column ${key}"><div class="kanban-heading"><span><strong>${title}</strong><small>${key==='blocked'?'Materiale o cliente':key==='received'?'Ordine registrato':key==='graphics'?'Bozza in preparazione':key==='approval'?'In attesa del cliente':key==='production'?'Nel piano operativo':'Controllo e consegna'}</small></span><b>${phaseRows.length}</b></div><div>${phaseRows.map(card).join('')||'<p class="kanban-empty">Nessun ordine</p>'}</div></article>`}).join('')}</div>
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
  const reviewed = rows.filter(row => Number(p(row).productionRating || 0) > 0)
  const average = reviewed.length ? reviewed.reduce((sum,row) => sum + Number(p(row).productionRating || 0),0) / reviewed.length : 0
  const critical = reviewed.filter(row => /MEDIUM|HIGH|CRITICAL/.test(String(p(row).productionCriticality || ''))).length
  return `<section class="production-analytics"><article><p class="kicker">VOLUMI</p><h3>Materiali lavorati</h3><div class="production-volume"><div><span>Carta stampata</span><strong class="cyan">${num(paper)} m</strong></div><div><span>Tessuto usato</span><strong class="pink">${num(fabric)} m</strong></div><div><span>Carta protezione</span><strong class="orange">${num(protection)} m</strong></div></div></article><article><p class="kicker">CLIENTI</p><h3>Metri carta per cliente</h3><div class="client-meter-bars">${clients.map(([name,value]) => `<div><span>${esc(name)}</span><i><b style="width:${Math.max(3,value/maxClient*100)}%"></b></i><strong>${num(value)} m</strong></div>`).join('') || '<p class="muted">Nessuna produzione nel periodo.</p>'}</div></article><article><p class="kicker">MACCHINE</p><h3>Utilizzo plotter</h3><div class="plotter-list">${[...plotters.entries()].sort((a,b)=>b[1].paper-a[1].paper).map(([name,value]) => `<div><span><b>${esc(name)}</b><small>${value.count} sessioni</small></span><strong>${num(value.paper)} m</strong></div>`).join('') || '<p class="muted">Nessun plotter indicato.</p>'}</div></article><article class="quality-summary"><p class="kicker">QUALITÀ</p><h3>Esito produzioni</h3><strong>${average ? `${num(average)} / 5` : '—'}</strong><span>${reviewed.length} valutate · ${critical} con criticità</span><div>${average ? '★'.repeat(Math.round(average)) + '☆'.repeat(5-Math.round(average)) : 'Nessuna valutazione nel periodo'}</div></article></section>`
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

function attachmentGallery(row: CloudRecord) {
  const urls = Array.isArray(p(row)._attachmentUrls) ? p(row)._attachmentUrls as unknown[] : []
  const count = Number(p(row).attachmentCount || urls.length || 0)
  if (urls.length) return `<section class="detail-section attachment-section"><h3>Immagini e allegati <span>${urls.length}</span></h3><div class="attachment-gallery">${urls.map((url,index) => `<a href="${esc(url)}" target="_blank" rel="noopener"><img src="${esc(url)}" alt="Allegato ${index + 1}" loading="lazy"><span>Apri immagine</span></a>`).join('')}</div></section>`
  if (count) return `<section class="detail-section attachment-section pending"><h3>Immagini e allegati <span>${count}</span></h3><p>Gli allegati sono custoditi nel workspace Android. Ripubblica il cloud con la versione Android aggiornata per rendere disponibili le anteprime protette sul web.</p></section>`
  return `<section class="detail-section attachment-section empty-attachments"><h3>Immagini e allegati</h3><p>Nessuna immagine associata a questo elemento.</p></section>`
}

function detailContent(module: string, row: CloudRecord, records: CloudRecord[], canFinance: boolean) {
  const x = p(row)
  let fields = ''
  if (module === 'orders') fields = field('Codice ordine', x.orderCode) + field('Cliente', x.client) + field('Stato', x.status) + field('Priorità', x.priority) + field('Data ordine', x.receivedDate, 'date') + field('Consegna materiale', x.materialReceivedDate, 'date') + field('Note consegna materiale', x.materialReceiptNotes) + field('Consegna ordine', x.dueDate, 'date') + field('Metri ordinati', x.orderedMeters, 'number') + field('Disegno', x.designCode) + (has(row, 'printPricePerMeter') ? field('Prezzo stampa / m', x.printPricePerMeter, 'money') : '')
  else if (module === 'production') fields = field('Data', x.date, 'date') + field('Cliente', x.clientName) + field('Ordine', x.orderRef) + field('Ordine Zentha collegato', x.zenthaOrderId) + field('Plotter', x.plotter) + field('Carta stampata', x.printedPaperMeters, 'number') + field('Carta protezione', x.protectionPaperMeters, 'number') + field('Tessuto usato', x.fabricUsedMeters, 'number') + field('Tessuto', x.fabricDisplayName) + (has(row, 'grossRevenue') ? field('Ricavo', x.grossRevenue, 'money') + field('Margine', x.grossProfit, 'money') : '')
  else if (module === 'inventory') fields = field('Tessuto', x.fabricName) + field('Proprietario', x.ownerName) + field('Disponibilità metri', x.quantityMeters, 'number') + field('Soglia minima', x.minimumQuantityMeters, 'number') + field('Fornitore', x.supplierName) + field('Composizione', x.composition) + field('Peso', x.weightGsm) + field('Variante', x.variant) + field('Lotto', x.lotCode) + (has(row, 'purchasePricePerMeter') ? field('Costo acquisto / m', x.purchasePricePerMeter, 'money') + field('Lavorazioni / m', x.processingCostPerMeter, 'money') + field('Vendita predefinita / m', x.defaultSalePricePerMeter, 'money') : '')
  else if (module === 'zentha' && row.entity_type === 'zentha_product') fields = field('Codice articolo', x.sku) + field('Prodotto', x.name) + field('Categoria', x.category) + field('Descrizione', x.description) + field('Listino', x.priceListName) + (has(row, 'standardPrice') ? field('Prezzo', x.standardPrice, 'money') + field('Costo', x.standardCost, 'money') + field('Ricarico %', x.markupPercent, 'number') : '') + field('Stato', x.active === false ? 'Non attivo' : 'Attivo')
  else if (module === 'zentha') fields = field('Ordine', x.orderCode) + field('Cliente', x.clientName) + field('Stato', x.status) + field('Data ordine', x.orderDate, 'date') + field('Consegna', x.dueDate, 'date')
  else fields = Object.entries(x).filter(([key, value]) => !key.startsWith('_') && !['id', 'attachmentCount', 'materials'].includes(key) && typeof value !== 'object').slice(0, 14).map(([key, value]) => field(label(key), value)).join('')

  let relatedHtml = ''
  if (module === 'clients') {
    const types: Record<string, string> = { work_order: 'Ordine sublimazione', production_record: 'Produzione', cotton_order: 'Cotone', economic_work: 'Ulteriori lavorazioni', zentha_order: 'Zentha' }
    const work = records.filter(item => types[item.entity_type] && matchesClient(x, p(item)))
    const now = new Date()
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    const dateOf = (item: CloudRecord) => item.entity_type === 'cotton_order' ? cottonAccountingDate(p(item)) : moduleDate(item)
    const revenueOf = (item: CloudRecord) => {
      const y = p(item)
      if (item.entity_type === 'cotton_order') return cottonAmounts(y).revenue
      if (item.entity_type === 'economic_work') return otherAmounts(y).revenue
      return item.entity_type === 'production_record' && y.type !== 'NEGATIVE' ? Number(y.grossRevenue || 0) : 0
    }
    const thisMonth = work.filter(item => dateOf(item).startsWith(month))
    const history = work.filter(item => !dateOf(item).startsWith(month))
    const section = (title: string, rows: CloudRecord[]) => `<section class="detail-section"><h3>${title} <span>${rows.length}</span></h3>${[...rows].sort((a,b) => dateOf(b).localeCompare(dateOf(a))).map(item => `<div class="linked-row"><b>${esc(titleOf(item))}</b><span>${types[item.entity_type]} · ${date(dateOf(item))} · ${esc(label(p(item).status))}</span></div>`).join('') || '<p class="muted">Nessuna registrazione.</p>'}</section>`
    const canSeeAmounts = canFinance
    relatedHtml = (canSeeAmounts ? `<section class="detail-section"><h3>Fatturato operativo</h3><div class="detail-grid">${field('Mese corrente', thisMonth.reduce((sum, item) => sum + revenueOf(item), 0), 'money')}${field('Totale storico', work.reduce((sum, item) => sum + revenueOf(item), 0), 'money')}</div></section>` : '') + section('Mese corrente', thisMonth) + section('Storico', history)
  }
  if (module === 'orders') {
    const productions = related(records, 'production_record', 'workOrderId', x.id)
    const timeline = related(records, 'order_timeline_event', 'workOrderId', x.id)
    relatedHtml = `<section class="detail-section"><h3>Produzioni collegate <span>${productions.length}</span></h3>${productions.map(item => `<div class="linked-row"><b>${date(p(item).date)}</b><span>${esc(text(p(item).clientName))} · ${num(p(item).printedPaperMeters)} m carta · ${num(p(item).fabricUsedMeters)} m tessuto</span></div>`).join('') || '<p class="muted">Nessuna produzione collegata.</p>'}</section>
      <section class="detail-section"><h3>Timeline ordine <span>${timeline.length}</span></h3>${timeline.map(item => `<div class="linked-row"><b>${date(p(item).date)} · ${esc(text(p(item).title))}</b><span>${esc(text(p(item).description, 'Nessuna descrizione'))}</span></div>`).join('') || '<p class="muted">Nessun evento visibile per il tuo ruolo.</p>'}</section>`
  }
  if (module === 'zentha' && row.entity_type === 'zentha_order') {
    const items = related(records, 'zentha_order_item', 'orderId', x.id)
    const productions = related(records, 'production_record', 'zenthaOrderId', x.id)
    const paperMeters = productions.reduce((sum, production) => sum + Number(p(production).printedPaperMeters || 0), 0)
    const chromia = paperMeters * Number(x.chromiaRatePerMeter || 2.5)
    relatedHtml = `<section class="detail-section"><h3>Stampa Chromia</h3><div class="detail-grid">${field('Produzioni collegate', productions.length, 'number')}${field('Carta stampata', paperMeters, 'number')}${has(row, 'chromiaRatePerMeter') ? field('Tariffa / m', x.chromiaRatePerMeter, 'money') + field('Totale Chromia', chromia, 'money') : ''}</div></section><section class="detail-section"><h3>Articoli ordinati <span>${items.length}</span></h3><div class="detail-lines">${items.map(item => { const y = p(item); return `<div class="order-line"><div><b>${esc(text(y.productName))}</b><small>${esc(text(y.skuSnapshot))} · ${esc(text(y.variant))} · ${esc(text(y.color))}</small></div><strong>${num(y.quantity, 0)} pz</strong><p>Taglie: ${esc(text(y.sizeBreakdown))}<br>Personalizzazione: ${esc(text(y.personalization))}<br>Note: ${esc(text(y.productionNotes))}</p></div>` }).join('') || '<p class="muted">Nessuna riga articolo.</p>'}</div></section>`
  }
  if (module === 'inventory') {
    const rolls = related(records, 'fabric_roll', 'inventoryId', x.id)
    const activeRolls = rolls.filter(item => Number(p(item).availableMeters || 0) > .0001)
    const depletedRolls = rolls.filter(item => Number(p(item).availableMeters || 0) <= .0001)
    relatedHtml = `<section class="detail-section"><h3>Colli disponibili <span>${activeRolls.length}</span></h3>${activeRolls.map(item => `<div class="linked-row"><b>${esc(text(p(item).rollCode))}</b><span>${num(p(item).availableMeters)} m disponibili su ${num(p(item).initialMeters)} m · ${esc(label(p(item).status))}</span></div>`).join('') || '<p class="muted">Nessun collo attivo: il materiale resta consultabile nello storico.</p>'}</section>${depletedRolls.length ? `<section class="detail-section inventory-history"><details><summary>Storico colli esauriti <span>${depletedRolls.length}</span></summary>${depletedRolls.map(item => `<div class="linked-row"><b>${esc(text(p(item).rollCode))}</b><span>0 m disponibili su ${num(p(item).initialMeters)} m · Esaurito</span></div>`).join('')}</details></section>` : ''}`
  }
  if (module === 'production') {
    const designs = Array.isArray(x.designs) ? x.designs : String(x.designs || '').split(',').map(value => value.trim()).filter(Boolean)
    relatedHtml = `<section class="detail-section production-brief"><h3>Scheda tecnica di produzione</h3><div class="production-brief-grid"><article><span>Disegni</span><div class="design-chips">${designs.length ? designs.map(value => `<b>${esc(value)}</b>`).join('') : '<em>Nessun disegno indicato</em>'}</div></article><article><span>Annotazioni operative</span><p>${esc(text(x.technicalNotes, 'Nessuna annotazione tecnica registrata.'))}</p></article></div></section>
      <section class="detail-section production-quality"><h3>Valutazione post-produzione</h3><div class="quality-score"><strong>${x.productionRating ? `${num(x.productionRating,0)} / 5` : 'Non valutata'}</strong><span>${x.productionRating ? '★'.repeat(Math.max(1,Math.min(5,Number(x.productionRating)))) : 'Aggiungi una valutazione dopo la lavorazione'}</span></div><div class="detail-grid">${field('Criticità', x.productionCriticality || 'Non indicata')}${field('Problemi rilevati', x.productionProblems || 'Nessun problema registrato')}${field('Valutazione finale', x.productionReview || 'Nessuna nota di valutazione')}</div></section>`
  }
  const notes = x.description || x.technicalNotes || x.notes
  return `<div class="detail-grid">${fields}</div>${notes && module !== 'production' ? `<section class="detail-section"><h3>Note</h3><p>${esc(notes)}</p></section>` : ''}${relatedHtml}${attachmentGallery(row)}`
}

function zenthaCatalog(products: CloudRecord[], canFinance: boolean) {
  const groups = new Map<string, CloudRecord[]>([['Basket', []], ['Calcio / Volley', []], ['Danza', []], ['Multisport', []]])
  for (const row of products) {
    const x = p(row)
    const terms = `${x.sku ?? ''} ${x.name ?? ''} ${x.category ?? ''} ${x.description ?? ''}`.toLowerCase()
    const sport = /basket|pallacanestro/.test(terms) ? 'Basket' : /calcio|volley|pallavolo/.test(terms) ? 'Calcio / Volley' : /danza|dance/.test(terms) ? 'Danza' : 'Multisport'
    groups.get(sport)!.push(row)
  }
  const card = (row: CloudRecord) => {
    const x = p(row)
    return `<button class="product-card" data-product="${esc(row.id)}"><span class="product-sku">${esc(x.sku)}</span><h3>${esc(x.name)}</h3><p>${esc(x.category)}</p>${canFinance && has(row, 'standardPrice') ? `<div><span>${esc(x.priceListName)}</span><strong>${euro(x.standardPrice)}</strong></div>` : ''}</button>`
  }
  return `<section class="zentha-catalog"><div class="catalog-heading"><div><h2>Catalogo per sport</h2><p>${products.length} articoli</p></div></div>${[...groups].filter(([, rows]) => rows.length).map(([sport, rows]) => `<details class="catalog-sport"><summary>${sport} <span>${rows.length} articoli</span></summary><div class="product-grid">${rows.map(card).join('')}</div></details>`).join('') || '<p>Catalogo non ancora sincronizzato.</p>'}</section>`
}

function printZenthaOrder(order: CloudRecord, records: CloudRecord[], profile: 'production'|'accounting'|'complete') {
  const x = p(order); const items = related(records, 'zentha_order_item', 'orderId', x.id)
  const productions = related(records, 'production_record', 'zenthaOrderId', x.id)
  const paperMeters = productions.reduce((sum, production) => sum + Number(p(production).printedPaperMeters || 0), 0)
  const chromiaAmount = paperMeters * Number(x.chromiaRatePerMeter || 2.5)
  const showPrices = profile !== 'production'; const showCosts = profile === 'complete'
  const title = profile === 'production' ? 'Scheda di produzione' : profile === 'accounting' ? 'Riepilogo contabilità' : 'Dossier completo'
  const total = items.reduce((sum, item) => sum + Number(p(item).quantity || 0) * Number(p(item).unitPrice || 0), 0)
  const page = window.open('', '_blank')
  if (!page) return
  page.document.write(`<!doctype html><html lang="it"><head><meta charset="utf-8"><title>${esc(title)} · ${esc(text(x.orderCode))}</title><style>body{font-family:Arial,sans-serif;color:#17171b;margin:32px;font-size:14px}header{display:flex;justify-content:space-between;border-bottom:4px solid #ff2db2;padding-bottom:18px}h1{font-size:28px;margin:5px 0}.brand{font-weight:900;letter-spacing:.12em}.meta{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:22px 0}.meta div{border:1px solid #ddd;padding:12px}.meta small{display:block;color:#666;margin-bottom:5px}table{width:100%;border-collapse:collapse}th{background:#17171b;color:white;text-align:left;padding:10px}td{padding:12px 10px;border-bottom:1px solid #ddd;vertical-align:top}.qty{font-size:18px;font-weight:800}.notes,.chromia{margin-top:22px;padding:15px;background:#f5f5f7}.chromia{border-left:5px solid #ff2db2}.total{text-align:right;font-size:22px;font-weight:900;margin-top:18px}@media print{body{margin:15mm}@page{size:A4;margin:12mm}}</style></head><body><header><div><div class="brand">SPECTRO · ZENTHA</div><h1>${esc(title)}</h1><div>Documento operativo</div></div><div><b>${esc(text(x.orderCode, 'Ordine Zentha'))}</b><br>${formatItalianDate(new Date().toISOString())}</div></header><section class="meta"><div><small>Cliente</small><b>${esc(text(x.clientName))}</b></div><div><small>Data ordine</small><b>${date(x.orderDate)}</b></div><div><small>Consegna</small><b>${date(x.dueDate)}</b></div><div><small>Stato</small><b>${esc(label(x.status))}</b></div></section><table><thead><tr><th>Articolo</th><th>Dettagli produzione</th><th>Quantità</th>${showPrices ? '<th>Prezzo</th><th>Totale</th>' : ''}${showCosts ? '<th>Costo</th>' : ''}</tr></thead><tbody>${items.map(item => { const y = p(item); const lineTotal = Number(y.quantity || 0) * Number(y.unitPrice || 0); return `<tr><td><b>${esc(text(y.productName))}</b><br><small>${esc(text(y.skuSnapshot))} · ${esc(text(y.variant))} · ${esc(text(y.color))}</small></td><td><b>Taglie:</b> ${esc(text(y.sizeBreakdown))}<br><b>Personalizzazione:</b> ${esc(text(y.personalization))}<br><b>Note:</b> ${esc(text(y.productionNotes))}</td><td class="qty">${num(y.quantity, 0)} pz</td>${showPrices ? `<td>${euro(y.unitPrice)}</td><td>${euro(lineTotal)}</td>` : ''}${showCosts ? `<td>${euro(y.unitCost)}</td>` : ''}</tr>` }).join('')}</tbody></table>${showPrices ? `<div class="total">Totale articoli: ${euro(total)}</div>` : ''}<div class="chromia"><b>Stampa Chromia</b><br>${num(paperMeters)} m carta${showPrices ? ` × ${euro(x.chromiaRatePerMeter || 2.5)}/m = <b>${euro(chromiaAmount)}</b>` : ''}</div><div class="notes"><b>Note ordine</b><br>${esc(text(x.notes, 'Nessuna nota aggiuntiva.'))}</div><script>window.onload=()=>window.print()<\/script></body></html>`)
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

export function printDashboardReport(records: CloudRecord[], from: string, to: string, canFinance: boolean) {
  const within = (row: CloudRecord, ...keys: string[]) => {
    const payload = p(row)
    const value = keys.map(key => String(payload[key] || '').slice(0, 10)).find(Boolean) || moduleDate(row)
    return Boolean(value && value >= from && value <= to)
  }
  const productionRecords = records.filter(row => row.entity_type === 'production_record' && within(row, 'date'))
  const productions = productionRecords.filter(row => String(p(row).type || 'PRODUCTION') !== 'NEGATIVE')
  const negativeEvents = productionRecords.filter(row => String(p(row).type || '') === 'NEGATIVE')
  const orders = records.filter(row => row.entity_type === 'work_order' && within(row, 'receivedDate'))
  const completed = records.filter(row => row.entity_type === 'work_order' && terminalStatus.test(String(p(row).status || '')) && within(row, 'effectiveDeliveryDate', 'dueDate', 'receivedDate'))
  const cottonOrders = records.filter(row => row.entity_type === 'cotton_order' && p(row).status !== 'CANCELLED' && cottonAccountingDate(p(row)) >= from && cottonAccountingDate(p(row)) <= to)
  const otherWorks = records.filter(row => row.entity_type === 'economic_work' && within(row, 'date'))
  const timeline = records.filter(row => row.entity_type === 'order_timeline_event' && within(row, 'date'))
  const issues = records.filter(row => row.entity_type === 'client_issue' && within(row, 'openedDate'))
  const tasks = records.filter(row => row.entity_type === 'operational_task' && within(row, 'createdDate', 'dueDate'))
  const settings = p(records.find(row => row.entity_type === 'app_settings') || ({payload:{}} as CloudRecord))
  const dailyFixedCost = Number(settings.dailyFixedCost || 760)
  const fixedDays = [...new Set(productionRecords.filter(row => p(row).applyFixedCosts !== false).map(row => String(p(row).date || '').slice(0, 10)).filter(Boolean))]
  const fixedCosts = fixedDays.length * dailyFixedCost
  const paper = productions.reduce((sum,row) => sum + Number(p(row).printedPaperMeters || 0), 0)
  const fabric = productions.reduce((sum,row) => sum + Number(p(row).fabricUsedMeters || 0), 0)
  const productionRevenue = productions.reduce((sum,row) => sum + Number(p(row).grossRevenue || 0), 0)
  const cottonRevenue = cottonOrders.reduce((sum,row) => sum + cottonAmounts(p(row)).revenue, 0)
  const otherRevenue = otherWorks.reduce((sum,row) => sum + otherAmounts(p(row)).revenue, 0)
  const productionCosts = productionRecords.reduce((sum,row) => sum + Number(p(row).materialCostTotal || 0), 0)
  const cottonCosts = cottonOrders.reduce((sum,row) => sum + cottonAmounts(p(row)).costs, 0)
  const otherCosts = otherWorks.reduce((sum,row) => sum + otherAmounts(p(row)).costs, 0)
  const costs = productionCosts + cottonCosts + otherCosts + fixedCosts
  const revenue = productionRevenue + cottonRevenue + otherRevenue
  const netProfit = revenue - costs
  const criticalTimeline = timeline.filter(row => !/INFO|LOW/.test(String(p(row).severity || 'INFO')))
  const openIssues = issues.filter(row => !/RESOLVED|CLOSED/.test(String(p(row).status || '')))
  const completedTasks = tasks.filter(row => terminalStatus.test(String(p(row).status || '')))
  const byClient = new Map<string,{paper:number,fabric:number,revenue:number,costs:number,count:number,orders:number,critical:number}>()
  const clientValue = (name: string) => byClient.get(name) || {paper:0,fabric:0,revenue:0,costs:0,count:0,orders:0,critical:0}
  productions.forEach(row => { const x=p(row); const name=text(x.clientName,'Senza cliente'); const value=clientValue(name); value.paper+=Number(x.printedPaperMeters||0); value.fabric+=Number(x.fabricUsedMeters||0); value.revenue+=Number(x.grossRevenue||0); value.costs+=Number(x.materialCostTotal||0); value.count++; byClient.set(name,value) })
  orders.forEach(row => { const name=text(p(row).client,'Senza cliente'); const value=clientValue(name); value.orders++; byClient.set(name,value) })
  criticalTimeline.forEach(row => { const name=text(p(row).clientName,'Senza cliente'); const value=clientValue(name); value.critical++; byClient.set(name,value) })
  for (const row of [...cottonOrders, ...otherWorks]) {
    const x = p(row); const name = text(x.clientName, 'Senza cliente'); const value = clientValue(name)
    const amounts = row.entity_type === 'cotton_order' ? cottonAmounts(x) : otherAmounts(x)
    value.revenue += amounts.revenue; value.costs += amounts.costs; value.orders++; byClient.set(name, value)
  }
  const clients = [...byClient.entries()].sort((a,b) => b[1].paper-a[1].paper)
  const days: string[] = []
  for (let cursor = new Date(`${from}T12:00:00`), end = new Date(`${to}T12:00:00`); cursor <= end && days.length < 93; cursor.setDate(cursor.getDate() + 1)) days.push(cursor.toISOString().slice(0,10))
  const daily = days.map(day => ({ day, paper: productions.filter(row => moduleDate(row) === day).reduce((sum,row) => sum + Number(p(row).printedPaperMeters || 0), 0) }))
  const maxDaily = Math.max(...daily.map(value => value.paper), 1)
  const page = window.open('', '_blank', 'width=1100,height=800')
  if (!page) return
  const footer = (number:number) => `<div class="footer"><span>Spectro ERP · report mensile analitico</span><span>Pagina ${number} · ${new Date().toLocaleString('it-IT')}</span></div>`
  const detailRows = (rows: CloudRecord[], render: (row:CloudRecord)=>string, empty='Nessun dato nel periodo.') => rows.length ? rows.map(render).join('') : `<tr><td colspan="6">${empty}</td></tr>`
  page.document.write(`<!doctype html><html lang="it"><head><meta charset="utf-8"><title>Report mensile analitico Spectro</title><style>*{box-sizing:border-box}body{margin:0;background:#e9e9ed;color:#15151a;font:12px Arial,sans-serif;-webkit-print-color-adjust:exact;print-color-adjust:exact}.page{width:210mm;min-height:297mm;margin:8mm auto;background:#fff;padding:12mm;page-break-after:always}.page:last-of-type{page-break-after:auto}.brand{color:#d31587;font-weight:900;letter-spacing:.13em}header{display:flex;justify-content:space-between;align-items:end;border-top:7px solid #ff7818;border-bottom:2px solid #e819a7;padding:15px 0}h1{margin:4px 0;font-size:27px}h2{margin:20px 0 10px;font-size:18px}.kpis{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:16px 0}.kpi,.panel{border:1px solid #dfe0e5;border-radius:11px;padding:12px}.kpi small{display:block;color:#696b75;text-transform:uppercase;font-size:9px}.kpi strong{display:block;margin-top:7px;font-size:21px}.pink{color:#d31587}.cyan{color:#078ca4}.green{color:#138f54}.orange{color:#d26400}.red{color:#c82b48}.bars{height:125px;display:flex;align-items:end;gap:3px;border-bottom:1px solid #ccc;padding-top:18px}.bars div{flex:1;min-width:3px;text-align:center}.bars i{display:block;min-height:2px;background:linear-gradient(#e819a7,#ff7818);border-radius:3px 3px 0 0}.bars span{display:block;font-size:7px;transform:rotate(-55deg);margin-top:12px;white-space:nowrap}.summary{display:grid;grid-template-columns:1fr 1fr;gap:10px}.summary .panel{margin:0}table{width:100%;border-collapse:collapse}th,td{padding:7px 6px;border-bottom:1px solid #e4e4e8;text-align:left;vertical-align:top}th{font-size:9px;letter-spacing:.07em;color:#747681;text-transform:uppercase;background:#f5f5f7}td:last-child,th:last-child{text-align:right}.note{color:#686b75;font-size:10px;line-height:1.35}.status{font-weight:800}.footer{margin-top:16px;padding-top:8px;border-top:1px solid #ddd;color:#777;display:flex;justify-content:space-between;font-size:9px}.page h2:first-of-type{margin-top:16px}@media print{body{background:#fff}@page{size:A4;margin:0}.page{margin:0}}</style></head><body>
  <main class="page"><header><div><div class="brand">SPECTRO ERP · CONTROLLO DIREZIONALE</div><h1>Report mensile analitico</h1><span>Produzione, economia, ordini, qualità e attività</span></div><b>${formatItalianDate(from)} – ${formatItalianDate(to)}</b></header><section class="kpis"><article class="kpi"><small>Ordini registrati</small><strong>${orders.length}</strong></article><article class="kpi"><small>Ordini evasi</small><strong class="green">${completed.length}</strong></article><article class="kpi"><small>Carta stampata</small><strong class="cyan">${num(paper)} m</strong></article><article class="kpi"><small>Tessuto lavorato</small><strong class="pink">${num(fabric)} m</strong></article><article class="kpi"><small>Produzioni</small><strong>${productions.length}</strong></article><article class="kpi"><small>Eventi negativi</small><strong class="${negativeEvents.length ? 'red':'green'}">${negativeEvents.length}</strong></article>${canFinance ? `<article class="kpi"><small>Fatturato totale</small><strong class="green">${euro(revenue)}</strong></article><article class="kpi"><small>Costi totali reali</small><strong class="orange">${euro(costs)}</strong></article><article class="kpi"><small>Utile netto reale</small><strong class="${netProfit>=0?'green':'red'}">${euro(netProfit)}</strong></article>` : ''}</section><section class="panel"><h2>Metri di carta stampati per giorno</h2><div class="bars">${daily.map(value=>`<div title="${date(value.day)} · ${num(value.paper)} m"><i style="height:${Math.max(2,value.paper/maxDaily*100)}px"></i><span>${new Date(`${value.day}T12:00:00`).toLocaleDateString('it-IT',{day:'2-digit',month:'2-digit'})}</span></div>`).join('')}</div></section>${canFinance?`<h2>Composizione economica completa</h2><div class="summary"><section class="panel"><table><tbody><tr><td>Sublimazione</td><td><b>${euro(productionRevenue)}</b></td></tr><tr><td>Cotone</td><td><b>${euro(cottonRevenue)}</b></td></tr><tr><td>Ulteriori lavorazioni</td><td><b>${euro(otherRevenue)}</b></td></tr><tr><td>Fatturato complessivo</td><td><b class="green">${euro(revenue)}</b></td></tr></tbody></table></section><section class="panel"><table><tbody><tr><td>Materiali e perdite produzione</td><td><b>${euro(productionCosts)}</b></td></tr><tr><td>Costi cotone</td><td><b>${euro(cottonCosts)}</b></td></tr><tr><td>Costi ulteriori lavorazioni</td><td><b>${euro(otherCosts)}</b></td></tr><tr><td>Costi fissi (${fixedDays.length} gg × ${euro(dailyFixedCost)})</td><td><b>${euro(fixedCosts)}</b></td></tr><tr><td>Costi complessivi</td><td><b class="orange">${euro(costs)}</b></td></tr><tr><td>Utile netto reale</td><td><b class="${netProfit>=0?'green':'red'}">${euro(netProfit)}</b></td></tr></tbody></table></section></div>`:''}${footer(1)}</main>
  <main class="page"><header><div><div class="brand">SPECTRO ERP · ANALISI CLIENTI</div><h1>Prestazioni per cliente</h1><span>Volumi, fatturato e criticità del periodo</span></div><b>${clients.length} clienti lavorati</b></header><section class="panel"><table><thead><tr><th>Cliente</th><th>Produzioni</th><th>Ordini</th><th>Carta</th><th>Tessuto</th><th>Criticità</th>${canFinance?'<th>Fatturato</th><th>Margine</th>':''}</tr></thead><tbody>${detailRows(clients.map(([name,value])=>({id:'',module:'',entity_type:'',payload:{name,...value},updated_at:'',revision:0})),row=>{const x=p(row);return `<tr><td><b>${esc(x.name)}</b></td><td>${x.count}</td><td>${x.orders}</td><td><b class="cyan">${num(x.paper)} m</b></td><td>${num(x.fabric)} m</td><td>${x.critical}</td>${canFinance?`<td>${euro(x.revenue)}</td><td><b class="${Number(x.revenue)-Number(x.costs)>=0?'green':'red'}">${euro(Number(x.revenue)-Number(x.costs))}</b></td>`:''}</tr>`})}</tbody></table></section><h2>Produzioni del periodo</h2><section class="panel"><table><thead><tr><th>Data</th><th>Cliente / ordine</th><th>Carta</th><th>Tessuto</th><th>Plotter</th>${canFinance?'<th>Fatturato</th>':''}</tr></thead><tbody>${detailRows([...productions].sort((a,b)=>moduleDate(a).localeCompare(moduleDate(b))),row=>{const x=p(row);return `<tr><td>${date(x.date)}</td><td><b>${esc(text(x.clientName))}</b><div class="note">${esc(text(x.orderRef,'Senza riferimento'))} · ${esc(Array.isArray(x.designs)?x.designs.join(', '):text(x.designs,'Nessun disegno'))}<br>${esc(text(x.technicalNotes,'Nessuna nota tecnica'))}</div></td><td>${num(x.printedPaperMeters)} m</td><td>${num(x.fabricUsedMeters)} m</td><td>${esc(text(x.plotter))}</td>${canFinance?`<td>${euro(x.grossRevenue)}</td>`:''}</tr>`})}</tbody></table></section>${footer(2)}</main>
  <main class="page"><header><div><div class="brand">SPECTRO ERP · ORDINI E QUALITÀ</div><h1>Ordini evasi e criticità</h1><span>Controllo completo del lavoro concluso e delle anomalie</span></div><b>${formatItalianDate(from)} – ${formatItalianDate(to)}</b></header><h2>Ordini completati / evasi</h2><section class="panel"><table><thead><tr><th>Data</th><th>Cliente</th><th>Ordine</th><th>Metri</th><th>Stato</th></tr></thead><tbody>${detailRows(completed,row=>{const x=p(row);return `<tr><td>${date(x.effectiveDeliveryDate||x.dueDate||x.receivedDate)}</td><td><b>${esc(text(x.client))}</b></td><td>${esc(text(x.title,x.orderCode as string))}<div class="note">${esc(text(x.description,'Nessuna nota'))}</div></td><td>${num(x.orderedMeters)} m</td><td class="status green">${esc(label(x.status))}</td></tr>`},'Nessun ordine evaso nel periodo.')}</tbody></table></section><h2>Criticità e anomalie</h2><section class="panel"><table><thead><tr><th>Data</th><th>Cliente / ordine</th><th>Evento</th><th>Gravità</th><th>Stato</th></tr></thead><tbody>${detailRows([...criticalTimeline,...issues].sort((a,b)=>moduleDate(a).localeCompare(moduleDate(b))),row=>{const x=p(row);return `<tr><td>${date(x.date||x.openedDate)}</td><td><b>${esc(text(x.clientName))}</b><div class="note">${esc(text(x.orderRef))}</div></td><td>${esc(text(x.title))}<div class="note">${esc(text(x.description,'Nessuna descrizione'))}</div></td><td class="status red">${esc(label(x.severity))}</td><td>${esc(label(x.status))}</td></tr>`},'Nessuna criticità rilevata nel periodo.')}</tbody></table></section><h2>Attività operative</h2><section class="panel"><table><tbody><tr><td>Attività registrate</td><td><b>${tasks.length}</b></td></tr><tr><td>Attività completate</td><td><b class="green">${completedTasks.length}</b></td></tr><tr><td>Attività ancora aperte</td><td><b class="orange">${tasks.length-completedTasks.length}</b></td></tr></tbody></table></section>${footer(3)}</main>
  <main class="page"><header><div><div class="brand">SPECTRO ERP · REPARTI COMPLEMENTARI</div><h1>Cotone e ulteriori lavorazioni</h1><span>Dettaglio economico e operativo del periodo</span></div><b>${cottonOrders.length + otherWorks.length} registrazioni</b></header><h2>Ordini cotone</h2><section class="panel"><table><thead><tr><th>Data</th><th>Cliente / ordine</th><th>Tessuto</th><th>Metri</th><th>Stato</th>${canFinance?'<th>Margine</th>':''}</tr></thead><tbody>${detailRows(cottonOrders,row=>{const x=p(row);const meters=Number(x.actualProducedMeters||x.orderedMeters||0);return `<tr><td>${date(x.orderDate)}</td><td><b>${esc(text(x.clientName))}</b><div class="note">${esc(text(x.title,x.orderCode as string))}</div></td><td>${esc(text(x.fabricName))}</td><td>${num(meters)} m</td><td>${esc(label(x.status))}</td>${canFinance?`<td>${euro(cottonAmounts(x).revenue-cottonAmounts(x).costs)}</td>`:''}</tr>`},'Nessun ordine cotone nel periodo.')}</tbody></table></section><h2>Ulteriori lavorazioni</h2><section class="panel"><table><thead><tr><th>Data</th><th>Cliente</th><th>Lavorazione</th><th>Quantità</th><th>Stato</th>${canFinance?'<th>Fatturato / utile</th>':''}</tr></thead><tbody>${detailRows(otherWorks,row=>{const x=p(row);const gross=Number(x.manualGrossRevenue||0)||Number(x.quantity||0)*Number(x.unitPrice||0);const workCosts=Number(x.totalCosts||x.materialCosts||0);return `<tr><td>${date(x.date)}</td><td><b>${esc(text(x.clientName))}</b></td><td>${esc(text(x.title))}<div class="note">${esc(text(x.notes,'Nessuna nota'))}</div></td><td>${num(x.quantity)}</td><td>${esc(label(x.status))}</td>${canFinance?`<td>${euro(gross)} / <b class="${gross-workCosts>=0?'green':'red'}">${euro(gross-workCosts)}</b></td>`:''}</tr>`},'Nessuna ulteriore lavorazione nel periodo.')}</tbody></table></section>${footer(4)}</main>
  <main class="page"><header><div><div class="brand">SPECTRO ERP · PERDITE E COSTI FISSI</div><h1>Eventi negativi del periodo</h1><span>Difetti, rimborsi, fermi produttivi e impatto economico</span></div><b>${negativeEvents.length} eventi · ${fixedDays.length} giornate conteggiate</b></header><section class="kpis"><article class="kpi"><small>Eventi negativi</small><strong class="${negativeEvents.length?'red':'green'}">${negativeEvents.length}</strong></article><article class="kpi"><small>Perdite materiali / rimborsi</small><strong class="orange">${euro(negativeEvents.reduce((sum,row)=>sum+Number(p(row).materialCostTotal||0),0))}</strong></article><article class="kpi"><small>Costi fissi del periodo</small><strong class="orange">${euro(fixedCosts)}</strong></article></section><h2>Dettaglio analitico eventi</h2><section class="panel"><table><thead><tr><th>Data</th><th>Cliente / riferimento</th><th>Descrizione e causa</th><th>Rimborso</th><th>Tessuto</th><th>Perdita totale</th></tr></thead><tbody>${detailRows([...negativeEvents].sort((a,b)=>moduleDate(a).localeCompare(moduleDate(b))),row=>{const x=p(row);return `<tr><td>${date(x.date)}${x.applyFixedCosts!==false?'<div class="note">Costo fisso giornata applicato</div>':''}</td><td><b>${esc(text(x.clientName,'Evento interno'))}</b><div class="note">${esc(text(x.orderRef,'Senza riferimento'))}</div></td><td>${esc(text(x.technicalNotes,'Nessuna descrizione'))}</td><td>${euro(x.refundCost)}</td><td>${euro(x.fabricLossCost)}</td><td><b class="red">${euro(x.materialCostTotal)}</b></td></tr>`},'Nessun evento negativo registrato nel periodo.')}</tbody></table></section><h2>Giornate con costi fissi</h2><section class="panel"><table><thead><tr><th>Giorno</th><th>Motivo</th><th>Costo giornaliero</th></tr></thead><tbody>${fixedDays.length?fixedDays.sort().map(day=>`<tr><td><b>${date(day)}</b></td><td>Presenza di produzione o evento con costi fissi attivi</td><td><b>${euro(dailyFixedCost)}</b></td></tr>`).join(''):'<tr><td colspan="3">Nessuna giornata con costi fissi nel periodo.</td></tr>'}</tbody></table></section>${footer(5)}</main><script>window.onload=()=>window.print()<\/script></body></html>`)
  page.document.close()
}

type EditableField = { key: string; label: string; kind?: 'text'|'number'|'date'|'textarea' }
function editableFields(module: string, row: CloudRecord): EditableField[] {
  if (row.entity_type === 'zentha_product') return [{key:'sku',label:'Codice articolo'},{key:'name',label:'Nome prodotto'},{key:'category',label:'Categoria'},{key:'description',label:'Descrizione',kind:'textarea'},{key:'standardCost',label:'Costo standard',kind:'number'},{key:'standardPrice',label:'Prezzo listino',kind:'number'},{key:'priceListName',label:'Nome listino'}]
  if (row.entity_type === 'zentha_order') module = 'zentha'
  const fields: Record<string, EditableField[]> = {
    orders:[{key:'orderCode',label:'Codice ordine'},{key:'client',label:'Cliente'},{key:'title',label:'Titolo'},{key:'status',label:'Stato'},{key:'priority',label:'Priorità'},{key:'receivedDate',label:'Data ordine',kind:'date'},{key:'materialReceivedDate',label:'Consegna materiale',kind:'date'},{key:'materialReceiptNotes',label:'Note consegna materiale',kind:'textarea'},{key:'dueDate',label:'Consegna ordine',kind:'date'},{key:'orderedMeters',label:'Metri ordinati',kind:'number'},{key:'designCode',label:'Codice disegno'},{key:'description',label:'Descrizione',kind:'textarea'}],
    production:[{key:'date',label:'Data',kind:'date'},{key:'clientName',label:'Cliente'},{key:'orderRef',label:'Riferimento ordine'},{key:'zenthaOrderId',label:'ID ordine Zentha collegato',kind:'number'},{key:'plotter',label:'Plotter'},{key:'printedPaperMeters',label:'Carta stampata (m)',kind:'number'},{key:'protectionPaperMeters',label:'Carta protezione (m)',kind:'number'},{key:'fabricUsedMeters',label:'Tessuto usato (m)',kind:'number'},{key:'fabricDisplayName',label:'Tessuto'},{key:'technicalNotes',label:'Note tecniche',kind:'textarea'},{key:'productionRating',label:'Valutazione produzione (1-5)',kind:'number'},{key:'productionCriticality',label:'Criticità (NESSUNA/BASSA/MEDIA/ALTA/CRITICA)'},{key:'productionProblems',label:'Problemi rilevati',kind:'textarea'},{key:'productionReview',label:'Valutazione finale',kind:'textarea'}],
    inventory:[{key:'fabricName',label:'Tessuto'},{key:'ownerName',label:'Proprietario'},{key:'quantityMeters',label:'Disponibilità (m)',kind:'number'},{key:'minimumQuantityMeters',label:'Soglia minima (m)',kind:'number'},{key:'supplierName',label:'Fornitore'},{key:'composition',label:'Composizione'},{key:'variant',label:'Variante'},{key:'lotCode',label:'Lotto'},{key:'purchasePricePerMeter',label:'Costo acquisto / m',kind:'number'},{key:'processingCostPerMeter',label:'Lavorazioni / m',kind:'number'},{key:'defaultSalePricePerMeter',label:'Vendita predefinita / m',kind:'number'}],
    clients:[{key:'name',label:'Ragione sociale / nome'},{key:'email',label:'Email'},{key:'phone',label:'Telefono'},{key:'address',label:'Indirizzo'},{key:'notes',label:'Note',kind:'textarea'}],
    cotton:[{key:'orderCode',label:'Codice ordine'},{key:'clientName',label:'Cliente'},{key:'fabricName',label:'Tessuto'},{key:'orderedMeters',label:'Metri ordinati',kind:'number'},{key:'actualProducedMeters',label:'Metri prodotti',kind:'number'},{key:'dueDate',label:'Consegna prevista',kind:'date'},{key:'completedDate',label:'Data effettiva di evasione',kind:'date'},{key:'status',label:'Stato'},{key:'notes',label:'Note',kind:'textarea'}],
    zentha:[{key:'orderCode',label:'Codice ordine'},{key:'clientName',label:'Cliente'},{key:'sport',label:'Sport (BASKET/CALCIO_VOLLEY/DANZA/MULTISPORT)'},{key:'audience',label:'Destinatario (UOMO/DONNA/BAMBINO/MISTO)'},{key:'priority',label:'Priorità'},{key:'blockerReason',label:'Motivo blocco / urgenza',kind:'textarea'},{key:'orderDate',label:'Data ordine',kind:'date'},{key:'dueDate',label:'Consegna',kind:'date'},{key:'status',label:'Stato'},{key:'chromiaRatePerMeter',label:'Tariffa Chromia / m',kind:'number'},{key:'notes',label:'Note',kind:'textarea'}],
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
  onUpdate?: (row: CloudRecord, patch: Record<string, unknown>) => Promise<string | null>,
  onCreateZentha?: (order: Record<string, unknown>, items: Array<Record<string, unknown>>) => Promise<string | null>,
  onCreateOrder?: (order: Record<string, unknown>) => Promise<string | null>,
  onCreateProduction?: (production: Record<string, unknown>) => Promise<string | null>,
  onCreateCotton?: (order: Record<string, unknown>) => Promise<string | null>
) {
  const types = primaryTypes[module.key] || []
  const primary = records.filter(row => types.includes(row.entity_type))
  const rowStatus = (row: CloudRecord) => p(row).archived === true ? 'ARCHIVED' : String(p(row).status || (p(row).paid === true ? 'PAID' : 'OPEN'))
  const products = module.key === 'zentha' ? records.filter(row => row.entity_type === 'zentha_product') : []
  const clients = records.filter(row => row.entity_type === 'client').sort((a,b) => text(p(a).name).localeCompare(text(p(b).name),'it'))
  const workOrders = records.filter(row => row.entity_type === 'work_order' && !terminalStatus.test(String(p(row).status || '')))
  const today = new Date().toISOString().slice(0, 10)
  let entireHistory = ['zentha', 'clients'].includes(module.key)
  let rangeStart = `${today.slice(0, 7)}-01`
  let rangeEnd = today
  const statuses = [...new Set(primary.map(row => String(p(row).status || (p(row).paid === true ? 'PAID' : p(row).paid === false ? 'OPEN' : ''))).filter(Boolean))].sort()
  const searchPlaceholder: Record<string, string> = {
    orders: 'Cerca ordine, cliente o disegno…', production: 'Cerca cliente, ordine, plotter o tessuto…',
    inventory: 'Cerca tessuto, lotto, collo o proprietario…', clients: 'Cerca cliente…', cotton: 'Cerca ordine, cliente o tessuto…',
    zentha: 'Cerca ordine, cliente, articolo o codice…', finance: 'Cerca cliente, scadenza o lavorazione…',
    tasks: 'Cerca attività, cliente o descrizione…', issues: 'Cerca criticità, cliente o ordine…'
  }
  let zenthaLineSequence = 0
  const zenthaCreateLine = () => {
    const index = zenthaLineSequence++
    return `<article class="zentha-create-line" data-line="${index}"><div class="zentha-line-heading"><strong>Prodotto ${index + 1}</strong><button type="button" data-remove-zentha-line="${index}" aria-label="Rimuovi prodotto">×</button></div><label>Articolo<select name="productId" required><option value="">Seleziona prodotto…</option>${products.map(product => `<option value="${esc(product.id)}">${esc(text(p(product).sku, 'S/C'))} · ${esc(text(p(product).name, 'Prodotto'))}</option>`).join('')}</select></label><div class="edit-field-row"><label>Quantità<input name="quantity" type="number" min="1" step="1" value="1" required></label>${canFinance ? '<label>Prezzo unitario €<input name="unitPrice" type="number" min="0" step="0.01"></label><label>Costo unitario €<input name="unitCost" type="number" min="0" step="0.01"></label>' : ''}</div><div class="edit-field-row"><label>Variante<input name="variant" type="text" placeholder="Es. uomo double"></label><label>Colore<input name="color" type="text"></label></div><label>Taglie e quantità<input name="sizeBreakdown" type="text" placeholder="S×4, M×8, L×6…"></label><label>Personalizzazioni<textarea name="personalization" placeholder="Nomi, numeri, sponsor, loghi…"></textarea></label><label>Note produzione<textarea name="productionNotes" placeholder="Indicazioni per stampa e confezionamento"></textarea></label></article>`
  }
  root.innerHTML = `<div class="analytical-page">
    <header class="module-header compact-header"><div><button id="home" class="back">← Dashboard</button><p class="kicker">${esc(module.eyebrow)}</p><h1>${esc(module.title)}</h1><p>${esc(module.description)}</p></div><div class="header-actions">${module.key === 'orders' && canManage && onCreateOrder ? '<button id="new-work-order" class="primary compact-action">＋ Nuovo ordine</button>' : ''}${module.key === 'production' && canManage && onCreateProduction ? '<button id="new-production" class="primary compact-action">＋ Nuova produzione</button>' : ''}${module.key === 'cotton' && canManage && onCreateCotton ? '<button id="new-cotton-order" class="primary compact-action">＋ Nuovo ordine cotone</button>' : ''}${module.key === 'zentha' && canManage && onCreateZentha ? '<button id="new-zentha-order" class="primary compact-action">＋ Nuovo ordine Zentha</button>' : ''}${canManage ? '<span class="permission-chip manage">● Gestione autorizzata</span>' : '<span class="permission-chip">Sola visualizzazione</span>'}<div class="live"><i></i>${primary.length} elementi</div></div></header>
    ${errorMessage ? `<p class="form-message">${esc(errorMessage)}</p>` : ''}
    ${module.key === 'tasks' ? '<div id="task-focus"></div>' : ''}
    ${module.key === 'orders' ? '<div id="order-focus"></div>' : ''}
    <section class="metric-grid" id="module-metrics">${statistics(module.key, primary.filter(row => moduleDate(row) >= rangeStart && moduleDate(row) <= rangeEnd), records, canFinance)}</section>
    <div id="module-insights">${moduleInsights(primary.filter(row => moduleDate(row) >= rangeStart && moduleDate(row) <= rangeEnd))}</div>
    ${module.key === 'production' ? `<div id="production-analysis">${productionAnalytics(primary.filter(row => moduleDate(row) >= rangeStart && moduleDate(row) <= rangeEnd))}</div>` : ''}
    <section class="data-panel"><div class="data-toolbar"><div><p class="kicker">${module.key === 'tasks' ? 'ARCHIVIO E PIANIFICAZIONE' : 'CENTRO OPERATIVO'}</p><h2>${esc(module.title)}</h2></div><div class="toolbar-actions">${canExport ? '<button id="open-export-center" class="primary compact-action">Centro esportazioni</button><button id="quick-csv" class="secondary compact-action">CSV rapido</button>' : '<span class="permission-chip">Esportazione non autorizzata</span>'}</div></div>
      <div class="view-tabs" role="tablist"><button class="${module.key === 'tasks' ? '' : 'active'}" data-view="all">Tutti <b>${primary.length}</b></button><button class="${module.key === 'tasks' ? 'active' : ''}" data-view="operative">Operativi <b>${primary.filter(row => !terminalStatus.test(rowStatus(row))).length}</b></button><button data-view="attention">Da attenzionare <b>${primary.filter(row => isOverdue(row) || /HIGH|URGENT|CRITICAL|BLOCKING/.test(String(p(row).priority || p(row).severity || rowStatus(row)))).length}</b></button><button data-view="archive">Archivio <b>${primary.filter(row => terminalStatus.test(rowStatus(row))).length}</b></button></div>
      <div class="advanced-filters"><label class="search-box">⌕<input id="record-search" type="search" placeholder="${esc(searchPlaceholder[module.key] || 'Cerca nei dati…')}"></label>${statuses.length ? `<select id="status-filter" aria-label="Filtra per stato"><option value="">Tutti gli stati</option>${statuses.map(status => `<option value="${esc(status)}">${esc(label(status))}</option>`).join('')}</select>` : ''}<select id="period-filter" aria-label="Scegli periodo"><option value="all" ${entireHistory ? 'selected' : ''}>Tutto lo storico</option><option value="current_month" ${entireHistory ? '' : 'selected'}>Mese corrente</option><option value="previous_month">Mese precedente</option><option value="7">Ultimi 7 giorni</option><option value="30">Ultimi 30 giorni</option><option value="90">Ultimi 90 giorni</option><option value="custom">Personalizzato</option></select><label class="date-filter">Dal<input id="period-from" type="date" value="${entireHistory ? '' : rangeStart}"></label><label class="date-filter">Al<input id="period-to" type="date" value="${entireHistory ? '' : rangeEnd}"></label><select id="sort-filter" aria-label="Ordina risultati"><option value="date_desc">Data: più recenti prima</option><option value="date_asc">Data: meno recenti prima</option><option value="updated_desc">Ultima modifica</option><option value="client_asc">Cliente: A–Z</option></select><select id="group-filter" aria-label="Raggruppa risultati"><option value="none">Nessun raggruppamento</option><option value="date">Raggruppa per mese</option><option value="status">Raggruppa per stato</option><option value="client">Raggruppa per cliente</option></select></div>
      <div class="table-wrap"><table class="module-table ${esc(module.key)}"><thead><tr>${(columns[module.key] || ['Record']).map(value => `<th>${esc(value)}</th>`).join('')}</tr></thead><tbody id="record-body"></tbody></table></div>
      <div id="record-empty" class="table-empty" hidden>Nessun elemento corrisponde ai filtri.</div>
      <footer class="table-footer"><span id="result-count"></span><small>Seleziona una riga per aprire il dossier completo</small></footer>
    </section>
    ${module.key === 'zentha' ? zenthaCatalog(products, canFinance) : ''}
    <div id="detail-overlay" class="detail-overlay" hidden><button class="detail-scrim" aria-label="Chiudi"></button><aside class="detail-drawer"><header><div><p class="kicker">DOSSIER COMPLETO</p><h2 id="detail-title"></h2></div><button id="detail-close" aria-label="Chiudi">×</button></header><nav id="detail-tabs" class="dossier-tabs"></nav><div id="detail-actions" class="detail-actions"></div><div id="detail-content"></div></aside></div>
    <div id="export-overlay" class="export-overlay" hidden><button class="export-scrim" aria-label="Chiudi"></button><section class="export-center"><header><div><p class="kicker">CENTRO ESPORTAZIONI</p><h2>Prepara il documento</h2><p>Il report userà il periodo e i filtri attualmente selezionati.</p></div><button id="export-close" aria-label="Chiudi">×</button></header>${module.key === 'production' ? `<div class="export-options"><label class="export-choice"><input type="radio" name="report-profile" value="executive" checked><span><b>Report direzionale</b><small>KPI, metri per cliente, dati economici autorizzati e dettaglio produzioni.</small></span></label><label class="export-choice"><input type="radio" name="report-profile" value="operations"><span><b>Report operativo</b><small>Produzioni, ordini, disegni, carta, tessuto e note senza prezzi.</small></span></label><label class="export-choice"><input type="radio" name="report-profile" value="complete"><span><b>Analisi completa</b><small>Documento analitico con indicatori economici per amministrazione.</small></span></label></div>` : `<div class="export-options"><div class="export-choice static"><span><b>Elenco filtrato ${esc(module.title)}</b><small>${primary.length} elementi disponibili prima dei filtri.</small></span></div></div>`}<div class="export-summary"><span>Periodo</span><strong id="export-period-label">${formatItalianDate(rangeStart)} – ${formatItalianDate(rangeEnd)}</strong><span>Elementi</span><strong id="export-count-label">0</strong></div><footer><button id="export-csv" class="secondary">Scarica Excel / CSV</button><button id="generate-report" class="primary">${module.key === 'production' ? 'Apri PDF professionale' : 'Stampa elenco filtrato'}</button></footer></section></div>
    <div id="edit-overlay" class="export-overlay" hidden><button class="edit-scrim export-scrim" aria-label="Chiudi"></button><form id="edit-form" class="export-center edit-center"><header><div><p class="kicker">MODIFICA RECORD</p><h2 id="edit-title">Aggiorna dati</h2><p>La modifica viene salvata nel cloud come livello protetto.</p></div><button id="edit-close" type="button" aria-label="Chiudi">×</button></header><div id="edit-fields" class="edit-fields"></div><p id="edit-message" class="form-message" hidden></p><footer><button id="edit-cancel" type="button" class="secondary">Annulla</button><button id="edit-save" type="submit" class="primary">Salva modifiche</button></footer></form></div>
    ${module.key === 'orders' && canManage && onCreateOrder ? `<div id="work-create-overlay" class="export-overlay" hidden><button class="work-create-scrim export-scrim" aria-label="Chiudi"></button><form id="work-create-form" class="export-center edit-center operational-create-center"><header><div><p class="kicker">NUOVO ORDINE SPECTRO</p><h2>Crea una commessa produttiva</h2><p>Scegli un cliente registrato per applicare il prezzo predefinito, che resta sempre modificabile.</p></div><button id="work-create-close" type="button" aria-label="Chiudi">×</button></header><div class="edit-fields"><div class="edit-field-row"><label>Cliente registrato<select name="clientId"><option value="">Cliente manuale…</option>${clients.map(row=>`<option value="${esc(p(row).id)}" data-name="${esc(p(row).name)}" data-price="${esc(p(row).defaultPrintPrice||0)}">${esc(text(p(row).name))} · ${euro(p(row).defaultPrintPrice||0)}/m</option>`).join('')}</select></label><label>Cliente manuale<input name="client" required placeholder="Seleziona sopra o scrivi qui"></label><label>Titolo / riferimento<input name="title" required placeholder="Nome commessa"></label></div><div class="edit-field-row"><label>Data ordine<input name="receivedDate" type="date" value="${today}" required></label><label>Consegna materiale<input name="materialReceivedDate" type="date"></label><label>Consegna ordine<input name="dueDate" type="date"></label></div><div class="edit-field-row"><label>Stato<select name="status"><option value="RECEIVED">Ricevuto</option><option value="IN_PROCESS">In lavorazione</option><option value="WAITING_MATERIAL">Attesa materiale</option><option value="WAITING_CLIENT">Attesa cliente</option><option value="IN_PRODUCTION">In produzione</option><option value="ON_HOLD">In pausa</option></select></label><label>Priorità<select name="priority"><option value="NORMAL">Normale</option><option value="HIGH">Alta</option></select></label><label>Blocco<select name="blocker"><option value="NONE">Nessuno</option><option value="WAITING_MATERIAL">Attesa materiale</option><option value="WAITING_CLIENT">Attesa cliente</option></select></label></div><div class="edit-field-row"><label>Metri ordinati<input name="orderedMeters" type="number" min="0" step="0.01"></label><label>Codice disegno<input name="designCode"></label>${canFinance ? '<label>Prezzo stampa €/m<input name="printPricePerMeter" type="number" min="0" step="0.01"></label>' : ''}</div><label>Descrizione e note operative<textarea name="description" placeholder="Disegni, colori, lavorazioni, richieste del cliente…"></textarea></label><label>Note consegna materiale<textarea name="materialReceiptNotes" placeholder="Pezzi, colli o materiale ricevuto…"></textarea></label></div><p id="work-create-message" class="form-message" hidden></p><footer><button id="work-create-cancel" type="button" class="secondary">Annulla</button><button id="work-create-save" type="submit" class="primary">Crea e sincronizza ordine</button></footer></form></div>` : ''}
    ${module.key === 'production' && canManage && onCreateProduction ? `<div id="production-create-overlay" class="export-overlay" hidden><button class="production-create-scrim export-scrim" aria-label="Chiudi"></button><form id="production-create-form" class="export-center edit-center operational-create-center"><header><div><p class="kicker">NUOVA REGISTRAZIONE</p><h2>Aggiungi produzione o evento negativo</h2><p>I calcoli economici usano automaticamente i costi impostati su Android.</p></div><button id="production-create-close" type="button" aria-label="Chiudi">×</button></header><div class="edit-fields"><div class="edit-field-row"><label>Tipo<select name="type"><option value="PRODUCTION">Produzione</option><option value="NEGATIVE">Evento negativo / perdita</option></select></label><label>Data<input name="date" type="date" value="${today}" required></label><label>Cliente registrato<select name="clientId"><option value="">Cliente manuale…</option>${clients.map(row=>`<option value="${esc(p(row).id)}" data-name="${esc(p(row).name)}" data-price="${esc(p(row).defaultPrintPrice||0)}">${esc(text(p(row).name))}</option>`).join('')}</select></label></div><div class="edit-field-row"><label>Cliente manuale<input name="clientName" required placeholder="Cliente o evento interno"></label><label>Ordine collegato<select name="workOrderId"><option value="">Nessun ordine</option>${workOrders.map(row=>`<option value="${esc(p(row).id)}" data-client="${esc(p(row).client)}" data-ref="${esc(p(row).title||p(row).orderCode)}" data-price="${esc(p(row).printPricePerMeter||0)}">${esc(text(p(row).client))} · ${esc(text(p(row).title,p(row).orderCode as string))}</option>`).join('')}</select></label><label>Riferimento ordine<input name="orderRef" placeholder="Commessa o lavorazione"></label></div><div class="edit-field-row"><label>Plotter<select name="plotter"><option value="X1">X1</option><option value="X2">X2</option></select></label><label>Carta stampata (m)<input name="printedPaperMeters" type="number" min="0" step="0.01"></label><label>Prezzo stampa €/m<input name="printPricePerMeter" type="number" min="0" step="0.01"></label></div><div class="edit-field-row"><label>Carta protezione (m)<input name="protectionPaperMeters" type="number" min="0" step="0.01"></label><label>Sfrido carta (m)<input name="paperWasteMeters" type="number" min="0" step="0.01"></label><label>Disegni<input name="designs" placeholder="Separati da virgola"></label></div><div class="edit-field-row"><label>Tessuto / materiale<input name="manualFabricName" placeholder="Facoltativo"></label><label>Tessuto lavorato (m)<input name="fabricUsedMeters" type="number" min="0" step="0.01"></label><label>Costo tessuto €/m<input name="fabricCostPerMeter" type="number" min="0" step="0.01"></label></div><div class="edit-field-row negative-costs"><label>Rimborso / perdita €<input name="refundCost" type="number" min="0" step="0.01"></label><label>Costo tessuto difettato €<input name="fabricLossCost" type="number" min="0" step="0.01"></label><label class="check-field"><input name="applyFixedCosts" type="checkbox" checked> Applica costi fissi alla giornata</label></div><label>Note tecniche o descrizione evento<textarea name="technicalNotes" required placeholder="Lavorazione, difetto, metri coinvolti, causa e conseguenze economiche…"></textarea></label></div><p id="production-create-message" class="form-message" hidden></p><footer><button id="production-create-cancel" type="button" class="secondary">Annulla</button><button id="production-create-save" type="submit" class="primary">Registra e sincronizza</button></footer></form></div>` : ''}
    ${module.key === 'cotton' && canManage && onCreateCotton ? `<div id="cotton-create-overlay" class="export-overlay" hidden><button class="cotton-create-scrim export-scrim" aria-label="Chiudi"></button><form id="cotton-create-form" class="export-center edit-center operational-create-center"><header><div><p class="kicker">NUOVO ORDINE COTONE</p><h2>Registra la commessa</h2><p>I campi obbligatori vengono controllati prima del salvataggio.</p></div><button id="cotton-create-close" type="button" aria-label="Chiudi">×</button></header><div class="edit-fields"><div class="edit-field-row"><label>Cliente registrato<select name="registeredClient"><option value="">Cliente manuale…</option>${clients.map(row=>`<option value="${esc(p(row).name)}">${esc(text(p(row).name))}</option>`).join('')}</select></label><label>Cliente<input name="clientName" required></label><label>Titolo / riferimento<input name="title" required></label></div><div class="edit-field-row"><label>Data ordine<input name="orderDate" type="date" value="${today}" required></label><label>Consegna<input name="dueDate" type="date"></label><label>Stato<select name="status"><option value="IN_PROCESS">In lavorazione</option><option value="WAITING_MATERIAL">Attesa materiale</option><option value="IN_PRODUCTION">In produzione</option><option value="COMPLETED">Completato</option><option value="DELIVERED">Consegnato</option></select></label></div><div class="edit-field-row"><label>Tessuto<input name="fabricName" required></label><label>Tipo stampa<select name="printType"><option value="REACTIVE">Reattiva</option><option value="PIGMENT">Pigmento</option><option value="SUBLIMATION">Sublimazione</option><option value="OTHER">Altro</option></select></label><label>Metri ordinati<input name="orderedMeters" type="number" min="0.01" step="0.01" required></label></div>${canFinance ? '<div class="edit-field-row"><label>Costo produzione €/m<input name="productionCostPerMeter" type="number" min="0" step="0.01" value="0"></label><label>Prezzo vendita €/m<input name="printPricePerMeter" type="number" min="0" step="0.01" value="0"></label><label>Costo tessuto €/m<input name="fabricCostPerMeter" type="number" min="0" step="0.01" value="0"></label></div>' : ''}<label>Note operative<textarea name="notes" placeholder="Colori, disegni, lavorazioni, consegna…"></textarea></label></div><p id="cotton-create-message" class="form-message" hidden></p><footer><button id="cotton-create-cancel" type="button" class="secondary">Annulla</button><button id="cotton-create-save" type="submit" class="primary">Crea e sincronizza ordine</button></footer></form></div>` : ''}
    ${module.key === 'zentha' && canManage && onCreateZentha ? `<div id="zentha-create-overlay" class="export-overlay" hidden><button class="zentha-create-scrim export-scrim" aria-label="Chiudi"></button><form id="zentha-create-form" class="export-center edit-center zentha-create-center"><header><div><p class="kicker">NUOVO ORDINE ZENTHA</p><h2>Crea una commessa completa</h2><p>Il nuovo ordine sarà visibile subito sul web e acquisito automaticamente da Android.</p></div><button id="zentha-create-close" type="button" aria-label="Chiudi">×</button></header><div class="edit-fields"><div class="edit-field-row"><label>Cliente / società<input name="clientName" required placeholder="Nome cliente manuale"></label><label>Stato<select name="status"><option value="CONFIRMED">Confermato</option><option value="DRAFT">Bozza</option><option value="GRAPHICS">Grafica</option><option value="WAITING_APPROVAL">Attesa approvazione</option><option value="PRODUCTION">In produzione</option></select></label></div><div class="edit-field-row"><label>Data ordine<input name="orderDate" type="date" value="${today}" required></label><label>Consegna facoltativa<input name="dueDate" type="date"></label></div><label>Note ordine<textarea name="notes" placeholder="Riferimenti, richieste e indicazioni generali"></textarea></label>${canFinance ? '<div class="edit-field-row"><label>Quota A %<input name="splitPercentA" type="number" value="50" min="0" max="100" step="0.01"></label><label>Quota B %<input name="splitPercentB" type="number" value="50" min="0" max="100" step="0.01"></label><label>Tariffa Chromia €/m<input name="chromiaRatePerMeter" type="number" value="2.5" min="0" step="0.01"></label></div>' : ''}<div class="zentha-create-products"><div class="zentha-products-title"><div><p class="kicker">PRODOTTI</p><h3>Articoli dell’ordine</h3></div><button id="add-zentha-line" type="button" class="secondary">＋ Aggiungi prodotto</button></div><div id="zentha-create-lines">${zenthaCreateLine()}</div></div></div><p id="zentha-create-message" class="form-message" hidden></p><footer><button id="zentha-create-cancel" type="button" class="secondary">Continua più tardi</button><button id="zentha-create-save" type="submit" class="primary">Crea ordine Zentha</button></footer></form></div>` : ''}
  </div>`
  const zenthaForm = root.querySelector<HTMLFormElement>('#zentha-create-form')
  if (zenthaForm) {
    const content = zenthaForm.querySelector<HTMLElement>('.edit-fields')!
    content.innerHTML = `<nav class="zentha-wizard-progress" aria-label="Avanzamento ordine">${['Cliente','Sport','Per chi','Kit e modello','Taglie','Riepilogo'].map((value,index)=>`<button type="button" data-z-step-label="${index+1}" class="${index===0?'active':''}"><b>${index+1}</b><span>${value}</span></button>`).join('')}</nav>
      <section class="zentha-wizard-step active" data-z-step="1"><p class="kicker">1 · CLIENTE</p><h3>Per chi stai creando l’ordine?</h3><div class="edit-field-row"><label>Cliente registrato<select name="registeredClient"><option value="">Cliente manuale…</option>${clients.map(row=>`<option value="${esc(p(row).name)}">${esc(text(p(row).name))}</option>`).join('')}</select></label><label>Cliente / società<input name="clientName" required placeholder="Società sportiva o cliente"></label></div><div class="edit-field-row"><label>Data ordine<input name="orderDate" type="date" value="${today}" required></label><label>Consegna facoltativa<input name="dueDate" type="date"></label><label>Priorità<select name="priority"><option value="NORMAL">Normale</option><option value="HIGH">Alta</option><option value="URGENT">Urgente</option></select></label></div></section>
      <section class="zentha-wizard-step" data-z-step="2"><p class="kicker">2 · SPORT</p><h3>Che sport?</h3><div class="zentha-choice-grid">${[['BASKET','Basket','Canotte e completi'],['CALCIO_VOLLEY','Calcio / Volley','Maglie, pantaloncini e kit'],['DANZA','Danza','Body, gonne e completi'],['MULTISPORT','Multisport','Catalogo completo']].map(([value,title,caption])=>`<label><input type="radio" name="sport" value="${value}" ${value==='MULTISPORT'?'checked':''}><span><b>${title}</b><small>${caption}</small></span></label>`).join('')}</div></section>
      <section class="zentha-wizard-step" data-z-step="3"><p class="kicker">3 · DESTINATARIO</p><h3>Per chi?</h3><div class="zentha-choice-grid audience">${[['UOMO','Uomo'],['DONNA','Donna'],['BAMBINO','Bambino / Junior'],['MISTO','Squadra mista']].map(([value,title])=>`<label><input type="radio" name="audience" value="${value}" ${value==='MISTO'?'checked':''}><span><b>${title}</b><small>Filtra modelli e vestibilità</small></span></label>`).join('')}</div></section>
      <section class="zentha-wizard-step" data-z-step="4"><div class="zentha-products-title"><div><p class="kicker">4 · KIT E MODELLO</p><h3>Seleziona i prodotti</h3><p>Prezzi e costi vengono congelati sull’ordine.</p></div><button id="add-zentha-line" type="button" class="secondary">Aggiungi prodotto</button></div><div id="zentha-create-lines">${zenthaCreateLine()}</div></section>
      <section class="zentha-wizard-step" data-z-step="5"><p class="kicker">5 · TAGLIE E PERSONALIZZAZIONI</p><h3>Quante per taglia?</h3><div class="size-stepper-grid">${['XS','S','M','L','XL','XXL','3XL'].map(size=>`<div data-size="${size}"><strong>${size}</strong><span><button type="button" data-size-change="-1">−</button><b>0</b><button type="button" data-size-change="1">+</button></span></div>`).join('')}</div><input name="globalSizeBreakdown" type="hidden"><label>Personalizzazioni<textarea name="globalPersonalization" placeholder="Nomi, numeri, sponsor, loghi, colori…"></textarea></label><label>Note per la produzione<textarea name="globalProductionNotes" placeholder="Stampa, confezionamento, abbinamenti e controlli qualità…"></textarea></label></section>
      <section class="zentha-wizard-step" data-z-step="6"><p class="kicker">6 · CONTROLLO</p><h3>Controlla e crea</h3><div id="zentha-wizard-summary" class="zentha-wizard-summary"></div><div class="edit-field-row"><label>Stato iniziale<select name="status"><option value="CONFIRMED">Ricevuto / confermato</option><option value="DRAFT">Bozza</option><option value="GRAPHICS">Grafica</option><option value="WAITING_APPROVAL">Attesa approvazione</option><option value="PRODUCTION">In produzione</option></select></label><label>Motivo blocco<input name="blockerReason" placeholder="Facoltativo"></label></div><label>Note ordine<textarea name="notes" placeholder="Riferimenti, urgenze e indicazioni generali"></textarea></label>${canFinance ? '<div class="edit-field-row"><label>Quota A %<input name="splitPercentA" type="number" value="50" min="0" max="100" step="0.01"></label><label>Quota B %<input name="splitPercentB" type="number" value="50" min="0" max="100" step="0.01"></label><label>Tariffa Chromia €/m<input name="chromiaRatePerMeter" type="number" value="2.5" min="0" step="0.01"></label></div>' : ''}</section>`
    const footer = zenthaForm.querySelector<HTMLElement>('footer')!
    footer.innerHTML = '<button id="zentha-create-cancel" type="button" class="secondary">Salva bozza e chiudi</button><span class="wizard-spacer"></span><button id="zentha-step-back" type="button" class="secondary" hidden>Indietro</button><button id="zentha-step-next" type="button" class="primary">Avanti</button><button id="zentha-create-save" type="submit" class="primary" hidden>Crea ordine</button>'
  }
  const body = root.querySelector<HTMLTableSectionElement>('#record-body')!
  const empty = root.querySelector<HTMLElement>('#record-empty')!
  const search = root.querySelector<HTMLInputElement>('#record-search')!
  const status = root.querySelector<HTMLSelectElement>('#status-filter')
  const period = root.querySelector<HTMLSelectElement>('#period-filter')!
  const periodFrom = root.querySelector<HTMLInputElement>('#period-from')!
  const periodTo = root.querySelector<HTMLInputElement>('#period-to')!
  const sort = root.querySelector<HTMLSelectElement>('#sort-filter')!
  const group = root.querySelector<HTMLSelectElement>('#group-filter')!
  const resultCount = root.querySelector<HTMLElement>('#result-count')!
  let currentView = module.key === 'tasks' ? 'operative' : 'all'
  let visibleRows: CloudRecord[] = []
  let editingRow: CloudRecord | null = null
  const editOverlay = root.querySelector<HTMLElement>('#edit-overlay')!
  const closeEdit = () => { editOverlay.hidden = true; editingRow = null; document.body.classList.remove('drawer-open') }
  const workCreateOverlay = root.querySelector<HTMLElement>('#work-create-overlay')
  const closeWorkCreate = () => { if (workCreateOverlay) workCreateOverlay.hidden = true; document.body.classList.remove('drawer-open') }
  root.querySelector('#new-work-order')?.addEventListener('click', () => { if (workCreateOverlay) { workCreateOverlay.hidden = false; document.body.classList.add('drawer-open') } })
  root.querySelector('#work-create-close')?.addEventListener('click', closeWorkCreate)
  root.querySelector('#work-create-cancel')?.addEventListener('click', closeWorkCreate)
  root.querySelector('.work-create-scrim')?.addEventListener('click', closeWorkCreate)
  root.querySelector<HTMLSelectElement>('#work-create-form [name="clientId"]')?.addEventListener('change', event => {
    const option = (event.currentTarget as HTMLSelectElement).selectedOptions[0]
    const clientInput = root.querySelector<HTMLInputElement>('#work-create-form [name="client"]')
    const priceInput = root.querySelector<HTMLInputElement>('#work-create-form [name="printPricePerMeter"]')
    if (option?.value && clientInput) clientInput.value = option.dataset.name || ''
    if (option?.value && priceInput) priceInput.value = option.dataset.price || '0'
  })
  root.querySelector<HTMLFormElement>('#work-create-form')?.addEventListener('submit', async event => {
    event.preventDefault(); if (!onCreateOrder) return
    const form = new FormData(event.currentTarget as HTMLFormElement)
    const order: Record<string, unknown> = {
      clientId:Number(form.get('clientId')||0)||null, client:String(form.get('client')||'').trim(), title:String(form.get('title')||'').trim(), receivedDate:String(form.get('receivedDate')||today),
      materialReceivedDate:String(form.get('materialReceivedDate')||''), dueDate:String(form.get('dueDate')||''), status:String(form.get('status')||'RECEIVED'),
      priority:String(form.get('priority')||'NORMAL'), blocker:String(form.get('blocker')||'NONE'), orderedMeters:Number(form.get('orderedMeters')||0),
      designCode:String(form.get('designCode')||'').trim(), printPricePerMeter:canFinance ? Number(form.get('printPricePerMeter')||0) : 0,
      description:String(form.get('description')||'').trim(), materialReceiptNotes:String(form.get('materialReceiptNotes')||'').trim()
    }
    const message = root.querySelector<HTMLElement>('#work-create-message')!; const save = root.querySelector<HTMLButtonElement>('#work-create-save')!
    if (!order.client || !order.title) { message.textContent='Inserisci cliente e titolo della commessa.'; message.hidden=false; return }
    save.disabled=true; save.textContent='Creazione e sincronizzazione…'; const error = await onCreateOrder(order)
    if (error) { message.textContent=error; message.hidden=false; save.disabled=false; save.textContent='Crea e sincronizza ordine' }
  })
  const productionCreateOverlay = root.querySelector<HTMLElement>('#production-create-overlay')
  const closeProductionCreate = () => { if (productionCreateOverlay) productionCreateOverlay.hidden=true; document.body.classList.remove('drawer-open') }
  root.querySelector('#new-production')?.addEventListener('click', () => { if (productionCreateOverlay) { productionCreateOverlay.hidden=false; document.body.classList.add('drawer-open') } })
  root.querySelector('#production-create-close')?.addEventListener('click', closeProductionCreate)
  root.querySelector('#production-create-cancel')?.addEventListener('click', closeProductionCreate)
  root.querySelector('.production-create-scrim')?.addEventListener('click', closeProductionCreate)
  root.querySelector<HTMLSelectElement>('#production-create-form [name="clientId"]')?.addEventListener('change', event => {
    const option=(event.currentTarget as HTMLSelectElement).selectedOptions[0]
    const name=root.querySelector<HTMLInputElement>('#production-create-form [name="clientName"]')
    const price=root.querySelector<HTMLInputElement>('#production-create-form [name="printPricePerMeter"]')
    if(option?.value&&name) name.value=option.dataset.name||''
    if(option?.value&&price) price.value=option.dataset.price||'0'
  })
  root.querySelector<HTMLSelectElement>('#production-create-form [name="workOrderId"]')?.addEventListener('change', event => {
    const option=(event.currentTarget as HTMLSelectElement).selectedOptions[0]
    const name=root.querySelector<HTMLInputElement>('#production-create-form [name="clientName"]')
    const ref=root.querySelector<HTMLInputElement>('#production-create-form [name="orderRef"]')
    const price=root.querySelector<HTMLInputElement>('#production-create-form [name="printPricePerMeter"]')
    if(option?.value&&name) name.value=option.dataset.client||''
    if(option?.value&&ref) ref.value=option.dataset.ref||''
    if(option?.value&&price&&Number(option.dataset.price||0)>0) price.value=option.dataset.price||'0'
  })
  root.querySelector<HTMLFormElement>('#production-create-form')?.addEventListener('submit', async event => {
    event.preventDefault(); if(!onCreateProduction) return
    const form=new FormData(event.currentTarget as HTMLFormElement)
    const production:Record<string,unknown>={
      type:String(form.get('type')||'PRODUCTION'),date:String(form.get('date')||today),clientId:Number(form.get('clientId')||0)||null,
      clientName:String(form.get('clientName')||'').trim(),workOrderId:Number(form.get('workOrderId')||0)||null,orderRef:String(form.get('orderRef')||'').trim(),
      plotter:String(form.get('plotter')||'X1'),printedPaperMeters:Number(form.get('printedPaperMeters')||0),printPricePerMeter:Number(form.get('printPricePerMeter')||0),
      protectionPaperMeters:Number(form.get('protectionPaperMeters')||0),paperWasteMeters:Number(form.get('paperWasteMeters')||0),designs:String(form.get('designs')||'').split(',').map(v=>v.trim()).filter(Boolean),
      manualFabricName:String(form.get('manualFabricName')||'').trim(),fabricUsedMeters:Number(form.get('fabricUsedMeters')||0),fabricCostPerMeter:Number(form.get('fabricCostPerMeter')||0),
      refundCost:Number(form.get('refundCost')||0),fabricLossCost:Number(form.get('fabricLossCost')||0),applyFixedCosts:form.get('applyFixedCosts')==='on',technicalNotes:String(form.get('technicalNotes')||'').trim()
    }
    const message=root.querySelector<HTMLElement>('#production-create-message')!; const save=root.querySelector<HTMLButtonElement>('#production-create-save')!
    if(!production.clientName){message.textContent='Seleziona o inserisci il cliente.';message.hidden=false;return}
    if(production.type==='PRODUCTION'&&Number(production.printedPaperMeters||0)<=0&&Number(production.fabricUsedMeters||0)<=0){message.textContent='Inserisci almeno i metri di carta o di tessuto lavorati.';message.hidden=false;return}
    if(production.type==='NEGATIVE'&&!production.technicalNotes){message.textContent='Descrivi l’evento negativo e la causa della perdita.';message.hidden=false;return}
    save.disabled=true;save.textContent='Calcolo e sincronizzazione…';const error=await onCreateProduction(production)
    if(error){message.textContent=error;message.hidden=false;save.disabled=false;save.textContent='Registra e sincronizza'}
  })
  const cottonCreateOverlay = root.querySelector<HTMLElement>('#cotton-create-overlay')
  const closeCottonCreate = () => { if (cottonCreateOverlay) cottonCreateOverlay.hidden=true; document.body.classList.remove('drawer-open') }
  root.querySelector('#new-cotton-order')?.addEventListener('click',()=>{if(cottonCreateOverlay){cottonCreateOverlay.hidden=false;document.body.classList.add('drawer-open')}})
  root.querySelector('#cotton-create-close')?.addEventListener('click',closeCottonCreate)
  root.querySelector('#cotton-create-cancel')?.addEventListener('click',closeCottonCreate)
  root.querySelector('.cotton-create-scrim')?.addEventListener('click',closeCottonCreate)
  root.querySelector<HTMLSelectElement>('#cotton-create-form [name="registeredClient"]')?.addEventListener('change',event=>{
    const name=(event.currentTarget as HTMLSelectElement).value
    const input=root.querySelector<HTMLInputElement>('#cotton-create-form [name="clientName"]')
    if(name&&input) input.value=name
  })
  root.querySelector<HTMLFormElement>('#cotton-create-form')?.addEventListener('submit',async event=>{
    event.preventDefault(); if(!onCreateCotton)return
    const form=new FormData(event.currentTarget as HTMLFormElement)
    const order:Record<string,unknown>={
      clientName:String(form.get('clientName')||'').trim(),title:String(form.get('title')||'').trim(),orderDate:String(form.get('orderDate')||today),dueDate:String(form.get('dueDate')||''),
      status:String(form.get('status')||'IN_PROCESS'),fabricName:String(form.get('fabricName')||'').trim(),printType:String(form.get('printType')||'REACTIVE'),orderedMeters:Number(form.get('orderedMeters')||0),
      productionCostPerMeter:canFinance?Number(form.get('productionCostPerMeter')||0):0,printPricePerMeter:canFinance?Number(form.get('printPricePerMeter')||0):0,
      fabricCostPerMeter:canFinance?Number(form.get('fabricCostPerMeter')||0):0,notes:String(form.get('notes')||'').trim()
    }
    const message=root.querySelector<HTMLElement>('#cotton-create-message')!;const save=root.querySelector<HTMLButtonElement>('#cotton-create-save')!
    if(!order.clientName||!order.title||!order.fabricName||Number(order.orderedMeters)<=0){message.textContent='Inserisci cliente, titolo, tessuto e metri ordinati validi.';message.hidden=false;return}
    save.disabled=true;save.textContent='Creazione e sincronizzazione…';const error=await onCreateCotton(order)
    if(error){message.textContent=error;message.hidden=false;save.disabled=false;save.textContent='Crea e sincronizza ordine'}
  })
  const createOverlay = root.querySelector<HTMLElement>('#zentha-create-overlay')
  const closeCreate = () => { if (createOverlay) createOverlay.hidden = true; document.body.classList.remove('drawer-open') }
  let zenthaWizardStep = 1
  const updateZenthaSummary = () => {
    const form = root.querySelector<HTMLFormElement>('#zentha-create-form'); const summary = root.querySelector<HTMLElement>('#zentha-wizard-summary')
    if (!form || !summary) return
    const client = form.querySelector<HTMLInputElement>('[name="clientName"]')?.value || 'Cliente da definire'
    const sport = form.querySelector<HTMLInputElement>('[name="sport"]:checked')?.value.replaceAll('_',' / ') || 'Multisport'
    const audience = form.querySelector<HTMLInputElement>('[name="audience"]:checked')?.value || 'Misto'
    const lines = [...root.querySelectorAll<HTMLElement>('.zentha-create-line')]
    const total = lines.reduce((sum,line)=>sum+Number(line.querySelector<HTMLInputElement>('[name="quantity"]')?.value||0),0)
    summary.innerHTML = `<article><span><b>${esc(client)}</b><small>${esc(sport)} · ${esc(audience)}</small></span><strong>${total} pezzi</strong></article>${lines.map(line=>{const select=line.querySelector<HTMLSelectElement>('[name="productId"]');const name=select?.selectedOptions[0]?.textContent||'Prodotto da selezionare';const qty=line.querySelector<HTMLInputElement>('[name="quantity"]')?.value||'0';return `<div><span>${esc(name)}</span><b>${esc(qty)} pz</b></div>`}).join('')}`
  }
  const setZenthaWizardStep = (step: number) => {
    zenthaWizardStep = Math.max(1,Math.min(6,step))
    root.querySelectorAll<HTMLElement>('[data-z-step]').forEach(section=>section.classList.toggle('active',Number(section.dataset.zStep)===zenthaWizardStep))
    root.querySelectorAll<HTMLElement>('[data-z-step-label]').forEach(label=>{const value=Number(label.dataset.zStepLabel);label.classList.toggle('active',value===zenthaWizardStep);label.classList.toggle('done',value<zenthaWizardStep)})
    const back=root.querySelector<HTMLButtonElement>('#zentha-step-back'); const next=root.querySelector<HTMLButtonElement>('#zentha-step-next'); const save=root.querySelector<HTMLButtonElement>('#zentha-create-save')
    if(back) back.hidden=zenthaWizardStep===1; if(next) next.hidden=zenthaWizardStep===6; if(save) save.hidden=zenthaWizardStep!==6
    if(zenthaWizardStep===6) updateZenthaSummary()
  }
  const bindZenthaLineActions = () => root.querySelectorAll<HTMLButtonElement>('[data-remove-zentha-line]').forEach(button => button.onclick = () => {
    const lines = root.querySelectorAll('.zentha-create-line'); if (lines.length > 1) button.closest('.zentha-create-line')?.remove()
  })
  root.querySelector('#new-zentha-order')?.addEventListener('click', () => { if (createOverlay) { createOverlay.hidden = false; document.body.classList.add('drawer-open') } })
  root.querySelector('#zentha-create-close')?.addEventListener('click', closeCreate)
  root.querySelector('#zentha-create-cancel')?.addEventListener('click', closeCreate)
  root.querySelector('.zentha-create-scrim')?.addEventListener('click', closeCreate)
  root.querySelector<HTMLSelectElement>('#zentha-create-form [name="registeredClient"]')?.addEventListener('change',event=>{const value=(event.currentTarget as HTMLSelectElement).value;const input=root.querySelector<HTMLInputElement>('#zentha-create-form [name="clientName"]');if(value&&input)input.value=value})
  root.querySelector('#zentha-step-back')?.addEventListener('click',()=>setZenthaWizardStep(zenthaWizardStep-1))
  root.querySelector('#zentha-step-next')?.addEventListener('click',()=>{
    const step=root.querySelector<HTMLElement>(`[data-z-step="${zenthaWizardStep}"]`)
    const invalid=[...(step?.querySelectorAll<HTMLInputElement|HTMLSelectElement>('[required]')||[])].find(input=>!input.value.trim())
    if(invalid){invalid.reportValidity();return}
    if(zenthaWizardStep===4&&![...root.querySelectorAll<HTMLSelectElement>('.zentha-create-line [name="productId"]')].every(select=>select.value)){root.querySelector<HTMLElement>('#zentha-create-message')!.textContent='Seleziona un articolo per ogni prodotto.';root.querySelector<HTMLElement>('#zentha-create-message')!.hidden=false;return}
    root.querySelector<HTMLElement>('#zentha-create-message')!.hidden=true;setZenthaWizardStep(zenthaWizardStep+1)
  })
  root.querySelectorAll<HTMLButtonElement>('[data-z-step-label]').forEach(button=>button.addEventListener('click',()=>{const requested=Number(button.dataset.zStepLabel);if(requested<=zenthaWizardStep)setZenthaWizardStep(requested)}))
  root.querySelectorAll<HTMLButtonElement>('[data-size-change]').forEach(button=>button.addEventListener('click',()=>{const box=button.closest<HTMLElement>('[data-size]')!;const value=box.querySelector<HTMLElement>('b')!;value.textContent=String(Math.max(0,Number(value.textContent||0)+Number(button.dataset.sizeChange||0)));const breakdown=[...root.querySelectorAll<HTMLElement>('[data-size]')].map(item=>`${item.dataset.size}×${item.querySelector('b')?.textContent||0}`).filter(value=>!value.endsWith('×0')).join(', ');const hidden=root.querySelector<HTMLInputElement>('[name="globalSizeBreakdown"]');if(hidden)hidden.value=breakdown}))
  root.querySelector('#add-zentha-line')?.addEventListener('click', () => { root.querySelector('#zentha-create-lines')?.insertAdjacentHTML('beforeend', zenthaCreateLine()); bindZenthaLineActions() })
  bindZenthaLineActions()
  root.querySelector<HTMLFormElement>('#zentha-create-form')?.addEventListener('submit', async event => {
    event.preventDefault(); if (!onCreateZentha) return
    const formElement = event.currentTarget as HTMLFormElement
    const form = new FormData(formElement)
    const order: Record<string, unknown> = { clientName:String(form.get('clientName')||''), orderDate:String(form.get('orderDate')||today), dueDate:String(form.get('dueDate')||''), status:String(form.get('status')||'CONFIRMED'), sport:String(form.get('sport')||'MULTISPORT'), audience:String(form.get('audience')||'MISTO'), priority:String(form.get('priority')||'NORMAL'), blockerReason:String(form.get('blockerReason')||''), notes:String(form.get('notes')||''), splitPercentA:Number(form.get('splitPercentA')||50), splitPercentB:Number(form.get('splitPercentB')||50), chromiaRatePerMeter:Number(form.get('chromiaRatePerMeter')||2.5) }
    const items = [...root.querySelectorAll<HTMLElement>('.zentha-create-line')].map(line => {
      const get = (name:string) => (line.querySelector<HTMLInputElement|HTMLTextAreaElement|HTMLSelectElement>(`[name="${name}"]`)?.value || '')
      const product = products.find(value => value.id === get('productId')); const productPayload = product ? p(product) : {}
      return { productId:productPayload.id || null, productName:text(productPayload.name,''), quantity:Number(get('quantity')||0), unitPrice:canFinance ? Number(get('unitPrice') || productPayload.standardPrice || 0) : 0, unitCost:canFinance ? Number(get('unitCost') || productPayload.standardCost || 0) : 0, skuSnapshot:text(productPayload.sku,''), productDescriptionSnapshot:text(productPayload.description,''), variant:get('variant') || String(form.get('audience')||''), color:get('color'), sizeBreakdown:get('sizeBreakdown') || String(form.get('globalSizeBreakdown')||''), personalization:get('personalization') || String(form.get('globalPersonalization')||''), productionNotes:get('productionNotes') || String(form.get('globalProductionNotes')||'') }
    })
    const message = root.querySelector<HTMLElement>('#zentha-create-message')!; const save = root.querySelector<HTMLButtonElement>('#zentha-create-save')!
    if (items.some(item => !item.productName || item.quantity <= 0)) { message.textContent='Seleziona un prodotto e una quantità valida per ogni riga.'; message.hidden=false; return }
    if (canFinance && Math.abs(Number(order.splitPercentA)+Number(order.splitPercentB)-100) > .01) { message.textContent='Le quote A e B devono totalizzare 100%.'; message.hidden=false; return }
    save.disabled=true; save.textContent='Creazione e sincronizzazione…'; const error = await onCreateZentha(order,items)
    if (error) { message.textContent=error; message.hidden=false; save.disabled=false; save.textContent='Crea ordine Zentha' }
  })
  const openEdit = (row: CloudRecord) => {
    const fields = editableFields(row.entity_type === 'zentha_order' ? 'zentha' : module.key, row)
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
  const openRecord = (row: CloudRecord) => {
    const recordModule = row.entity_type === 'zentha_order' ? 'zentha' : module.key
    root.querySelector<HTMLElement>('#detail-title')!.textContent = titleOf(row)
    root.querySelector<HTMLElement>('#detail-content')!.innerHTML = detailContent(recordModule, row, records, canFinance)
    const actions = root.querySelector<HTMLElement>('#detail-actions')!
    actions.innerHTML = `${canManage && onUpdate ? `<button id="edit-record">${recordModule === 'production' ? 'Modifica e valuta produzione' : 'Modifica dati'}</button>` : ''}${recordModule === 'zentha' && row.entity_type === 'zentha_order' ? `<button data-export-profile="production">PDF produzione</button>${canFinance ? '<button data-export-profile="accounting">PDF contabilità</button><button data-export-profile="complete">Dossier completo</button>' : ''}` : ''}`
    actions.querySelector('#edit-record')?.addEventListener('click', () => openEdit(row))
    actions.querySelectorAll<HTMLButtonElement>('[data-export-profile]').forEach(button => button.addEventListener('click', () => printZenthaOrder(row, records, button.dataset.exportProfile as 'production'|'accounting'|'complete')))
    const tabs = root.querySelector<HTMLElement>('#detail-tabs')!
    tabs.innerHTML = recordModule === 'production' ? '<button class="active" data-detail-target="top">Riepilogo</button><button data-detail-target=".production-brief">Scheda tecnica</button><button data-detail-target=".production-quality">Valutazione</button><button data-detail-target=".attachment-section">Immagini</button>' : `<button class="active" data-detail-target="top">Riepilogo</button>${recordModule === 'orders' ? '<button data-detail-target=".detail-section">Collegamenti e timeline</button>' : ''}<button data-detail-target=".attachment-section">Allegati</button>`
    const drawer = root.querySelector<HTMLElement>('.detail-drawer')!
    tabs.querySelectorAll<HTMLButtonElement>('[data-detail-target]').forEach(button => button.addEventListener('click', () => { if (button.dataset.detailTarget === 'top') drawer.scrollTo({top:0,behavior:'smooth'}); else drawer.querySelector<HTMLElement>(button.dataset.detailTarget || '')?.scrollIntoView({behavior:'smooth',block:'start'}); tabs.querySelectorAll('button').forEach(item => item.classList.toggle('active',item === button)) }))
    const detailOverlay = root.querySelector<HTMLElement>('#detail-overlay')!
    detailOverlay.hidden = false
    drawer.scrollTop = 0
    requestAnimationFrame(() => {
      if (!detailOverlay.hidden && detailOverlay.isConnected) document.body.classList.add('drawer-open')
    })
  }
  const draw = () => {
    const term = search.value.trim().toLowerCase(); const selected = status?.value || ''
    if (module.key === 'tasks') {
      const focusRows = primary.filter(row => JSON.stringify(p(row)).toLowerCase().includes(term) && (!selected || String(p(row).status || 'OPEN') === selected))
      root.querySelector<HTMLElement>('#task-focus')!.innerHTML = taskFocusBoard(focusRows, canManage)
      root.querySelectorAll<HTMLButtonElement>('[data-task]').forEach(button => button.addEventListener('click', () => {
        const row = records.find(item => item.id === button.dataset.task); if (row) openRecord(row)
      }))
      if (canManage && onUpdate) {
        root.querySelectorAll<HTMLElement>('[data-task]').forEach(card => {
          card.addEventListener('dragstart', event => { event.dataTransfer?.setData('text/plain', card.dataset.task || ''); card.classList.add('dragging') })
          card.addEventListener('dragend', () => card.classList.remove('dragging'))
        })
        root.querySelectorAll<HTMLElement>('[data-drop-state]').forEach(column => {
          column.addEventListener('dragover', event => { event.preventDefault(); column.classList.add('drop-target') })
          column.addEventListener('dragleave', () => column.classList.remove('drop-target'))
          column.addEventListener('drop', async event => { event.preventDefault(); column.classList.remove('drop-target'); const id = event.dataTransfer?.getData('text/plain'); const row = records.find(item => item.id === id); if (row) await onUpdate(row,{status:column.dataset.dropState}) })
        })
      }
    }
    if (module.key === 'orders') {
      const focusRows = primary.filter(row => JSON.stringify(p(row)).toLowerCase().includes(term) && (!selected || String(p(row).status || 'OPEN') === selected))
      root.querySelector<HTMLElement>('#order-focus')!.innerHTML = orderFocusBoard(focusRows, records)
      root.querySelectorAll<HTMLButtonElement>('[data-order]').forEach(button => button.addEventListener('click', () => {
        const row = records.find(item => item.id === button.dataset.order); if (row) openRecord(row)
      }))
    }
    const periodRows = primary.filter(row => { const value = moduleDate(row); return entireHistory || (value && value >= rangeStart && value <= rangeEnd) })
    root.querySelector<HTMLElement>('#module-metrics')!.innerHTML = statistics(module.key, periodRows, records, canFinance)
    root.querySelector<HTMLElement>('#module-insights')!.innerHTML = moduleInsights(periodRows)
    if (module.key === 'production') root.querySelector<HTMLElement>('#production-analysis')!.innerHTML = productionAnalytics(periodRows)
    visibleRows = periodRows.filter(row => {
      const effectiveStatus = rowStatus(row)
      const viewMatch = currentView === 'all' || (currentView === 'operative' && !terminalStatus.test(effectiveStatus)) ||
        (currentView === 'archive' && terminalStatus.test(effectiveStatus)) ||
        (currentView === 'attention' && (isOverdue(row) || /HIGH|URGENT|CRITICAL|BLOCKING/.test(String(p(row).priority || p(row).severity || effectiveStatus))))
      return viewMatch && JSON.stringify(p(row)).toLowerCase().includes(term) && (!selected || effectiveStatus === selected)
    }).sort((a,b) => {
      if (sort.value === 'date_asc') return moduleDate(a).localeCompare(moduleDate(b)) || a.updated_at.localeCompare(b.updated_at)
      if (sort.value === 'updated_desc') return b.updated_at.localeCompare(a.updated_at)
      if (sort.value === 'client_asc') return text(p(a).clientName || p(a).client || p(a).ownerName).localeCompare(text(p(b).clientName || p(b).client || p(b).ownerName),'it')
      return moduleDate(b).localeCompare(moduleDate(a)) || b.updated_at.localeCompare(a.updated_at)
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
    resultCount.textContent = `${visibleRows.length} elementi visualizzati su ${periodRows.length} ${entireHistory ? 'nello storico completo' : `nel periodo ${formatItalianDate(rangeStart)} – ${formatItalianDate(rangeEnd)}`}`
    root.querySelector<HTMLElement>('#export-period-label')!.textContent = entireHistory ? 'Tutto lo storico' : `${formatItalianDate(rangeStart)} – ${formatItalianDate(rangeEnd)}`
    root.querySelector<HTMLElement>('#export-count-label')!.textContent = String(visibleRows.length)
    body.querySelectorAll<HTMLTableRowElement>('tr').forEach(element => element.addEventListener('click', () => {
      if (!element.dataset.record) return
      const row = records.find(item => item.id === element.dataset.record); if (!row) return
      openRecord(row)
    }))
  }
  const setPreset = () => {
    const mode = period.value
    const now = new Date(); const end = now.toISOString().slice(0, 10)
    entireHistory = mode === 'all'
    if (entireHistory) { periodFrom.value = ''; periodTo.value = ''; draw(); return }
    if (mode === 'custom') { periodFrom.value ||= rangeStart; periodTo.value ||= rangeEnd; return }
    if (mode === 'current_month') rangeStart = `${end.slice(0, 7)}-01`
    else if (mode === 'previous_month') { rangeStart = new Date(now.getFullYear(), now.getMonth() - 1, 1).toISOString().slice(0, 10); rangeEnd = new Date(now.getFullYear(), now.getMonth(), 0).toISOString().slice(0, 10); periodFrom.value = rangeStart; periodTo.value = rangeEnd; draw(); return }
    else { const start = new Date(now); start.setDate(start.getDate() - Number(mode) + 1); rangeStart = start.toISOString().slice(0, 10) }
    rangeEnd = end; periodFrom.value = rangeStart; periodTo.value = rangeEnd; draw()
  }
  const setCustom = () => { if (periodFrom.value && periodTo.value && periodFrom.value <= periodTo.value) { period.value = 'custom'; entireHistory = false; rangeStart = periodFrom.value; rangeEnd = periodTo.value; draw() } }
  draw(); search.addEventListener('input', draw); status?.addEventListener('change', draw); period.addEventListener('change', setPreset); periodFrom.addEventListener('change', setCustom); periodTo.addEventListener('change', setCustom); sort.addEventListener('change', draw); group.addEventListener('change', draw)
  root.querySelectorAll<HTMLButtonElement>('[data-view]').forEach(button => button.addEventListener('click', () => {
    currentView = button.dataset.view || 'all'
    if (currentView === 'all' && module.key === 'zentha') { period.value = 'all'; entireHistory = true; periodFrom.value = ''; periodTo.value = '' }
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
    root.querySelector<HTMLElement>('#detail-content')!.innerHTML = detailContent(module.key, row, records, canFinance)
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
    editableFields(editingRow.entity_type === 'zentha_order' ? 'zentha' : module.key, editingRow).forEach(field => { const raw = String(form.get(field.key) ?? ''); patch[field.key] = field.kind === 'number' ? Number(raw || 0) : raw })
    if (editingRow.entity_type === 'cotton_order') {
      const completed = (status: unknown) => ['COMPLETED', 'DELIVERED', 'CLOSED'].includes(String(status))
      if (!completed(patch.status)) patch.completedDate = ''
      else if (!patch.completedDate && !completed(p(editingRow).status)) patch.completedDate = today
    }
    const error = await onUpdate(editingRow, patch)
    if (error) { const message = root.querySelector<HTMLElement>('#edit-message')!; message.textContent = error; message.hidden = false; button.disabled = false; button.textContent = 'Salva modifiche' }
  })
  root.querySelector('#home')?.addEventListener('click', onBack)
}
import { cottonAccountingDate, cottonAmounts, otherAmounts, matchesClient } from './accounting'
