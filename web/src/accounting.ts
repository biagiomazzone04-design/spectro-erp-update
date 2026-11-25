type Payload = Record<string, unknown>

const amount = (value: unknown) => Number(value) || 0
export const clientKey = (name: unknown) => String(name ?? '').trim().replace(/\s+/g, ' ').toLocaleLowerCase('it-IT')

export function cottonAccountingDate(order: Payload): string {
  const completed = ['COMPLETED', 'DELIVERED', 'CLOSED'].includes(String(order.status))
  return String((completed && order.completedDate) || order.orderDate || '').slice(0, 10)
}

export function cottonAmounts(order: Payload) {
  if (order.status === 'CANCELLED') return { revenue: 0, costs: 0 }
  const ordered = amount(order.orderedMeters)
  const delivered = amount(order.deliveredMeters)
  const produced = amount(order.actualProducedMeters)
  const fabricRate = order.fabricOwnerType === 'HOUSE' ? amount(order.fabricCostPerMeter) : 0
  return {
    revenue: (delivered > 0 ? delivered : ordered) * amount(order.printPricePerMeter),
    costs: (produced > 0 ? produced : ordered) * (amount(order.productionCostPerMeter) + fabricRate)
  }
}

export function otherAmounts(work: Payload) {
  if (work.status === 'CANCELLED') return { revenue: 0, costs: 0 }
  const materials = Array.isArray(work.materials) ? work.materials : []
  return {
    revenue: amount(work.manualGrossRevenue) || amount(work.quantity) * amount(work.unitPrice),
    costs: amount(work.otherCosts) + materials.reduce((sum, entry) => sum + amount(entry.quantity) * amount(entry.unitCost), 0)
  }
}

export function matchesClient(client: Payload, row: Payload) {
  if (row.clientId != null && row.clientId !== '' && Number(row.clientId) !== 0) {
    return String(row.clientId) === String(client.id)
  }
  const key = clientKey(client.name)
  return key !== '' && key === clientKey(row.clientName || row.client)
}
