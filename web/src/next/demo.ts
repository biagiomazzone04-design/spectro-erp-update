import type { CloudRecord } from './model'
import { todayIso } from './model'

// Dati dimostrativi per l'anteprima senza accesso (?demo). Clienti e ordini
// sono inventati; il catalogo riprende i 14 codici del listino Zentha.

const shift = (days: number) => {
  const d = new Date(`${todayIso()}T12:00:00`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

let seq = 0
const rec = (module: string, entity_type: string, payload: Record<string, unknown>): CloudRecord => ({
  id: `demo-${++seq}`, module, entity_type, payload, updated_at: new Date().toISOString(), revision: 1
})

const catalog: [string, string, string, string, number][] = [
  ['01 VGMD INS BABY', 'Maglia baby GM inserti', 'Maglia', 'MAGLIA BABY GM INSERTI LISCIO FINDITEX + POLY MESH', 8.56],
  ['01PCDO BABY', 'Pantaloncino baby sublimato', 'Pantaloncino', 'PANTALONCINO CORTO BABY ELAST DANZA CTA SUBLY', 10.11],
  ['01PCDO BABY UNITO', 'Pantaloncino baby unito', 'Pantaloncino', 'PANTALONCINO CORTO BABY ELAST DANZA CTA UNITO', 9.38],
  ['01PCDO DONNA', 'Pantaloncino donna sublimato', 'Pantaloncino', 'PANTALONCINO CORTO DONNA ELAST DANZA CTA SUBLY', 11.42],
  ['01PCDO DONNA UNITO', 'Pantaloncino donna unito', 'Pantaloncino', 'PANTALONCINO CORTO DONNA ELAST DANZA CTA UNITO', 10.03],
  ['01VGMD INS DONNA', 'Maglia donna GM inserti', 'Maglia', 'MAGLIA DONNA GM INSERTI LISCIO FINDITEX + POLY MESH', 9.32],
  ['02BASKE PCU UOMO', 'Pantaloncino uomo basket', 'Pantaloncino', 'PANTALONCINO UOMO BASKET POLYMESH FINDITEX', 10.03],
  ['02BGM DOUBLE BABY', 'Maglia baby GM Double', 'Maglia', 'MAGLIA BABY GM DOUBLE POLYMESH FINDITEX', 12.5],
  ['02BGM DOUBLE UOMO', 'Maglia uomo GM Double', 'Maglia', 'MAGLIA UOMO GM DOUBLE POLYMESH FINDITEX', 14.61],
  ['02BGM UOMO', 'Maglia uomo GM', 'Maglia', 'MAGLIA UOMO GM POLYMESH FINDITEX + COSTA', 8.29],
  ['03VBM PIEGA BABY', 'Maglia baby calcio/volley', 'Maglia', 'MAGLIA CALCIO/VOLLEY MC/BORDI 100% POLIESTERE', 7.88],
  ['03VBM PIEGA UOMO', 'Maglia uomo calcio/volley', 'Maglia', 'MAGLIA CALCIO/VOLLEY PIEGHE 100% POLIESTERE', 9.48],
  ['03VPC BABY', 'Pantaloncino baby volley/calcio', 'Pantaloncino', 'PANTALONCINO CORTO VOLLEY/CALCIO 100% POLIESTERE', 7.97],
  ['03VPC UOMO', 'Pantaloncino uomo volley/calcio', 'Pantaloncino', 'PANTALONCINO CORTO VOLLEY/CALCIO 100% POLIESTERE', 8.45]
]

export function demoRecords(): CloudRecord[] {
  seq = 0
  const records: CloudRecord[] = []
  catalog.forEach(([sku, name, category, description, price], index) => records.push(rec('zentha', 'zentha_product', {
    id: index + 1, sku, name, category, description, standardPrice: price, standardCost: Math.round(price / 1.47 * 100) / 100, active: true
  })))
  const work: [string, string, string, number, number, string, string][] = [
    ['SPE-2026-0112', 'Tessitura Aurora', 'Stampa foulard primavera', 410, 3, 'IN_PRODUCTION', 'NONE'],
    ['SPE-2026-0115', 'Moda Riva', 'Campionario costumi', 180, 1, 'IN_PRODUCTION', 'NONE'],
    ['SPE-2026-0110', 'Atelier Nord', 'Tende stampa digitale', 260, -1, 'IN_PRODUCTION', 'NONE'],
    ['SPE-2026-0109', 'Lino & Co', 'Cuscini collezione mare', 320, 6, 'RECEIVED', 'WAITING_MATERIAL'],
    ['SPE-2026-0116', 'Tessitura Aurora', 'Riassortimento sciarpe', 150, 9, 'IN_PROCESS', 'NONE'],
    ['SPE-2026-0117', 'Studio Fili', 'Tessuto arredo', 540, 14, 'RECEIVED', 'NONE'],
    ['SPE-2026-0105', 'Moda Riva', 'Pareo estate', 220, -6, 'DELIVERED', 'NONE'],
    ['SPE-2026-0118', 'Atelier Nord', 'Prova colore', 40, 2, 'READY', 'NONE']
  ]
  work.forEach(([orderCode, client, title, meters, due, status, blocker], index) => records.push(rec('orders', 'work_order', {
    id: 200 + index, orderCode, client, title, orderedMeters: meters, dueDate: shift(due), receivedDate: shift(-10 - index),
    status, blocker, priority: due < 3 ? 'HIGH' : 'NORMAL', description: ''
  })))
  const zentha: [string, string, number, string, [number, number, string][]][] = [
    ['ZEN-2026-0004', 'ASD Volley Centro', 6, 'PRODUCTION', [[12, 20, 'S×4, M×8, L×6, XL×2'], [14, 20, 'S×4, M×8, L×6, XL×2']]],
    ['ZEN-2026-0005', 'Basket Junior Club', 11, 'GRAPHICS', [[9, 15, 'M×5, L×6, XL×4'], [7, 15, 'M×5, L×6, XL×4']]],
    ['ZEN-2026-0006', 'Scuola Danza Aria', 4, 'WAITING_APPROVAL', [[4, 18, 'XS×6, S×8, M×4']]],
    ['ZEN-2026-0003', 'Polisportiva Sole', -3, 'DELIVERED', [[11, 30, '8 anni×10, 10 anni×12, 12 anni×8']]]
  ]
  zentha.forEach(([orderCode, clientName, due, status, lines], index) => {
    const id = 10 + index
    records.push(rec('zentha', 'zentha_order', {
      id, orderCode, clientName, orderDate: shift(-8 - index), dueDate: shift(due), status, notes: '',
      items: lines.map(([product, quantity, sizeBreakdown]) => ({
        productId: product, productName: catalog[product - 1][1], quantity, sizeBreakdown,
        unitPrice: catalog[product - 1][4], variant: '', color: ''
      }))
    }))
  })
  const plotterDays = 14
  for (let day = plotterDays - 1; day >= 0; day--) {
    const date = shift(-day)
    const weekday = new Date(`${date}T12:00:00`).getDay()
    if (weekday === 0 || weekday === 6) continue
    const x1 = 180 + ((day * 97) % 420)
    const x2 = 120 + ((day * 61) % 260)
    records.push(rec('production', 'production_record', { id: 900 + day * 2, type: 'PRODUCTION', date, plotter: 'X1', printedPaperMeters: x1, grossRevenue: x1 * 2.64, clientName: 'Demo', workOrderId: day < 3 ? 200 : null }))
    records.push(rec('production', 'production_record', { id: 901 + day * 2, type: 'PRODUCTION', date, plotter: 'X2', printedPaperMeters: x2, grossRevenue: x2 * 2.64, clientName: 'Demo' }))
  }
  return records
}
