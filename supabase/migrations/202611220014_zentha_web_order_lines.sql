-- Spectro ERP Cloud 014 — righe ordine Zentha modificabili dal portale.
-- Migrazione additiva e idempotente. Android (Beta 19) pubblica le righe anche
-- dentro l'ordine come `items`: il portale le modifica come un blocco unico in
-- overlay, Android le acquisisce e la conferma per revisione (cloud 013) le
-- consuma. Senza `finance.view` il campo `items` non è né leggibile né scrivibile.

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
            'processingCostPerMeter','defaultSalePricePerMeter','chromiaRatePerMeter','items'
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

create or replace function public.web_update_record(
    p_organization_id uuid,
    p_record_id uuid,
    p_patch jsonb
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_record public.erp_records%rowtype; v_key text; v_overlay public.web_record_overlays%rowtype;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if p_patch is null or jsonb_typeof(p_patch) <> 'object' or p_patch = '{}'::jsonb then raise exception 'Invalid empty patch'; end if;
    if pg_column_size(p_patch) > 65536 then raise exception 'Patch too large'; end if;
    select * into v_record from public.erp_records where id=p_record_id and organization_id=p_organization_id and deleted_at is null;
    if not found then raise exception 'Record not found'; end if;
    if not public.has_permission(p_organization_id, public.module_permission(v_record.module,true)) then raise exception 'Permission denied'; end if;
    for v_key in select jsonb_object_keys(p_patch) loop
      if v_key ~ '^_' or v_key in ('id','organization_id','legacy_id','revision','created_by','updated_by','deleted_at') then raise exception 'Protected field: %',v_key; end if;
      if not public.has_permission(p_organization_id,'finance.view') and v_key in (
        'amount','fabricCost','fabricCostPerMeter','fabricSalePricePerMeter','grossProfit','grossRevenue','manualGrossRevenue',
        'markupPercent','materialCostTotal','otherCost','otherCosts','paperPrintCost','priceListName','printCost','printPricePerMeter',
        'productionCostPerMeter','standardCost','standardPrice','unitCost','unitPrice','accessoryCost','cuttingCost','fabricUsedCost',
        'processingCostPerMeter','defaultSalePricePerMeter','chromiaRatePerMeter'
      ) then raise exception 'Financial field not authorized'; end if;
    end loop;
    -- Righe ordine Zentha: blocco unico con prezzi e costi, solo per ordini e
    -- solo per chi vede i dati economici.
    if p_patch ? 'items' then
      if v_record.entity_type <> 'zentha_order' then raise exception 'Protected field: items'; end if;
      if not public.has_permission(p_organization_id,'finance.view') then raise exception 'Financial field not authorized'; end if;
      if jsonb_typeof(p_patch->'items') <> 'array' or jsonb_array_length(p_patch->'items') < 1 or jsonb_array_length(p_patch->'items') > 300 then
        raise exception 'Invalid order lines';
      end if;
      if exists (
        select 1 from jsonb_array_elements(p_patch->'items') line(value)
        where jsonb_typeof(line.value) <> 'object'
           or length(trim(coalesce(line.value->>'productName',''))) < 1
           or coalesce(line.value->>'quantity','') !~ '^[1-9][0-9]{0,6}$'
           or coalesce(line.value->>'unitPrice','0') !~ '^[0-9]+([.][0-9]+)?$'
           or coalesce(line.value->>'unitCost','0') !~ '^[0-9]+([.][0-9]+)?$'
      ) then raise exception 'Invalid order lines'; end if;
    end if;
    insert into public.web_record_overlays(organization_id,record_id,patch,revision,updated_by,android_applied_at)
    values(p_organization_id,p_record_id,p_patch,1,auth.uid(),null)
    on conflict(record_id) do update set patch=public.web_record_overlays.patch||excluded.patch,
      revision=public.web_record_overlays.revision+1,updated_at=now(),updated_by=auth.uid(),android_applied_at=null
    returning * into v_overlay;
    insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,old_data,new_data)
    values(p_organization_id,auth.uid(),'WEB_OVERLAY_UPDATE',v_record.entity_type,p_record_id,null,p_patch);
    return jsonb_build_object('record_id',p_record_id,'overlay_revision',v_overlay.revision,'updated_at',v_overlay.updated_at);
end;
$$;

revoke execute on function public.list_erp_records_safe_v2(uuid,text,integer,integer) from public, anon;
grant execute on function public.list_erp_records_safe_v2(uuid,text,integer,integer) to authenticated;
revoke execute on function public.web_update_record(uuid,uuid,jsonb) from public, anon;
grant execute on function public.web_update_record(uuid,uuid,jsonb) to authenticated;
