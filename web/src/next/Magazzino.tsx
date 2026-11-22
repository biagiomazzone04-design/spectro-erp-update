import { motion } from 'framer-motion'
import { useMemo, useState } from 'react'
import type { CloudRecord } from './model'
import { formatDate } from './model'
import { DataTable, Facts, Head, Section, Stats, euro, label, meters, n, p, s, type Column } from './kit'
import { Drawer } from './ui'

export function Magazzino({ records, canFinance }: { records: CloudRecord[]; canFinance: boolean }) {
  const [owner, setOwner] = useState<'all' | 'HOUSE' | 'CLIENT' | 'low'>('all')
  const [open, setOpen] = useState<CloudRecord | null>(null)
  const items = useMemo(() => records.filter(r => r.entity_type === 'inventory_item' && p(r).archived !== true), [records])
  const rolls = useMemo(() => records.filter(r => r.entity_type === 'fabric_roll'), [records])
  const isLow = (r: CloudRecord) => n(p(r).minimumQuantityMeters) > 0 && n(p(r).quantityMeters) <= n(p(r).minimumQuantityMeters)
  const visible = items.filter(r => owner === 'all' || (owner === 'low' ? isLow(r) : s(p(r).ownerType) === owner))
  const total = items.reduce((t, r) => t + n(p(r).quantityMeters), 0)
  const value = items.filter(r => s(p(r).ownerType) === 'HOUSE').reduce((t, r) => t + n(p(r).quantityMeters) * n(p(r).purchasePricePerMeter), 0)
  const activeRolls = (r: CloudRecord) => rolls.filter(x => s(p(x).inventoryId) === s(p(r).id) && n(p(x).availableMeters) > 0.0001)

  const columns: Column<CloudRecord>[] = [
    { key: 'fabric', title: 'Tessuto', render: r => <><b>{s(p(r).fabricName)}</b><small>{[s(p(r).composition), s(p(r).variant), s(p(r).lotCode) && `lotto ${s(p(r).lotCode)}`].filter(Boolean).join(' · ')}</small></>, sort: r => s(p(r).fabricName) },
    { key: 'owner', title: 'Proprietario', render: r => s(p(r).ownerType) === 'HOUSE' ? 'Aziendale' : s(p(r).ownerName) || 'Cliente', sort: r => s(p(r).ownerName), hideOnPhone: true },
    { key: 'stock', title: 'Disponibile', render: r => <StockBar r={r} low={isLow(r)} />, sort: r => n(p(r).quantityMeters) },
    { key: 'rolls', title: 'Colli', render: r => String(activeRolls(r).length), sort: r => activeRolls(r).length, align: 'right', hideOnPhone: true }
  ]

  return (
    <div className="screen">
      <Head eyebrow="Scorte" title="Magazzino" lead="Tessuti per proprietario, colli attivi e soglie minime." />
      <Stats items={[
        { label: 'Tessuti a magazzino', value: String(items.length), tone: 'pink' },
        { label: 'Metri disponibili', value: meters(total) },
        { label: 'Sotto scorta', value: String(items.filter(isLow).length), tone: items.some(isLow) ? 'warning' : 'ok' },
        ...(canFinance ? [{ label: 'Valore tessuti aziendali', value: euro(value) }] : [{ label: 'Colli attivi', value: String(rolls.filter(x => n(p(x).availableMeters) > 0.0001).length) }])
      ]} />
      <Section title="Tessuti" actions={
        <div className="segmented small">
          {([['all', 'Tutti'], ['HOUSE', 'Aziendali'], ['CLIENT', 'Dei clienti'], ['low', 'Sotto scorta']] as const).map(([k, t]) => <button key={k} className={owner === k ? 'on' : ''} onClick={() => setOwner(k)}>{t}</button>)}
        </div>}>
        <DataTable rows={visible} columns={columns} rowKey={r => r.id} onOpen={setOpen} initialSort={{ key: 'stock', dir: -1 }}
          search={r => `${s(p(r).fabricName)} ${s(p(r).ownerName)} ${s(p(r).supplierName)} ${s(p(r).lotCode)}`} empty="Nessun tessuto." />
      </Section>
      <Drawer open={!!open} onClose={() => setOpen(null)} title="Tessuto">
        {open ? (
          <div className="detail">
            <h2>{s(p(open).fabricName)}</h2>
            <p className="detail-sub">{s(p(open).ownerType) === 'HOUSE' ? 'Tessuto aziendale' : `Del cliente ${s(p(open).ownerName)}`}</p>
            <StockBar r={open} low={isLow(open)} big />
            <Facts items={[
              ['Fornitore', s(p(open).supplierName)], ['Composizione', s(p(open).composition)], ['Grammatura', n(p(open).weightGsm) ? `${n(p(open).weightGsm)} g/m²` : ''],
              ['Lotto', s(p(open).lotCode)], ['Soglia minima', n(p(open).minimumQuantityMeters) ? meters(n(p(open).minimumQuantityMeters)) : ''], ['Caricato il', formatDate(s(p(open).createdDate))],
              ...(canFinance ? [['Costo al metro', euro(n(p(open).purchasePricePerMeter))]] as [string, string][] : [])
            ]} />
            <h3>Colli</h3>
            <ul className="lines">
              {rolls.filter(x => s(p(x).inventoryId) === s(p(open).id)).sort((a, b) => n(p(b).availableMeters) - n(p(a).availableMeters)).map(x => (
                <li key={x.id}><b>{s(p(x).rollCode) || 'Collo'}</b><span>{meters(n(p(x).availableMeters))} / {meters(n(p(x).initialMeters))}</span><small>{label(p(x).status)}{s(p(x).note) ? ` · ${s(p(x).note)}` : ''}</small></li>
              ))}
            </ul>
          </div>
        ) : null}
      </Drawer>
    </div>
  )
}

function StockBar({ r, low, big }: { r: CloudRecord; low: boolean; big?: boolean }) {
  const qty = n(p(r).quantityMeters), min = n(p(r).minimumQuantityMeters)
  const ref = Math.max(qty, min * 3, 1)
  return (
    <div className={`stock ${big ? 'big' : ''}`}>
      <span className={low ? 'text-warning' : ''}><b>{meters(qty)}</b>{low ? ' · sotto scorta' : ''}</span>
      <div className="stock-track">
        <motion.i className={low ? 'low' : ''} initial={{ width: 0 }} animate={{ width: `${Math.min(100, (qty / ref) * 100)}%` }} transition={{ duration: 0.6 }} />
        {min ? <em style={{ left: `${Math.min(100, (min / ref) * 100)}%` }} /> : null}
      </div>
    </div>
  )
}
