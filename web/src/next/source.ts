import { createClient, type Session } from '@supabase/supabase-js'
import type { CloudRecord } from './model'
import { demoRecords } from './demo'

const projectUrl = import.meta.env.VITE_SUPABASE_URL || 'https://fbfxtyowcxgagseqdlyz.supabase.co'
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY || 'sb_publishable_7cUKVShHm2xZg96-2pvnOw_79AunZnU'

// Stessa configurazione del portale classico: la sessione è condivisa.
export const supabase = createClient(projectUrl, anonKey, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true }
})

export type Access = {
  userId: string
  name: string
  organizationId: string
  role: string
  permissions: string[]
  canFinance: boolean
  canManageZentha: boolean
  demo: boolean
}

export const isDemo = () => new URLSearchParams(location.search).has('demo')

export async function currentSession(): Promise<Session | null> {
  const { data } = await supabase.auth.getSession()
  return data.session
}

export async function signIn(email: string, password: string) {
  const { error } = await supabase.auth.signInWithPassword({ email, password })
  if (error) throw new Error(/invalid/i.test(error.message) ? 'Email o password non corretti.' : error.message)
}

export async function loadAccess(): Promise<Access> {
  if (isDemo()) {
    return { userId: 'demo', name: 'Biagio', organizationId: 'demo', role: 'Amministratore', permissions: ['finance.view'], canFinance: true, canManageZentha: true, demo: true }
  }
  const { data, error } = await supabase.rpc('my_access_context')
  if (error) throw new Error(error.message)
  const ctx = (Array.isArray(data) ? data[0] : data) as Record<string, unknown> | null
  if (!ctx?.organization_id) throw new Error('Il tuo account non è ancora associato a un’organizzazione.')
  const permissions = Array.isArray(ctx.permissions) ? ctx.permissions.map(String) : []
  return {
    userId: String(ctx.user_id || ''),
    name: String(ctx.display_name || '').split(' ')[0] || 'Ciao',
    organizationId: String(ctx.organization_id),
    role: String(ctx.role_name || ''),
    permissions,
    canFinance: permissions.includes('finance.view'),
    canManageZentha: permissions.includes('zentha.manage'),
    demo: false
  }
}

async function listModule(organizationId: string, module: string | null): Promise<CloudRecord[]> {
  const all: CloudRecord[] = []
  for (let offset = 0; offset < 10000; offset += 500) {
    const { data, error } = await supabase.rpc('list_erp_records_safe_v2', {
      p_organization_id: organizationId, p_module: module, p_limit: 500, p_offset: offset
    })
    if (error) throw new Error(error.message)
    const page = (data || []) as CloudRecord[]
    all.push(...page)
    if (page.length < 500) break
  }
  return all
}

export async function loadRecords(access: Access): Promise<CloudRecord[]> {
  if (access.demo) return demoRecords()
  // Tutti i moduli consentiti dal ruolo: il server filtra permessi e campi economici.
  return listModule(access.organizationId, null)
}

export type NewZenthaLine = {
  productId: number | null
  productName: string
  quantity: number
  unitPrice: number
  unitCost: number
  skuSnapshot: string
  productDescriptionSnapshot: string
  variant: string
  color: string
  sizeBreakdown: string
  personalization: string
  productionNotes: string
}

export async function createZenthaOrder(access: Access, order: Record<string, unknown>, items: NewZenthaLine[]) {
  if (access.demo) {
    await new Promise(resolve => setTimeout(resolve, 700))
    return { order_code: 'ZEN-DEMO-0001' }
  }
  const { data, error } = await supabase.rpc('web_create_zentha_order', {
    p_organization_id: access.organizationId, p_order: order, p_items: items
  })
  if (error) throw new Error(error.message)
  return data as { order_code: string }
}
