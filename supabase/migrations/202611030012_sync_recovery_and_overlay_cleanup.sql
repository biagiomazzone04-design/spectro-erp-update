-- Spectro ERP Cloud 012 — elimina gli overlay già assorbiti da Android e
-- impedisce che uno stato web obsoleto prevalga sulle pubblicazioni successive.
-- Migrazione additiva/idempotente: nessun record operativo viene eliminato.

-- Un overlay marcato come applicato è già contenuto nel payload canonico
-- pubblicato da Android. Conservarlo separatamente lo farebbe riapparire sopra
-- modifiche mobili più recenti (per esempio DONE che torna IN_PROGRESS).
delete from public.web_record_overlays
where android_applied_at is not null;

create or replace function public.list_erp_records_safe_v2(
    p_organization_id uuid,
    p_module text default null,
    p_limit integer default 500,
    p_offset integer default 0
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
    select r.id, r.module, r.entity_type,
        case when v_can_finance then r.payload || coalesce(o.patch, '{}'::jsonb)
        else (r.payload || coalesce(o.patch, '{}'::jsonb)) - array[
            'amount','dailyFixedCost','defaultPrintPrice','fabricCost','fabricCostPerMeter','fabricLossCost',
            'fabricSalePricePerMeter','grossProfit','grossRevenue','labelCost','liningCost','manualGrossRevenue',
            'markupPercent','materialCostTotal','otherCost','otherCosts','packagingCost','paperPrintCost',
            'priceListName','printCost','printPricePerMeter','productionCostPerMeter','protectionPaperCost',
            'purchasePricePerMeter','refundCost','ribCost','sewingCost','splitPercentA','splitPercentB',
            'standardCost','standardPrice','unitCost','unitPrice','accessoryCost','cuttingCost','fabricUsedCost',
            'processingCostPerMeter','defaultSalePricePerMeter','chromiaRatePerMeter'
        ] end,
        greatest(r.updated_at, coalesce(o.updated_at, r.updated_at)),
        r.revision + coalesce(o.revision, 0)
    from public.erp_records r
    left join public.web_record_overlays o
      on o.record_id = r.id
     and o.organization_id = r.organization_id
     and o.android_applied_at is null
    where r.organization_id = p_organization_id
      and r.deleted_at is null
      and (p_module is null or r.module = p_module)
      and public.has_permission(p_organization_id, public.module_permission(r.module, false))
      and (v_can_finance or r.entity_type not in (
          'zentha_payment','zentha_extra_cost','payment_deadline','economic_work'
      ))
    order by
      case r.entity_type
        when 'zentha_order' then 0
        when 'work_order' then 0
        when 'production_record' then 0
        else 1
      end,
      greatest(r.updated_at, coalesce(o.updated_at, r.updated_at)) desc,
      r.id
    limit least(greatest(coalesce(p_limit, 500), 1), 500)
    offset greatest(coalesce(p_offset, 0), 0);
end;
$$;

-- Compatibilità per portali precedenti che usano ancora la funzione senza
-- offset: delega alla lettura corretta e mantiene i medesimi permessi.
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
language sql
stable
security definer
set search_path = public
as $$
    select * from public.list_erp_records_safe_v2(
        p_organization_id,
        p_module,
        least(greatest(coalesce(p_limit, 100), 1), 500),
        0
    );
$$;

-- Dopo la pubblicazione Android l'overlay incorporato viene consumato, non
-- soltanto marcato. Una futura modifica web creerà un nuovo overlay pulito.
create or replace function public.ack_web_changes(p_organization_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_count integer;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id, 'sync.use') then raise exception 'Permission denied'; end if;

    with consumed as (
        delete from public.web_record_overlays o
        using public.erp_records r
        where o.record_id = r.id
          and o.organization_id = p_organization_id
          and r.organization_id = p_organization_id
          and r.entity_type in ('zentha_order','work_order','production_record')
          and o.patch <@ r.payload
        returning o.record_id
    )
    select count(*) into v_count from consumed;
    return v_count;
end;
$$;

create or replace function public.ack_web_zentha_changes(p_organization_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_count integer;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id, 'sync.use') then raise exception 'Permission denied'; end if;

    with consumed as (
        delete from public.web_record_overlays o
        using public.erp_records r
        where o.record_id = r.id
          and o.organization_id = p_organization_id
          and r.organization_id = p_organization_id
          and r.entity_type = 'zentha_order'
          and o.patch <@ r.payload
        returning o.record_id
    )
    select count(*) into v_count from consumed;
    return v_count;
end;
$$;

revoke execute on function public.list_erp_records_safe_v2(uuid,text,integer,integer) from public, anon;
grant execute on function public.list_erp_records_safe_v2(uuid,text,integer,integer) to authenticated;
revoke execute on function public.list_erp_records_safe(uuid,text,integer) from public, anon;
grant execute on function public.list_erp_records_safe(uuid,text,integer) to authenticated;
revoke execute on function public.ack_web_changes(uuid) from public, anon;
grant execute on function public.ack_web_changes(uuid) to authenticated;
revoke execute on function public.ack_web_zentha_changes(uuid) from public, anon;
grant execute on function public.ack_web_zentha_changes(uuid) to authenticated;

