import { useMemo, useState } from 'react'
import { printDashboardReport } from '../erp'
import type { CloudRecord } from './model'
import { DailyChart, Head, PeriodPicker, Ranking, Section, Stats, dateOf, daysOf, euro, inPeriod, int, meters, n, p, periodFor, s, terminal, type Stat } from './kit'
import { Icon } from './ui'

// Stesse formule della Dashboard del portale classico, così i numeri coincidono.
export function Direzione({ records, canFinance }: { records: CloudRecord[]; canFinance: boolean }) {
  const [period, setPeriod] = useState(periodFor('current_month'))
  const data = useMemo(() => {
    const within = (row: CloudRecord, ...keys: string[]) => inPeriod(dateOf(row, ...keys), period)
    const productionRecords = records.filter(r => r.entity_type === 'production_record' && within(r, 'date'))
    const production = productionRecords.filter(r => s(p(r).type || 'PRODUCTION') !== 'NEGATIVE')
    const negative = productionRecords.filter(r => s(p(r).type) === 'NEGATIVE')
    const cotton = records.filter(r => r.entity_type === 'cotton_order' && within(r, 'orderDate'))
    const works = records.filter(r => r.entity_type === 'economic_work' && within(r, 'date'))
    const orders = records.filter(r => r.entity_type === 'work_order' && within(r, 'receivedDate'))
    const settings = records.find(r => r.entity_type === 'app_settings')?.payload || {}
    const dailyFixedCost = n(settings.dailyFixedCost) || 760
    const fixedDays = new Set(productionRecords.filter(r => p(r).applyFixedCosts !== false).map(r => s(p(r).date).slice(0, 10)).filter(Boolean)).size
    const productionRevenue = production.reduce((t, r) => t + n(p(r).grossRevenue), 0)
    const cottonRevenue = cotton.reduce((t, r) => t + n(p(r).grossRevenue), 0)
    const otherRevenue = works.reduce((t, r) => t + (n(p(r).manualGrossRevenue) || n(p(r).quantity) * n(p(r).unitPrice)), 0)
    const revenue = productionRevenue + cottonRevenue + otherRevenue
    const materialCosts = productionRecords.reduce((t, r) => t + n(p(r).materialCostTotal), 0)
    const cottonCosts = cotton.reduce((t, r) => t + (n(p(r).totalCost) || n(p(r).productionCost)), 0)
    const otherCosts = works.reduce((t, r) => t + (n(p(r).totalCosts) || n(p(r).materialCosts)), 0)
    const fixedCosts = fixedDays * dailyFixedCost
    const totalCosts = materialCosts + cottonCosts + otherCosts + fixedCosts
    const paper = production.reduce((t, r) => t + n(p(r).printedPaperMeters), 0)
    const fabric = production.reduce((t, r) => t + n(p(r).fabricUsedMeters), 0)
    const clients = new Map<string, { revenue: number; profit: number; paper: number }>()
    production.forEach(r => {
      const name = s(p(r).clientName) || 'Senza cliente'
      const cur = clients.get(name) || { revenue: 0, profit: 0, paper: 0 }
      cur.revenue += n(p(r).grossRevenue); cur.profit += n(p(r).grossRevenue) - n(p(r).materialCostTotal); cur.paper += n(p(r).printedPaperMeters)
      clients.set(name, cur)
    })
    const days = daysOf(period, production.map(r => s(p(r).date)))
    const perDay = days.map(day => production.filter(r => s(p(r).date).slice(0, 10) === day).reduce((t, r) => t + n(p(r).printedPaperMeters), 0))
    const revenuePerDay = days.map(day => production.filter(r => s(p(r).date).slice(0, 10) === day).reduce((t, r) => t + n(p(r).grossRevenue), 0))
    return {
      production, negative, orders, revenue, productionRevenue, cottonRevenue, otherRevenue, materialCosts, cottonCosts, otherCosts,
      fixedCosts, fixedDays, dailyFixedCost, totalCosts, net: revenue - totalCosts, paper, fabric, clients: [...clients.entries()].sort((a, b) => b[1].revenue - a[1].revenue),
      days, perDay, revenuePerDay, completed: orders.filter(r => terminal.test(s(p(r).status))).length,
      workDays: new Set(production.map(r => s(p(r).date))).size
    }
  }, [records, period])

  const stats: Stat[] = canFinance ? [
    { label: 'Fatturato', value: euro(data.revenue), hint: `${data.workDays} giornate di produzione`, tone: 'pink' },
    { label: 'Utile netto reale', value: euro(data.net), hint: `margine ${data.revenue ? Math.round((data.net / data.revenue) * 100) : 0}%`, tone: data.net >= 0 ? 'ok' : 'danger' },
    { label: 'Costi complessivi', value: euro(data.totalCosts), hint: `di cui fissi ${euro(data.fixedCosts)}`, tone: 'warning' },
    { label: 'Eventi negativi', value: String(data.negative.length), hint: data.negative.length ? euro(data.negative.reduce((t, r) => t + n(p(r).materialCostTotal), 0)) + ' di perdite' : 'nessuna perdita', tone: data.negative.length ? 'danger' : 'ok' }
  ] : []
  const operational: Stat[] = [
    { label: 'Carta stampata', value: meters(data.paper), hint: `${int(data.workDays ? data.paper / data.workDays : 0)} m al giorno lavorato` },
    { label: 'Tessuto lavorato', value: meters(data.fabric) },
    { label: 'Produzioni', value: String(data.production.length) },
    { label: 'Ordini ricevuti', value: String(data.orders.length), hint: `${data.completed} evasi` }
  ]

  return (
    <div className="screen">
      <Head eyebrow="Centro di controllo" title="Direzione" lead="Indicatori calcolati solo nel periodo selezionato, con le stesse regole del portale.">
        <button className="ghost" onClick={() => printDashboardReport(records, period.start, period.end, canFinance)}><Icon name="printer" size={16} />Report PDF</button>
      </Head>
      <PeriodPicker period={period} onChange={setPeriod} />
      {canFinance ? <Stats items={stats} /> : null}
      <Stats items={operational} />
      <div className="two-col">
        <Section title="Carta stampata per giorno" lead="Metri di carta delle produzioni registrate">
          <DailyChart days={data.days} values={data.perDay} />
        </Section>
        {canFinance ? (
          <Section title="Clienti del periodo" lead="Fatturato, risultato industriale e metri carta">
            <div className="client-table">
              <div className="ct-head"><span>Cliente</span><span>Ricavi</span><span>Risultato</span><span>Carta</span></div>
              {data.clients.slice(0, 8).map(([name, v]) => (
                <div key={name} className="ct-row"><b>{name}</b><span>{euro(v.revenue)}</span><span className={v.profit >= 0 ? 'text-ok' : 'text-danger'}>{euro(v.profit)}</span><span>{meters(v.paper)}</span></div>
              ))}
              {!data.clients.length ? <div className="empty">Nessuna produzione nel periodo.</div> : null}
            </div>
          </Section>
        ) : (
          <Section title="Clienti del periodo" lead="Metri di carta stampati">
            <Ranking rows={data.clients.slice(0, 8).map(([name, v]) => ({ name, value: v.paper }))} format={meters} />
          </Section>
        )}
      </div>
      {canFinance ? (
        <div className="two-col">
          <Section title="Composizione dei ricavi">
            <Ranking rows={[{ name: 'Sublimazione', value: data.productionRevenue }, { name: 'Cotone', value: data.cottonRevenue }, { name: 'Ulteriori lavorazioni', value: data.otherRevenue }]} format={euro} />
          </Section>
          <Section title="Composizione dei costi" lead={`Costi fissi: ${data.fixedDays} giornate × ${euro(data.dailyFixedCost)}`}>
            <Ranking rows={[{ name: 'Materiali e perdite', value: data.materialCosts }, { name: 'Costi fissi', value: data.fixedCosts }, { name: 'Cotone', value: data.cottonCosts }, { name: 'Ulteriori lavorazioni', value: data.otherCosts }]} format={euro} />
          </Section>
        </div>
      ) : null}
      {canFinance ? (
        <Section title="Fatturato per giorno" lead="Ricavi delle produzioni registrate">
          <DailyChart days={data.days} values={data.revenuePerDay} format={euro} color="orange" />
        </Section>
      ) : null}
    </div>
  )
}
