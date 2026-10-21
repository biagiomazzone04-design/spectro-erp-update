-- Spectro ERP Cloud 008 — creazione ordini Spectro dal web e ritorno automatico su Android.
-- Migrazione additiva e idempotente. Non elimina né riscrive dati esistenti.

-- Mantiene completo il profilo amministratore anche sui progetti aggiornati
-- da cataloghi permessi precedenti.
insert into public.role_permissions(role_id,permission_key)
select r.id,p.key from public.roles r cross join public.permissions p
where lower(r.name) in ('admin','administrator','amministratore')
on conflict do nothing;

create or replace function public.web_create_work_order(
    p_organization_id uuid,
    p_order jsonb
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_legacy_id bigint;
    v_record_id uuid;
    v_code text;
    v_received date;
    v_due text;
    v_material_received text;
    v_can_finance boolean;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id, 'orders.manage') then raise exception 'Permission denied'; end if;
    if p_order is null or jsonb_typeof(p_order) <> 'object' then raise exception 'Invalid order'; end if;
    if length(trim(coalesce(p_order->>'client',''))) < 2 then raise exception 'Client name required'; end if;
    if length(trim(coalesce(p_order->>'title',''))) < 2 then raise exception 'Order title required'; end if;

    v_received := coalesce(nullif(p_order->>'receivedDate','')::date, current_date);
    v_due := coalesce(p_order->>'dueDate','');
    v_material_received := coalesce(p_order->>'materialReceivedDate','');
    if v_due <> '' then perform v_due::date; end if;
    if v_material_received <> '' then perform v_material_received::date; end if;
    v_can_finance := public.has_permission(p_organization_id, 'finance.view');

    perform pg_advisory_xact_lock(hashtextextended(p_organization_id::text || ':work-order-web', 21871));
    select least(coalesce(min(legacy_id), 0) - 1, -1) into v_legacy_id
      from public.erp_records where organization_id = p_organization_id and entity_type = 'work_order';
    v_code := 'SPE-WEB-' || to_char(v_received, 'YYYY') || '-' || lpad(abs(v_legacy_id)::text, 4, '0');

    insert into public.erp_records(organization_id,module,entity_type,legacy_id,payload,created_by,updated_by)
    values (p_organization_id,'orders','work_order',v_legacy_id,
      jsonb_build_object(
        'id',v_legacy_id,'receivedDate',v_received::text,'client',trim(p_order->>'client'),
        'title',trim(p_order->>'title'),'description',coalesce(p_order->>'description',''),'images','[]'::jsonb,
        'status',coalesce(nullif(p_order->>'status',''),'RECEIVED'),'dueDate',v_due,
        'priority',coalesce(nullif(p_order->>'priority',''),'NORMAL'),
        'blocker',coalesce(nullif(p_order->>'blocker',''),'NONE'),'clientId',null,
        'orderCode',v_code,'fabricInventoryId',null,'orderedMeters',coalesce((p_order->>'orderedMeters')::numeric,0),
        'printPricePerMeter',case when v_can_finance then coalesce((p_order->>'printPricePerMeter')::numeric,0) else 0 end,
        'designCode',coalesce(p_order->>'designCode',''),'productionRecordId',null,
        'effectiveDeliveryDate','','cancelReason','','materialReceivedDate',v_material_received,
        'materialReceiptNotes',coalesce(p_order->>'materialReceiptNotes',''),'_web_pending_android',true
      ),auth.uid(),auth.uid()) returning id into v_record_id;

    insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,new_data)
    values (p_organization_id,auth.uid(),'WEB_CREATE','work_order',v_record_id,
      jsonb_build_object('legacy_id',v_legacy_id,'order_code',v_code));
    return jsonb_build_object('id',v_record_id,'legacy_id',v_legacy_id,'order_code',v_code);
end;
$$;

create or replace function public.get_web_pending_records(p_organization_id uuid)
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
  return jsonb_build_object('records', coalesce((
    select jsonb_agg(entry order by entry->>'entity_type', (entry->>'legacy_id')::bigint)
    from (
      select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload,'force_update',false) entry
      from public.erp_records r
      where r.organization_id=p_organization_id and r.deleted_at is null
        and r.entity_type in ('zentha_order','zentha_order_item','work_order')
        and r.payload->>'_web_pending_android'='true'
      union all
      select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload||o.patch,'force_update',true) entry
      from public.erp_records r join public.web_record_overlays o
        on o.record_id=r.id and o.organization_id=r.organization_id
      where r.organization_id=p_organization_id and r.deleted_at is null
        and r.entity_type in ('zentha_order','work_order') and o.android_applied_at is null
    ) pending
  ), '[]'::jsonb));
end;
$$;

create or replace function public.ack_web_changes(p_organization_id uuid)
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
      and r.entity_type in ('zentha_order','work_order') and o.patch <@ r.payload
    returning o.record_id
  ) select count(*) into v_count from applied;
  return v_count;
end;
$$;

revoke execute on function public.web_create_work_order(uuid,jsonb) from public,anon;
grant execute on function public.web_create_work_order(uuid,jsonb) to authenticated;
revoke execute on function public.get_web_pending_records(uuid) from public,anon;
grant execute on function public.get_web_pending_records(uuid) to authenticated;
revoke execute on function public.ack_web_changes(uuid) from public,anon;
grant execute on function public.ack_web_changes(uuid) to authenticated;
