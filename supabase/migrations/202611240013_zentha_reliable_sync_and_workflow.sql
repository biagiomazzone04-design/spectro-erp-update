-- Spectro ERP Cloud 013 — visibilità Zentha affidabile per il reparto e
-- metadati del nuovo workflow sportivo. Additiva, idempotente, senza DROP.

-- Il ruolo usato da Maria deve poter consultare l'intero contesto operativo
-- (clienti, ordini, produzione, attività e Zentha) senza ricevere finance.view.
insert into public.role_permissions(role_id, permission_key)
select r.id, p.key
from public.roles r
join public.permissions p on p.key = any(array[
  'dashboard.view','clients.view','orders.view','production.view',
  'tasks.view','zentha.view','reports.export'
]::text[])
where lower(r.name) in (
  'attività e zentha senza prezzi',
  'attivita e zentha senza prezzi'
)
on conflict do nothing;

-- Mantiene intenzionalmente esclusi tutti i permessi economici dal preset.
delete from public.role_permissions rp
using public.roles r
where rp.role_id = r.id
  and lower(r.name) in ('attività e zentha senza prezzi','attivita e zentha senza prezzi')
  and rp.permission_key in ('finance.view','finance.manage');

create or replace function public.web_create_zentha_order(
    p_organization_id uuid,
    p_order jsonb,
    p_items jsonb
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_order_id bigint; v_item_id bigint; v_order_uuid uuid; v_item jsonb;
    v_code text; v_date date; v_due text; v_can_finance boolean;
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
        'sport',coalesce(nullif(p_order->>'sport',''),'MULTISPORT'),
        'audience',coalesce(nullif(p_order->>'audience',''),'MISTO'),
        'priority',coalesce(nullif(p_order->>'priority',''),'NORMAL'),
        'blockerReason',coalesce(p_order->>'blockerReason',''),
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

revoke execute on function public.web_create_zentha_order(uuid,jsonb,jsonb) from public, anon;
grant execute on function public.web_create_zentha_order(uuid,jsonb,jsonb) to authenticated;

create or replace function public.web_create_cotton_order(p_organization_id uuid, p_order jsonb)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_legacy_id bigint; v_record_id uuid; v_date date; v_due text; v_code text; v_can_finance boolean;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'cotton.manage') then raise exception 'Permission denied'; end if;
  if length(trim(coalesce(p_order->>'clientName',''))) < 2 then raise exception 'Client name required'; end if;
  if length(trim(coalesce(p_order->>'fabricName',''))) < 1 then raise exception 'Fabric required'; end if;
  if coalesce((p_order->>'orderedMeters')::numeric,0) <= 0 then raise exception 'Meters required'; end if;
  v_date:=coalesce(nullif(p_order->>'orderDate','')::date,current_date);
  v_due:=coalesce(p_order->>'dueDate',''); if v_due<>'' then perform v_due::date; end if;
  v_can_finance:=public.has_permission(p_organization_id,'finance.view');
  perform pg_advisory_xact_lock(hashtextextended(p_organization_id::text||':cotton-web',21871));
  select least(coalesce(min(legacy_id),0)-1,-1) into v_legacy_id from public.erp_records where organization_id=p_organization_id and entity_type='cotton_order';
  v_code:='COT-WEB-'||to_char(v_date,'YYYY')||'-'||lpad(abs(v_legacy_id)::text,4,'0');
  insert into public.erp_records(organization_id,module,entity_type,legacy_id,payload,created_by,updated_by)
  values(p_organization_id,'cotton','cotton_order',v_legacy_id,jsonb_build_object(
    'id',v_legacy_id,'orderCode',v_code,'clientId',null,'clientName',trim(p_order->>'clientName'),'orderDate',v_date::text,'dueDate',v_due,
    'fabricName',trim(p_order->>'fabricName'),'printType',coalesce(nullif(p_order->>'printType',''),'REACTIVE'),'orderedMeters',(p_order->>'orderedMeters')::numeric,
    'actualProducedMeters',0,'deliveredMeters',0,'defectiveMeters',0,'sampleMeters',0,'leftoverMeters',0,
    'productionCostPerMeter',case when v_can_finance then coalesce((p_order->>'productionCostPerMeter')::numeric,0) else 0 end,
    'printPricePerMeter',case when v_can_finance then coalesce((p_order->>'printPricePerMeter')::numeric,0) else 0 end,
    'fabricOwnerType','CLIENT','inventoryId',null,'fabricCostPerMeter',case when v_can_finance then coalesce((p_order->>'fabricCostPerMeter')::numeric,0) else 0 end,
    'notes',coalesce(p_order->>'notes',''),'status',coalesce(nullif(p_order->>'status',''),'IN_PROCESS'),'title',coalesce(p_order->>'title',''),'images','[]'::jsonb,
    '_web_pending_android',true
  ),auth.uid(),auth.uid()) returning id into v_record_id;
  insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,new_data)
  values(p_organization_id,auth.uid(),'WEB_CREATE','cotton_order',v_record_id,jsonb_build_object('legacy_id',v_legacy_id,'order_code',v_code));
  return jsonb_build_object('id',v_record_id,'legacy_id',v_legacy_id,'order_code',v_code);
end $$;

create or replace function public.get_web_pending_records(p_organization_id uuid)
returns jsonb language plpgsql stable security definer set search_path=public as $$
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
  return jsonb_build_object('records',coalesce((select jsonb_agg(entry order by entry->>'entity_type',(entry->>'legacy_id')::bigint) from (
    select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload,'force_update',false) entry
    from public.erp_records r where r.organization_id=p_organization_id and r.deleted_at is null
      and r.entity_type in ('zentha_order','zentha_order_item','work_order','production_record','cotton_order') and r.payload->>'_web_pending_android'='true'
    union all
    select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload||o.patch,'force_update',true) entry
    from public.erp_records r join public.web_record_overlays o on o.record_id=r.id and o.organization_id=r.organization_id
    where r.organization_id=p_organization_id and r.deleted_at is null
      and r.entity_type in ('zentha_order','work_order','production_record','cotton_order') and o.android_applied_at is null
  ) pending),'[]'::jsonb));
end $$;

create or replace function public.ack_web_changes(p_organization_id uuid)
returns integer language plpgsql security definer set search_path=public as $$
declare v_count integer;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
  with consumed as (delete from public.web_record_overlays o using public.erp_records r
    where o.record_id=r.id and o.organization_id=p_organization_id and r.organization_id=p_organization_id
      and r.entity_type in ('zentha_order','work_order','production_record','cotton_order') and o.patch<@r.payload returning o.record_id)
  select count(*) into v_count from consumed; return v_count;
end $$;

revoke execute on function public.web_create_cotton_order(uuid,jsonb) from public,anon;
grant execute on function public.web_create_cotton_order(uuid,jsonb) to authenticated;
revoke execute on function public.get_web_pending_records(uuid) from public,anon;
grant execute on function public.get_web_pending_records(uuid) to authenticated;
revoke execute on function public.ack_web_changes(uuid) from public,anon;
grant execute on function public.ack_web_changes(uuid) to authenticated;
