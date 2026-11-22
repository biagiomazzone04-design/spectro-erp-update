import { useMemo, useState } from 'react'
import type { CloudRecord } from './model'
import { formatDate } from './model'
import { DailyChart, DataTable, Facts, Head, PeriodPicker, Ranking, Section, Stats, daysOf, euro, inPeriod, meters, n, p, periodFor, s, type Column } from './kit'
import { Drawer } from './ui'

export function Produzione({ records, canFinance }: { records: CloudRecord[]; canFinance: boolean }) {
  const [period, setPeriod] = useState(periodFor('current_month'))
  const [kind, setKind] = useState<'all' | 'PRODUCTION' | 'NEGATIVE'>('all')
  const [open, setOpen] = useState<CloudRecord | null>(null)
  const all = useMemo(() => records.filter(r => r.entity_type === 'production_record'), [records])
  const rows = useMemo(() => all.filter(r => inPeriod(s(p(r).date), period) && (kind === 'all' || s(p(r).type || 'PRODUCTION') === kind)), [all, period, kind])
  const production = rows.filter(r => s(p(r).type || 'PRODUCTION') !== 'NEGATIVE')
  const paper = production.reduce((t, r) => t + n(p(r).printedPaperMeters), 0)
  const fabric = production.reduce((t, r) => t + n(p(r).fabricUsedMeters), 0)
  const days = daysOf(period, production.map(r => s(p(r).date)))
  const perDay = days.map(day => production.filter(r => s(p(r).date) === day).reduce((t, r) => t + n(p(r).printedPaperMeters), 0))
  const group = (key: string) => [...production.reduce((m, r) => m.set(s(p(r)[key]) || '—', (m.get(s(p(r)[key]) || '—') || 0) + n(p(r).printedPaperMeters)), new Map<string, number>())]
    .map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value)

  const columns: Column<CloudRecord>[] = [
    { key: 'date', title: 'Data', render: r => formatDate(s(p(r).date)), sort: r => s(p(r).date) },
    { key: 'client', title: 'Cliente', render: r => <><b>{s(p(r).clientName) || 'Evento interno'}</b><small>{s(p(r).orderRef) || (s(p(r).type) === 'NEGATIVE' ? 'Evento negativo' : '')}</small></>, sort: r => s(p(r).clientName) },
    { key: 'plotter', title: 'Plotter', render: r => s(p(r).plotter) || '—', sort: r => s(p(r).plotter), hideOnPhone: true },
    { key: 'paper', title: 'Carta', render: r => meters(n(p(r).printedPaperMeters)), sort: r => n(p(r).printedPaperMeters), align: 'right' },
    { key: 'fabric', title: 'Tessuto', render: r => <>{meters(n(p(r).fabricUsedMeters))}<small>{s(p(r).fabricDisplayName)}</small></>, sort: r => n(p(r).fabricUsedMeters), align: 'right', hideOnPhone: true },
    ...(canFinance ? [{ key: 'rev', title: 'Ricavo', render: (r: CloudRecord) => <span className={s(p(r).type) === 'NEGATIVE' ? 'text-danger' : ''}>{euro(s(p(r).type) === 'NEGATIVE' ? -n(p(r).materialCostTotal) : n(p(r).grossRevenue))}</span>, sort: (r: CloudRecord) => n(p(r).grossRevenue), align: 'right' as const }] : [])
  ]

  return (
    <div className="screen">
      <Head eyebrow="Reparto" title="Produzione" lead="Registrazioni giornaliere: carta, tessuto, plotter e disegni." />
      <PeriodPicker period={period} onChange={setPeriod} />
      <Stats items={[
        { label: 'Registrazioni', value: String(production.length), hint: `${new Set(production.map(r => s(p(r).date))).size} giornate`, tone: 'pink' },
        { label: 'Carta stampata', value: meters(paper) },
        { label: 'Tessuto lavorato', value: meters(fabric) },
        { label: 'Eventi negativi', value: String(rows.length - production.length), tone: rows.length - production.length ? 'danger' : 'ok' }
      ]} />
      <div className="two-col">
        <Section title="Carta per giorno"><DailyChart days={days} values={perDay} /></Section>
        <Section title="Per plotter e cliente" lead="Metri di carta">
          <Ranking rows={group('plotter')} format={meters} />
          <div className="spacer" />
          <Ranking rows={group('clientName').slice(0, 6)} format={meters} />
        </Section>
      </div>
      <Section title="Registrazioni" actions={
        <div className="segmented small">
          {([['all', 'Tutte'], ['PRODUCTION', 'Produzioni'], ['NEGATIVE', 'Eventi negativi']] as const).map(([k, t]) => <button key={k} className={kind === k ? 'on' : ''} onClick={() => setKind(k)}>{t}</button>)}
        </div>}>
        <DataTable rows={rows} columns={columns} rowKey={r => r.id} onOpen={setOpen} initialSort={{ key: 'date', dir: -1 }}
          search={r => `${s(p(r).clientName)} ${s(p(r).orderRef)} ${s(p(r).plotter)} ${s(p(r).fabricDisplayName)} ${JSON.stringify(p(r).designs || '')}`} empty="Nessuna registrazione nel periodo." />
      </Section>
      <Drawer open={!!open} onClose={() => setOpen(null)} title="Produzione">
        {open ? <ProductionDetail r={open} canFinance={canFinance} /> : null}
      </Drawer>
    </div>
  )
}

function ProductionDetail({ r, canFinance }: { r: CloudRecord; canFinance: boolean }) {
  const x = p(r)
  const designs = Array.isArray(x.designs) ? x.designs.map(String) : s(x.designs).split(',').map(v => v.trim()).filter(Boolean)
  return (
    <div className="detail">
      <div className="detail-tags"><span className={`pill ${s(x.type) === 'NEGATIVE' ? 'danger' : 'pink'}`}>{s(x.type) === 'NEGATIVE' ? 'Evento negativo' : 'Produzione'}</span></div>
      <h2>{s(x.clientName) || 'Evento interno'}</h2>
      <p className="detail-sub">{formatDate(s(x.date))}{s(x.orderRef) ? ` · ${s(x.orderRef)}` : ''}</p>
      <Facts items={[
        ['Plotter', s(x.plotter)], ['Carta stampata', meters(n(x.printedPaperMeters))], ['Carta protezione', meters(n(x.protectionPaperMeters))],
        ['Tessuto', `${meters(n(x.fabricUsedMeters))} · ${s(x.fabricDisplayName)}`],
        ...(canFinance ? [['Ricavo', euro(n(x.grossRevenue))], ['Costi materiali', euro(n(x.materialCostTotal))], ['Margine', euro(n(x.grossProfit))]] as [string, string][] : [])
      ]} />
      {designs.length ? <section><h3>Disegni</h3><div className="chips-static">{designs.map(d => <span key={d} className="pill neutral">{d}</span>)}</div></section> : null}
      {s(x.technicalNotes) ? <section><h3>Note tecniche</h3><p className="notes">{s(x.technicalNotes)}</p></section> : null}
      {x.productionRating ? <section><h3>Valutazione</h3><p className="notes">{'★'.repeat(Math.max(1, Math.min(5, n(x.productionRating))))} {s(x.productionReview)}</p></section> : null}
    </div>
  )
}
