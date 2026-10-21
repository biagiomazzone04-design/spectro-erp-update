-- Spectro ERP Cloud 009 — produzione web, contabilità completa e ritorno su Android.
-- Migrazione additiva e idempotente: non elimina né riscrive record esistenti.

insert into public.role_permissions(role_id,permission_key)
select r.id,p.key from public.roles r cross join public.permissions p
where lower(r.name) in ('admin','administrator','amministratore')
on conflict do nothing;

create or replace function public.web_create_work_order(p_organization_id uuid, p_order jsonb)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_legacy_id bigint; v_record_id uuid; v_code text; v_received date; v_due text;
v_material_received text; v_can_finance boolean; v_client_id bigint;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'orders.manage') then raise exception 'Permission denied'; end if;
  if p_order is null or jsonb_typeof(p_order)<>'object' then raise exception 'Invalid order'; end if;
  if length(trim(coalesce(p_order->>'client','')))<2 then raise exception 'Client name required'; end if;
  if length(trim(coalesce(p_order->>'title','')))<2 then raise exception 'Order title required'; end if;
  v_received:=coalesce(nullif(p_order->>'receivedDate','')::date,current_date);
  v_due:=coalesce(p_order->>'dueDate',''); v_material_received:=coalesce(p_order->>'materialReceivedDate','');
  if v_due<>'' then perform v_due::date; end if; if v_material_received<>'' then perform v_material_received::date; end if;
  v_can_finance:=public.has_permission(p_organization_id,'finance.view');
  v_client_id:=nullif(p_order->>'clientId','')::bigint;
  perform pg_advisory_xact_lock(hashtextextended(p_organization_id::text||':work-order-web',21871));
  select least(coalesce(min(legacy_id),0)-1,-1) into v_legacy_id from public.erp_records where organization_id=p_organization_id and entity_type='work_order';
  v_code:='SPE-WEB-'||to_char(v_received,'YYYY')||'-'||lpad(abs(v_legacy_id)::text,4,'0');
  insert into public.erp_records(organization_id,module,entity_type,legacy_id,payload,created_by,updated_by)
  values(p_organization_id,'orders','work_order',v_legacy_id,jsonb_build_object(
    'id',v_legacy_id,'receivedDate',v_received::text,'client',trim(p_order->>'client'),'title',trim(p_order->>'title'),
    'description',coalesce(p_order->>'description',''),'images','[]'::jsonb,'status',coalesce(nullif(p_order->>'status',''),'RECEIVED'),
    'dueDate',v_due,'priority',coalesce(nullif(p_order->>'priority',''),'NORMAL'),'blocker',coalesce(nullif(p_order->>'blocker',''),'NONE'),
    'clientId',v_client_id,'orderCode',v_code,'fabricInventoryId',null,'orderedMeters',coalesce((p_order->>'orderedMeters')::numeric,0),
    'printPricePerMeter',case when v_can_finance then coalesce((p_order->>'printPricePerMeter')::numeric,0) else 0 end,
    'designCode',coalesce(p_order->>'designCode',''),'productionRecordId',null,'effectiveDeliveryDate','','cancelReason','',
    'materialReceivedDate',v_material_received,'materialReceiptNotes',coalesce(p_order->>'materialReceiptNotes',''),'_web_pending_android',true
  ),auth.uid(),auth.uid()) returning id into v_record_id;
  insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,new_data)
  values(p_organization_id,auth.uid(),'WEB_CREATE','work_order',v_record_id,jsonb_build_object('legacy_id',v_legacy_id,'order_code',v_code));
  return jsonb_build_object('id',v_record_id,'legacy_id',v_legacy_id,'order_code',v_code);
end $$;

