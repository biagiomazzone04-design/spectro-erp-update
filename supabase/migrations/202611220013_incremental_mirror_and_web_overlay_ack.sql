-- Spectro ERP Cloud 013 — mirror incrementale e conferma affidabile delle
-- modifiche web. Migrazione additiva e idempotente: nessuna tabella, colonna o
-- record operativo viene eliminato. Compatibile con le app Android precedenti.
--
-- Problemi risolti
-- 1. Ogni pubblicazione Android riscriveva TUTTI i record del mirror (anche
--    quelli invariati, perché il payload conteneva `_workspace_revision`).
--    Il trigger di audit salvava così due copie complete di ogni record a ogni
--    sincronizzazione: database in crescita continua, pubblicazioni sempre più
--    lente e, oltre i limiti del piano Supabase, sincronizzazione bloccata.
-- 2. Il confronto dei record eliminati era quadratico (ogni riga esistente
--    scansionava l'intero array JSON in ingresso): con migliaia di record la
--    pubblicazione superava il timeout delle istruzioni e falliva.
-- 3. Gli overlay web venivano consumati solo se contenuti esattamente nel
--    payload Android. Campi esistenti soltanto sul web (es. valutazione
--    produzione) o valori normalizzati dal telefono lasciavano l'overlay attivo
--    per sempre: Android lo reimportava a ogni ciclo e ripubblicava senza fine.

-- ---------------------------------------------------------------------------
-- 1-2. Pubblicazione set-based: aggiorna solo i record realmente cambiati.
-- ---------------------------------------------------------------------------
create or replace function public.publish_workspace_snapshot(
    p_organization_id uuid,
    p_expected_revision bigint,
    p_storage_path text,
    p_sha256 text,
    p_size_bytes bigint,
    p_record_count integer,
    p_device_id uuid,
    p_records jsonb
)
returns jsonb language plpgsql security definer set search_path=public,storage as $$
declare
    v_current_revision bigint;
    v_next_revision bigint;
    v_invalid integer;
    v_changed integer;
    v_deleted integer;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
    if p_expected_revision<0 then raise exception 'Invalid expected revision'; end if;
    if p_storage_path !~ ('^'||p_organization_id::text||'/workspaces/[0-9a-f-]+[.]spectrobackup$') then raise exception 'Invalid workspace storage path'; end if;
    if p_sha256 !~ '^[0-9a-f]{64}$' then raise exception 'Invalid SHA-256'; end if;
    if p_size_bytes<=0 or p_size_bytes>52428800 then raise exception 'Invalid workspace size'; end if;
    if p_record_count<=0 then raise exception 'Invalid record count'; end if;
    if p_records is null or jsonb_typeof(p_records)<>'array' or jsonb_array_length(p_records)<>p_record_count then raise exception 'Invalid record mirror'; end if;
    if not exists(select 1 from storage.objects where bucket_id='spectro-attachments' and name=p_storage_path) then raise exception 'Uploaded workspace object not found'; end if;

    perform pg_advisory_xact_lock(hashtextextended(p_organization_id::text,908172));
    select revision into v_current_revision from public.workspace_snapshots where organization_id=p_organization_id;
    v_current_revision:=coalesce(v_current_revision,0);
    if v_current_revision<>p_expected_revision then raise exception 'SYNC_CONFLICT current_revision=% expected_revision=%',v_current_revision,p_expected_revision; end if;
    v_next_revision:=v_current_revision+1;

    -- Validazione in un solo passaggio (stesse regole della versione 010).
    select count(*) into v_invalid
    from jsonb_array_elements(p_records) x(value)
    where jsonb_typeof(x.value) <> 'object'
       or x.value->>'module' is null
       or x.value->>'entity_type' is null
       or x.value->'payload' is null
       or jsonb_typeof(x.value->'payload') <> 'object'
       or public.module_permission(x.value->>'module',false)='__denied__'
       or (x.value->>'entity_type') !~ '^[a-z][a-z0-9_]{1,79}$'
       or coalesce(x.value->>'legacy_id','') !~ '^-?[0-9]{1,18}$'
       or (x.value->>'legacy_id')::bigint = 0;
    if v_invalid > 0 then raise exception 'Invalid ERP mirror record'; end if;

    insert into public.workspace_snapshots(organization_id,revision,storage_path,sha256,size_bytes,record_count,device_id,format_version,updated_at,updated_by)
    values(p_organization_id,v_next_revision,p_storage_path,p_sha256,p_size_bytes,p_record_count,p_device_id,1,now(),auth.uid())
    on conflict(organization_id) do update set revision=excluded.revision,storage_path=excluded.storage_path,sha256=excluded.sha256,
      size_bytes=excluded.size_bytes,record_count=excluded.record_count,device_id=excluded.device_id,format_version=excluded.format_version,
      updated_at=excluded.updated_at,updated_by=excluded.updated_by;

    -- Record Android rimossi dal dispositivo: soft-delete con anti-join hash.
    with incoming as materialized (
        select distinct x.value->>'entity_type' as entity_type, (x.value->>'legacy_id')::bigint as legacy_id
        from jsonb_array_elements(p_records) x(value)
    )
    update public.erp_records existing
       set deleted_at=now(), deleted_by=auth.uid(), updated_at=now(), updated_by=auth.uid(), revision=existing.revision+1
     where existing.organization_id=p_organization_id
       and existing.payload->>'_spectro_workspace'='true'
       and existing.deleted_at is null
       and not exists (
           select 1 from incoming i
           where i.entity_type=existing.entity_type and i.legacy_id=existing.legacy_id
       );
    get diagnostics v_deleted = row_count;

    -- Upsert in blocco. `_workspace_revision` non viene più scritto nel payload:
    -- così un record invariato resta identico e NON viene riscritto (niente
    -- nuova revisione, niente riga di audit, updated_at significativo).
    with incoming as (
        select distinct on (entity_type, legacy_id) module, entity_type, legacy_id, payload
        from (
            select x.value->>'module' as module,
                   x.value->>'entity_type' as entity_type,
                   (x.value->>'legacy_id')::bigint as legacy_id,
                   ((x.value->'payload') - '_workspace_revision') || jsonb_build_object('_spectro_workspace',true) as payload,
                   x.ordinality
            from jsonb_array_elements(p_records) with ordinality x(value, ordinality)
        ) expanded
        order by entity_type, legacy_id, ordinality desc
    )
    insert into public.erp_records(organization_id,module,entity_type,legacy_id,payload,revision,created_by,updated_by,deleted_at,deleted_by)
    select p_organization_id, i.module, i.entity_type, i.legacy_id, i.payload, 1, auth.uid(), auth.uid(), null, null
    from incoming i
    on conflict(organization_id,entity_type,legacy_id) where legacy_id is not null do update set
        module=excluded.module, payload=excluded.payload, revision=public.erp_records.revision+1,
        updated_at=now(), updated_by=auth.uid(), deleted_at=null, deleted_by=null
    where public.erp_records.payload is distinct from excluded.payload
       or public.erp_records.module is distinct from excluded.module
       or public.erp_records.deleted_at is not null;
    get diagnostics v_changed = row_count;

    insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,new_data)
    values(p_organization_id,auth.uid(),'WORKSPACE_PUBLISH','workspace_snapshot',p_organization_id,
      jsonb_build_object('revision',v_next_revision,'storage_path',p_storage_path,'sha256',p_sha256,'size_bytes',p_size_bytes,
        'record_count',p_record_count,'device_id',p_device_id,'changed_records',v_changed,'deleted_records',v_deleted));
    return public.get_workspace_sync_state(p_organization_id);
