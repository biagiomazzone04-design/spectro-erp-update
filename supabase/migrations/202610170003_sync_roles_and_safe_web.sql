-- Spectro ERP Cloud — repair cloud access, add requested role presets and
-- expose a finance-safe web mirror. Idempotent and non-destructive.

-- Admin must always receive capabilities introduced by later migrations.
insert into public.role_permissions(role_id, permission_key)
select r.id, p.key
from public.roles r
cross join public.permissions p
where r.is_system and r.name = 'Admin'
on conflict do nothing;

do $$
declare
    v_org uuid;
    v_role uuid;
begin
    for v_org in select id from public.organizations loop
        insert into public.roles(organization_id, name, description, is_system)
        values (v_org, 'Supervisore', 'Tutta Spectro in sola lettura, dashboard e statistiche comprese', true)
        on conflict (organization_id, name) do update set
            description = excluded.description,
            is_system = true
        returning id into v_role;

        delete from public.role_permissions where role_id = v_role;
        insert into public.role_permissions(role_id, permission_key)
        select v_role, unnest(array[
            'dashboard.view','clients.view','orders.view','production.view',
            'inventory.view','issues.view','cotton.view','zentha.view',
            'finance.view','tasks.view','reports.export','audit.view'
        ]::text[]);

        insert into public.roles(organization_id, name, description, is_system)
        values (v_org, 'Attività e Zentha senza prezzi', 'Attività e ordini Zentha operativi senza dati economici', true)
        on conflict (organization_id, name) do update set
            description = excluded.description,
            is_system = true
        returning id into v_role;

        delete from public.role_permissions where role_id = v_role;
        insert into public.role_permissions(role_id, permission_key)
        select v_role, unnest(array['tasks.view','zentha.view','reports.export']::text[]);
    end loop;
end;
$$;

-- Returns only records the signed-in member may read. Financial fields are
-- removed server-side when finance.view is missing, so hiding prices does not
-- rely on browser rendering alone.
create or replace function public.list_erp_records_safe(
    p_organization_id uuid,
    p_module text default null,
    p_limit integer default 100
)
returns table (
    id uuid,
    module text,
    entity_type text,
    payload jsonb,
    updated_at timestamptz,
    revision bigint
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_can_finance boolean;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.is_org_member(p_organization_id) then raise exception 'Permission denied'; end if;
    v_can_finance := public.has_permission(p_organization_id, 'finance.view');

    return query
    select
        r.id,
        r.module,
        r.entity_type,
        case
            when v_can_finance then r.payload
            else r.payload - array[
                'amount','dailyFixedCost','defaultPrintPrice','fabricCost','fabricCostPerMeter',
                'fabricLossCost','fabricSalePricePerMeter','grossProfit','grossRevenue',
                'labelCost','liningCost','manualGrossRevenue','markupPercent','materialCostTotal',
                'otherCost','otherCosts','packagingCost','paperPrintCost','priceListName',
                'printCost','printPricePerMeter','productionCostPerMeter','protectionPaperCost',
                'purchasePricePerMeter','refundCost','ribCost','sewingCost','splitPercentA',
                'splitPercentB','standardCost','standardPrice','unitCost','unitPrice','accessoryCost',
                'cuttingCost','fabricUsedCost'
            ]
        end as payload,
        r.updated_at,
        r.revision
    from public.erp_records r
    where r.organization_id = p_organization_id
      and r.deleted_at is null
      and (p_module is null or r.module = p_module)
      and public.has_permission(p_organization_id, public.module_permission(r.module, false))
      and (v_can_finance or r.entity_type not in ('zentha_payment','zentha_extra_cost','payment_deadline','economic_work'))
    order by r.updated_at desc
    limit least(greatest(coalesce(p_limit, 100), 1), 500);
end;
$$;

revoke execute on function public.list_erp_records_safe(uuid, text, integer) from public, anon;
grant execute on function public.list_erp_records_safe(uuid, text, integer) to authenticated;

-- Prevent authenticated clients from bypassing the sanitizing RPC and reading
-- the original JSON payload directly. Workspace publication continues through
-- its SECURITY DEFINER RPC.
revoke select on public.erp_records from authenticated;