create or replace function public.web_create_production_record(p_organization_id uuid, p_record jsonb)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_legacy_id bigint; v_record_id uuid; v_type text; v_date date; v_client_id bigint; v_work_order_id bigint;
v_daily numeric:=760; v_x1 numeric:=0.8; v_x2 numeric:=1; v_protection_rate numeric:=0.1;
v_paper numeric; v_waste numeric; v_protection numeric; v_fabric numeric; v_fabric_rate numeric;
v_refund numeric; v_fabric_loss numeric; v_print_rate numeric; v_paper_cost numeric; v_protection_cost numeric;
v_fabric_cost numeric; v_material_cost numeric; v_revenue numeric; v_settings jsonb;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'production.manage') then raise exception 'Permission denied'; end if;
  if p_record is null or jsonb_typeof(p_record)<>'object' then raise exception 'Invalid production record'; end if;
  v_type:=upper(coalesce(nullif(p_record->>'type',''),'PRODUCTION'));
  if v_type not in ('PRODUCTION','NEGATIVE') then raise exception 'Invalid record type'; end if;
  v_date:=coalesce(nullif(p_record->>'date','')::date,current_date);
  if length(trim(coalesce(p_record->>'clientName','')))<2 then raise exception 'Client name required'; end if;
  v_client_id:=nullif(p_record->>'clientId','')::bigint; v_work_order_id:=nullif(p_record->>'workOrderId','')::bigint;
  select payload into v_settings from public.erp_records where organization_id=p_organization_id and entity_type='app_settings' and deleted_at is null order by updated_at desc limit 1;
  if v_settings is not null then
    v_daily:=coalesce((v_settings->>'dailyFixedCost')::numeric,v_daily); v_x1:=coalesce((v_settings->>'plotterX1Cost')::numeric,v_x1);
    v_x2:=coalesce((v_settings->>'plotterX2Cost')::numeric,v_x2); v_protection_rate:=coalesce((v_settings->>'protectionPaperCost')::numeric,v_protection_rate);
  end if;
  v_paper:=coalesce((p_record->>'printedPaperMeters')::numeric,0); v_waste:=coalesce((p_record->>'paperWasteMeters')::numeric,0);
  v_protection:=coalesce((p_record->>'protectionPaperMeters')::numeric,0); v_fabric:=coalesce((p_record->>'fabricUsedMeters')::numeric,0);
  v_fabric_rate:=coalesce((p_record->>'fabricCostPerMeter')::numeric,0); v_refund:=coalesce((p_record->>'refundCost')::numeric,0);
  v_fabric_loss:=coalesce((p_record->>'fabricLossCost')::numeric,0); v_print_rate:=coalesce((p_record->>'printPricePerMeter')::numeric,0);
  if least(v_paper,v_waste,v_protection,v_fabric,v_fabric_rate,v_refund,v_fabric_loss,v_print_rate)<0 or v_waste>v_paper then raise exception 'Invalid meters or costs'; end if;
  if v_type='PRODUCTION' and v_paper<=0 and v_fabric<=0 then raise exception 'Production meters required'; end if;
  v_paper_cost:=case when v_type='PRODUCTION' then greatest(v_paper-v_waste,0)*case when upper(coalesce(p_record->>'plotter','X1'))='X2' then v_x2 else v_x1 end else 0 end;
  v_protection_cost:=case when v_type='PRODUCTION' then v_protection*v_protection_rate else 0 end;
  v_fabric_cost:=case when v_type='PRODUCTION' then v_fabric*v_fabric_rate else 0 end;
  v_material_cost:=case when v_type='NEGATIVE' then v_refund+v_fabric_loss else v_paper_cost+v_protection_cost+v_fabric_cost end;
  v_revenue:=case when v_type='PRODUCTION' then v_paper*v_print_rate else 0 end;
  perform pg_advisory_xact_lock(hashtextextended(p_organization_id::text||':production-web',21871));
  select least(coalesce(min(legacy_id),0)-1,-1) into v_legacy_id from public.erp_records where organization_id=p_organization_id and entity_type='production_record';
  insert into public.erp_records(organization_id,module,entity_type,legacy_id,payload,created_by,updated_by)
  values(p_organization_id,'production','production_record',v_legacy_id,jsonb_build_object(
    'id',v_legacy_id,'type',v_type,'date',v_date::text,'clientId',v_client_id,'clientName',trim(p_record->>'clientName'),
    'orderRef',coalesce(p_record->>'orderRef',''),'designs',coalesce(p_record->'designs','[]'::jsonb),'printPricePerMeter',v_print_rate,
    'plotter',upper(coalesce(p_record->>'plotter','X1')),'printedPaperMeters',v_paper,'protectionPaperMeters',v_protection,'paperWasteMeters',v_waste,
    'fabricMode',case when v_fabric>0 or length(trim(coalesce(p_record->>'manualFabricName','')))>0 then 'MANUAL' else 'NONE' end,
    'fabricId',null,'fabricUsedMeters',v_fabric,'fabricWasteMeters',0,'fabricSalePricePerMeter',0,
    'manualFabricName',coalesce(p_record->>'manualFabricName',''),'fabricDisplayName',coalesce(nullif(p_record->>'manualFabricName',''),'Nessuno'),
    'technicalNotes',coalesce(p_record->>'technicalNotes',''),'images','[]'::jsonb,'refundCost',v_refund,'fabricLossCost',v_fabric_loss,
    'applyFixedCosts',coalesce((p_record->>'applyFixedCosts')::boolean,true),'paperPrintCost',v_paper_cost,
    'protectionPaperCost',v_protection_cost,'fabricUsedCost',v_fabric_cost,'materialCostTotal',v_material_cost,
    'grossRevenue',v_revenue,'grossProfit',v_revenue-v_material_cost,'workOrderId',v_work_order_id,'zenthaOrderId',null,'_web_pending_android',true
  ),auth.uid(),auth.uid()) returning id into v_record_id;
  insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,new_data)
  values(p_organization_id,auth.uid(),'WEB_CREATE','production_record',v_record_id,jsonb_build_object('legacy_id',v_legacy_id,'type',v_type,'date',v_date));
  return jsonb_build_object('id',v_record_id,'legacy_id',v_legacy_id);
