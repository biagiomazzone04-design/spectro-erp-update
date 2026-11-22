import { motion } from 'framer-motion'
import { useMemo, useState } from 'react'
import type { CloudRecord } from './model'
import { formatDate } from './model'
import { Facts, Head, euro, int, meters, n, p, s } from './kit'
import { Drawer, Icon } from './ui'

type ClientCard = {
  name: string
  orders: CloudRecord[]
  productions: CloudRecord[]
  zentha: CloudRecord[]
  issues: CloudRecord[]
  revenue: number
  paper: number
  last: string
}

const key = (name: string) => name.trim().toLowerCase()

/** Storico unificato: ordini, produzioni (anche senza ordine), Zentha e criticità per nome cliente. */
export function Clienti({ records, canFinance }: { records: CloudRecord[]; canFinance: boolean }) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState<ClientCard | null>(null)
  const cards = useMemo(() => {
    const map = new Map<string, ClientCard>()
    const get = (name: string) => {
      const k = key(name)
      if (!k) return null
      if (!map.has(k)) map.set(k, { name: name.trim(), orders: [], productions: [], zentha: [], issues: [], revenue: 0, paper: 0, last: '' })
      return map.get(k)!
    }
    records.filter(r => r.entity_type === 'client').forEach(r => get(s(p(r).name)))
    records.forEach(r => {
      const x = p(r)
      if (r.entity_type === 'work_order') { const c = get(s(x.client)); if (c) { c.orders.push(r); c.last = [c.last, s(x.receivedDate)].sort().at(-1)! } }
      if (r.entity_type === 'production_record' && s(x.type || 'PRODUCTION') !== 'NEGATIVE') { const c = get(s(x.clientName)); if (c) { c.productions.push(r); c.revenue += n(x.grossRevenue); c.paper += n(x.printedPaperMeters); c.last = [c.last, s(x.date)].sort().at(-1)! } }
      if (r.entity_type === 'zentha_order') { const c = get(s(x.clientName)); if (c) { c.zentha.push(r); c.last = [c.last, s(x.orderDate)].sort().at(-1)! } }
      if (r.entity_type === 'client_issue' || r.entity_type === 'order_timeline_event') { const c = get(s(x.clientName)); if (c) c.issues.push(r) }
    })
    return [...map.values()].sort((a, b) => (b.last || '').localeCompare(a.last || '') || a.name.localeCompare(b.name, 'it'))
  }, [records])
  const visible = cards.filter(c => c.name.toLowerCase().includes(query.trim().toLowerCase()))

  return (
    <div className="screen">
      <Head eyebrow="CRM" title="Clienti" lead={`${cards.length} clienti · storico unificato di ordini, produzioni, Zentha e criticità`} />
      <label className="search"><Icon name="search" size={18} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Cerca un cliente" /></label>
      <div className="client-grid">
        {visible.map((c, i) => (
          <motion.button key={c.name} className="client-card" onClick={() => setOpen(c)} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(i, 20) * 0.02 }} whileHover={{ y: -3 }}>
            <span className="avatar">{c.name.slice(0, 1).toUpperCase()}</span>
            <b>{c.name}</b>
            <small>{c.last ? `ultima attività ${formatDate(c.last)}` : 'nessuna attività'}</small>
            <div className="client-stats">
              <span><b>{c.orders.length + c.zentha.length}</b>ordini</span>
              <span><b>{c.productions.length}</b>produzioni</span>
              <span><b>{canFinance ? euro(c.revenue) : meters(c.paper)}</b>{canFinance ? 'fatturato' : 'carta'}</span>
            </div>
            {c.issues.some(r => !/RESOLVED|CLOSED/.test(s(p(r).status))) ? <em className="pill warn">Criticità aperte</em> : null}
          </motion.button>
        ))}
      </div>
      <Drawer open={!!open} onClose={() => setOpen(null)} title="Cliente">
        {open ? <ClientDetail c={open} canFinance={canFinance} /> : null}
      </Drawer>
    </div>
  )
}

function ClientDetail({ c, canFinance }: { c: ClientCard; canFinance: boolean }) {
  type Item = { date: string; kind: string; title: string; detail: string; tone: string }
  const timeline: Item[] = [
    ...c.orders.map(r => ({ date: s(p(r).receivedDate), kind: 'Ordine', title: s(p(r).orderCode) || s(p(r).title), detail: `${s(p(r).title)} · ${n(p(r).orderedMeters) ? meters(n(p(r).orderedMeters)) : ''}`, tone: 'info' })),
    ...c.zentha.map(r => ({ date: s(p(r).orderDate), kind: 'Zentha', title: s(p(r).orderCode), detail: s(p(r).notes) || 'Ordine abbigliamento', tone: 'pink' })),
    ...c.productions.map(r => ({ date: s(p(r).date), kind: 'Produzione', title: s(p(r).orderRef) || 'Produzione', detail: `${meters(n(p(r).printedPaperMeters))} carta${canFinance ? ` · ${euro(n(p(r).grossRevenue))}` : ''}${s(p(r).workOrderId) ? '' : ' · senza ordine collegato'}`, tone: 'ok' })),
    ...c.issues.map(r => ({ date: s(p(r).openedDate || p(r).date), kind: 'Criticità', title: s(p(r).title), detail: s(p(r).description).slice(0, 90), tone: 'warn' }))
  ].sort((a, b) => b.date.localeCompare(a.date))
  return (
    <div className="detail">
      <h2>{c.name}</h2>
      <Facts items={[
        ['Ordini', String(c.orders.length + c.zentha.length)], ['Produzioni', String(c.productions.length)], ['Carta stampata', meters(c.paper)],
        ...(canFinance ? [['Fatturato storico', euro(c.revenue)], ['Media per produzione', euro(c.productions.length ? c.revenue / c.productions.length : 0)]] as [string, string][] : []),
        ['Ultima attività', formatDate(c.last)]
      ]} />
      <h3>Storico · {int(timeline.length)} voci</h3>
      <ol className="history">
        {timeline.slice(0, 80).map((item, i) => (
          <li key={i} className={item.tone}><span className="h-date">{formatDate(item.date)}</span><span className="h-body"><b>{item.kind} · {item.title}</b><small>{item.detail}</small></span></li>
        ))}
      </ol>
    </div>
  )
}

