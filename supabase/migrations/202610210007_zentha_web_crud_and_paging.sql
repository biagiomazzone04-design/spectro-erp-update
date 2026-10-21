-- Spectro ERP Cloud 007 — paginazione deterministica e creazione ordini Zentha dal web.
-- Additiva e idempotente. I record creati sul web usano ID locali negativi:
-- Android li importa senza collisioni e la pubblicazione successiva li rende canonici.

alter table public.web_record_overlays add column if not exists android_applied_at timestamptz;

-- Ripara gli amministratori creati con cataloghi permessi precedenti.
insert into public.role_permissions(role_id,permission_key)
select r.id,p.key from public.roles r cross join public.permissions p
where lower(r.name) in ('admin','administrator','amministratore')
on conflict do nothing;

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

create or replace function public.list_erp_records_safe_v2(
    p_organization_id uuid,
    p_module text default null,
    p_limit integer default 500,
    p_offset integer default 0
)
returns table (id uuid, module text, entity_type text, payload jsonb, updated_at timestamptz, revision bigint)
language plpgsql stable security definer set search_path = public
as $$
declare v_can_finance boolean;
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
        greatest(r.updated_at, coalesce(o.updated_at, r.updated_at)), r.revision + coalesce(o.revision, 0)
    from public.erp_records r
    left join public.web_record_overlays o on o.record_id = r.id and o.organization_id = r.organization_id
    where r.organization_id = p_organization_id and r.deleted_at is null
      and (p_module is null or r.module = p_module)
      and public.has_permission(p_organization_id, public.module_permission(r.module, false))
      and (v_can_finance or r.entity_type not in ('zentha_payment','zentha_extra_cost','payment_deadline','economic_work'))
    order by
      case r.entity_type when 'zentha_order' then 0 when 'work_order' then 0 when 'production_record' then 0 else 1 end,
      greatest(r.updated_at, coalesce(o.updated_at, r.updated_at)) desc,
      r.id
    limit least(greatest(coalesce(p_limit, 500), 1), 500)
    offset greatest(coalesce(p_offset, 0), 0);
end;
$$;

create or replace function public.web_create_zentha_order(
    p_organization_id uuid,
    p_order jsonb,
    p_items jsonb
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_order_id bigint;
    v_item_id bigint;
    v_order_uuid uuid;
    v_item jsonb;
    v_code text;
    v_date date;
    v_due text;
    v_can_finance boolean;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id, 'zentha.manage') then raise exception 'Permission denied'; end if;
    if p_order is null or jsonb_typeof(p_order) <> 'object' then raise exception 'Invalid order'; end if;
    if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) < 1 then raise exception 'Add at least one product'; end if;
    if length(trim(coalesce(p_order->>'clientName',''))) < 2 then raise exception 'Client name required'; end if;
    v_date := coalesce(nullif(p_order->>'orderDate','')::date, current_date);
    v_due := coalesce(p_order->>'dueDate','');
    if v_due <> '' then perform v_due::date; end if;
    v_can_finance := public.has_permission(p_organization_id, 'finance.view');

    perform pg_advisory_xact_lock(hashtextextended(p_organization_id::text || ':zentha-web', 21871));
    select least(coalesce(min(legacy_id), 0) - 1, -1) into v_order_id
      from public.erp_records where organization_id = p_organization_id and entity_type = 'zentha_order';
    v_code := coalesce(nullif(trim(p_order->>'orderCode'), ''), 'ZEN-WEB-' || to_char(v_date, 'YYYY') || '-' || lpad(abs(v_order_id)::text, 4, '0'));

    insert into public.erp_records(organization_id,module,entity_type,legacy_id,payload,created_by,updated_by)
    values (p_organization_id,'zentha','zentha_order',v_order_id,
      jsonb_build_object(
        'id',v_order_id,'orderCode',v_code,'clientId',null,'clientName',trim(p_order->>'clientName'),
        'orderDate',v_date::text,'dueDate',v_due,'status',coalesce(nullif(p_order->>'status',''),'CONFIRMED'),
        'notes',coalesce(p_order->>'notes',''),'attachments','[]'::jsonb,
        'splitPercentA',case when v_can_finance then coalesce((p_order->>'splitPercentA')::numeric,50) else 50 end,
        'splitPercentB',case when v_can_finance then coalesce((p_order->>'splitPercentB')::numeric,50) else 50 end,
        'chromiaRatePerMeter',case when v_can_finance then coalesce((p_order->>'chromiaRatePerMeter')::numeric,2.5) else 2.5 end,
        '_web_pending_android',true
      ),auth.uid(),auth.uid()) returning id into v_order_uuid;

    select least(coalesce(min(legacy_id), 0) - 1, -1) into v_item_id
      from public.erp_records where organization_id = p_organization_id and entity_type = 'zentha_order_item';
    for v_item in select value from jsonb_array_elements(p_items) loop
      if length(trim(coalesce(v_item->>'productName',''))) < 1 or coalesce((v_item->>'quantity')::integer,0) <= 0 then
        raise exception 'Invalid product row';
      end if;
      insert into public.erp_records(organization_id,module,entity_type,legacy_id,payload,created_by,updated_by)
      values (p_organization_id,'zentha','zentha_order_item',v_item_id,
        jsonb_build_object(
          'id',v_item_id,'orderId',v_order_id,'productId',nullif(v_item->>'productId','')::bigint,
          'productName',trim(v_item->>'productName'),'quantity',(v_item->>'quantity')::integer,
          'unitPrice',case when v_can_finance then coalesce((v_item->>'unitPrice')::numeric,0) else 0 end,
          'unitCost',case when v_can_finance then coalesce((v_item->>'unitCost')::numeric,0) else 0 end,
          'skuSnapshot',coalesce(v_item->>'skuSnapshot',''),'productDescriptionSnapshot',coalesce(v_item->>'productDescriptionSnapshot',''),
          'variant',coalesce(v_item->>'variant',''),'color',coalesce(v_item->>'color',''),
          'sizeBreakdown',coalesce(v_item->>'sizeBreakdown',''),'personalization',coalesce(v_item->>'personalization',''),
          'productionNotes',coalesce(v_item->>'productionNotes',''),'_web_pending_android',true
        ),auth.uid(),auth.uid());
      v_item_id := v_item_id - 1;
    end loop;

    insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,new_data)
    values (p_organization_id,auth.uid(),'WEB_CREATE','zentha_order',v_order_uuid,jsonb_build_object('legacy_id',v_order_id,'order_code',v_code));
    return jsonb_build_object('id',v_order_uuid,'legacy_id',v_order_id,'order_code',v_code);