end $$;

create or replace function public.get_web_pending_records(p_organization_id uuid)
returns jsonb language plpgsql stable security definer set search_path=public as $$
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
  return jsonb_build_object('records',coalesce((select jsonb_agg(entry order by entry->>'entity_type',(entry->>'legacy_id')::bigint) from (
    select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload,'force_update',false) entry
    from public.erp_records r where r.organization_id=p_organization_id and r.deleted_at is null
      and r.entity_type in ('zentha_order','zentha_order_item','work_order','production_record') and r.payload->>'_web_pending_android'='true'
    union all
    select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload||o.patch,'force_update',true) entry
    from public.erp_records r join public.web_record_overlays o on o.record_id=r.id and o.organization_id=r.organization_id
    where r.organization_id=p_organization_id and r.deleted_at is null
      and r.entity_type in ('zentha_order','work_order','production_record') and o.android_applied_at is null
  ) pending),'[]'::jsonb));
end $$;

create or replace function public.ack_web_changes(p_organization_id uuid)
returns integer language plpgsql security definer set search_path=public as $$
declare v_count integer;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
  with applied as (update public.web_record_overlays o set android_applied_at=now() from public.erp_records r
    where o.record_id=r.id and o.organization_id=p_organization_id and r.organization_id=p_organization_id
      and r.entity_type in ('zentha_order','work_order','production_record') and o.patch<@r.payload returning o.record_id)
  select count(*) into v_count from applied; return v_count;
end $$;

revoke execute on function public.web_create_work_order(uuid,jsonb) from public,anon;
grant execute on function public.web_create_work_order(uuid,jsonb) to authenticated;
revoke execute on function public.web_create_production_record(uuid,jsonb) from public,anon;
grant execute on function public.web_create_production_record(uuid,jsonb) to authenticated;
revoke execute on function public.get_web_pending_records(uuid) from public,anon;
grant execute on function public.get_web_pending_records(uuid) to authenticated;
revoke execute on function public.ack_web_changes(uuid) from public,anon;
grant execute on function public.ack_web_changes(uuid) to authenticated;