end $$;

revoke execute on function public.publish_workspace_snapshot(uuid,bigint,text,text,bigint,integer,uuid,jsonb) from public,anon;
grant execute on function public.publish_workspace_snapshot(uuid,bigint,text,text,bigint,integer,uuid,jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- 3. Overlay web: invio ad Android con identità/revisione e conferma mirata.
-- ---------------------------------------------------------------------------

-- Chiavi dell'overlay che Android conosce (presenti nel payload pubblicato) e
-- che hanno un valore diverso: sono le sole che il telefono deve assorbire.
create or replace function public.web_overlay_android_delta(p_patch jsonb, p_payload jsonb)
returns jsonb language sql immutable set search_path=public as $$
    select coalesce(jsonb_object_agg(e.key, e.value), '{}'::jsonb)
    from jsonb_each(coalesce(p_patch, '{}'::jsonb)) e
    where coalesce(p_payload, '{}'::jsonb) ? e.key
      and (p_payload->e.key) is distinct from e.value
$$;

create or replace function public.get_web_pending_records(p_organization_id uuid)
returns jsonb language plpgsql stable security definer set search_path=public as $$
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
  return jsonb_build_object('records',coalesce((select jsonb_agg(entry order by entry->>'entity_type',(entry->>'legacy_id')::bigint) from (
    select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload,'force_update',false,
                              'record_id',r.id) entry
    from public.erp_records r
    where r.organization_id=p_organization_id and r.deleted_at is null
      and r.entity_type in ('zentha_order','zentha_order_item','work_order','production_record')
      and r.payload->>'_web_pending_android'='true'
    union all
    select jsonb_build_object('entity_type',r.entity_type,'legacy_id',r.legacy_id,'payload',r.payload||o.patch,'force_update',true,
                              'record_id',r.id,'overlay_revision',o.revision) entry
    from public.erp_records r
    join public.web_record_overlays o on o.record_id=r.id and o.organization_id=r.organization_id
    where r.organization_id=p_organization_id and r.deleted_at is null
      and r.entity_type in ('zentha_order','work_order','production_record','operational_task')
      and o.android_applied_at is null
      and public.web_overlay_android_delta(o.patch, r.payload) <> '{}'::jsonb
  ) pending),'[]'::jsonb));