end;
$$;

create or replace function public.get_web_pending_zentha_records(p_organization_id uuid)
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id, 'sync.use') then raise exception 'Permission denied'; end if;
  return jsonb_build_object('records', coalesce((
    select jsonb_agg(entry order by entry->>'entity_type', (entry->>'legacy_id')::bigint)
    from (
      select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload,'force_update',false) entry
      from public.erp_records r where r.organization_id=p_organization_id and r.deleted_at is null and r.module='zentha' and r.payload->>'_web_pending_android'='true'
      union all
      select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload||o.patch,'force_update',true) entry
      from public.erp_records r join public.web_record_overlays o on o.record_id=r.id and o.organization_id=r.organization_id
      where r.organization_id=p_organization_id and r.deleted_at is null and r.entity_type='zentha_order' and o.android_applied_at is null
    ) pending
  ), '[]'::jsonb));
end;
$$;

create or replace function public.ack_web_zentha_changes(p_organization_id uuid)
returns integer language plpgsql security definer set search_path=public
as $$
declare v_count integer;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
  with applied as (
    update public.web_record_overlays o set android_applied_at=now()
    from public.erp_records r
    where o.record_id=r.id and o.organization_id=p_organization_id and r.organization_id=p_organization_id
      and r.entity_type='zentha_order' and o.patch <@ r.payload
    returning o.record_id
  ) select count(*) into v_count from applied;
  return v_count;
end;
$$;

revoke execute on function public.list_erp_records_safe_v2(uuid,text,integer,integer) from public,anon;
grant execute on function public.list_erp_records_safe_v2(uuid,text,integer,integer) to authenticated;
revoke execute on function public.web_create_zentha_order(uuid,jsonb,jsonb) from public,anon;
grant execute on function public.web_create_zentha_order(uuid,jsonb,jsonb) to authenticated;
revoke execute on function public.get_web_pending_zentha_records(uuid) from public,anon;
grant execute on function public.get_web_pending_zentha_records(uuid) to authenticated;
revoke execute on function public.ack_web_zentha_changes(uuid) from public,anon;
grant execute on function public.ack_web_zentha_changes(uuid) to authenticated;
revoke execute on function public.web_update_record(uuid,uuid,jsonb) from public,anon;
grant execute on function public.web_update_record(uuid,uuid,jsonb) to authenticated;