end $$;

-- Conferma dopo una pubblicazione Android riuscita.
-- p_applied = [{"record_id": "<uuid>", "overlay_revision": <n>}, ...]
-- Per ogni overlay importato (revisione non successiva a quella importata)
-- rimuove le chiavi che Android gestisce: il valore canonico è ora quello del
-- telefono. Le chiavi esistenti solo sul web restano nell'overlay. Un overlay
-- modificato dopo l'import (revisione maggiore) non viene toccato.
create or replace function public.ack_web_changes_v2(p_organization_id uuid, p_applied jsonb)
returns integer language plpgsql security definer set search_path=public as $$
declare
    v_count integer := 0;
    v_removed integer := 0;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id,'sync.use') then raise exception 'Permission denied'; end if;
    if p_applied is not null and jsonb_typeof(p_applied) <> 'array' then raise exception 'Invalid applied overlay list'; end if;

    with applied as (
        select (a.value->>'record_id')::uuid as record_id, max((a.value->>'overlay_revision')::bigint) as revision
        from jsonb_array_elements(coalesce(p_applied,'[]'::jsonb)) a(value)
        where coalesce(a.value->>'record_id','') ~ '^[0-9a-f-]{36}$'
          and coalesce(a.value->>'overlay_revision','') ~ '^[0-9]{1,18}$'
        group by 1
    )
    update public.web_record_overlays o
       set patch = coalesce((
               select jsonb_object_agg(e.key, e.value)
               from jsonb_each(o.patch) e
               where not (r.payload ? e.key)
           ), '{}'::jsonb)
      from applied a, public.erp_records r
     where o.organization_id = p_organization_id
       and o.record_id = a.record_id
       and o.revision <= a.revision
       and r.id = o.record_id
       and r.organization_id = p_organization_id;
    get diagnostics v_count = row_count;

    -- Compattazione generale e sicura: chiavi identiche al payload non
    -- cambiano nulla di ciò che il web mostra e possono essere rimosse.
    update public.web_record_overlays o
       set patch = coalesce((
               select jsonb_object_agg(e.key, e.value)
               from jsonb_each(o.patch) e
               where (r.payload->e.key) is distinct from e.value
           ), '{}'::jsonb)
      from public.erp_records r
     where o.organization_id = p_organization_id
       and r.id = o.record_id
       and r.organization_id = p_organization_id
       and exists (select 1 from jsonb_each(o.patch) e where (r.payload->e.key) is not distinct from e.value);

    delete from public.web_record_overlays
     where organization_id = p_organization_id and patch = '{}'::jsonb;
    get diagnostics v_removed = row_count;

    return v_count + v_removed;
end $$;

revoke execute on function public.web_overlay_android_delta(jsonb,jsonb) from public,anon;
grant execute on function public.web_overlay_android_delta(jsonb,jsonb) to authenticated;
revoke execute on function public.get_web_pending_records(uuid) from public,anon;
grant execute on function public.get_web_pending_records(uuid) to authenticated;
revoke execute on function public.ack_web_changes_v2(uuid,jsonb) from public,anon;
grant execute on function public.ack_web_changes_v2(uuid,jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. Manutenzione opzionale (NON eseguita automaticamente).
-- Le pubblicazioni precedenti alla 013 hanno generato righe di audit con copie
-- complete del mirror. L'amministratore può eliminarle dopo N giorni, a
-- partire da 30. Le azioni web, di accesso e di pubblicazione restano intatte.
-- ---------------------------------------------------------------------------
create or replace function public.admin_prune_mirror_audit(p_organization_id uuid, p_keep_days integer default 90)
returns integer language plpgsql security definer set search_path=public as $$
declare v_count integer;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id,'users.manage') then raise exception 'Permission denied'; end if;
    if p_keep_days is null or p_keep_days < 30 then raise exception 'Keep at least 30 days of audit history'; end if;
    delete from public.audit_log
     where organization_id = p_organization_id
       and action in ('INSERT','UPDATE')
       and created_at < now() - make_interval(days => p_keep_days);
    get diagnostics v_count = row_count;
    insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,new_data)
    values(p_organization_id,auth.uid(),'AUDIT_PRUNED','audit_log',p_organization_id,
           jsonb_build_object('removed_rows',v_count,'keep_days',p_keep_days));
    return v_count;
end $$;

revoke execute on function public.admin_prune_mirror_audit(uuid,integer) from public,anon;
grant execute on function public.admin_prune_mirror_audit(uuid,integer) to authenticated;
