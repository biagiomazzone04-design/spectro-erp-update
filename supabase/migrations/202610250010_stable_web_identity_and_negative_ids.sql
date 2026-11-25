-- Spectro ERP Cloud 010 — identità stabile dei record creati dal web.
-- Gli ID negativi sono riservati ai record creati sul portale e impediscono
-- collisioni con gli AUTOINCREMENT Android. Zero resta sempre non valido.

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
    v_current_revision bigint; v_next_revision bigint; v_record jsonb;
    v_module text; v_entity_type text; v_legacy_id bigint; v_payload jsonb;
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

    for v_record in select value from jsonb_array_elements(p_records) loop
        v_module:=v_record->>'module'; v_entity_type:=v_record->>'entity_type';
        v_legacy_id:=(v_record->>'legacy_id')::bigint; v_payload:=v_record->'payload';
        if v_module is null or v_entity_type is null or v_payload is null
           or public.module_permission(v_module,false)='__denied__'
           or v_entity_type !~ '^[a-z][a-z0-9_]{1,79}$'
           or v_legacy_id=0 or jsonb_typeof(v_payload)<>'object' then
            raise exception 'Invalid ERP mirror record';
        end if;
    end loop;

    insert into public.workspace_snapshots(organization_id,revision,storage_path,sha256,size_bytes,record_count,device_id,format_version,updated_at,updated_by)
    values(p_organization_id,v_next_revision,p_storage_path,p_sha256,p_size_bytes,p_record_count,p_device_id,1,now(),auth.uid())
    on conflict(organization_id) do update set revision=excluded.revision,storage_path=excluded.storage_path,sha256=excluded.sha256,
      size_bytes=excluded.size_bytes,record_count=excluded.record_count,device_id=excluded.device_id,format_version=excluded.format_version,
      updated_at=excluded.updated_at,updated_by=excluded.updated_by;

    update public.erp_records existing set deleted_at=now(),deleted_by=auth.uid(),updated_at=now(),updated_by=auth.uid(),revision=existing.revision+1
    where existing.organization_id=p_organization_id and existing.payload->>'_spectro_workspace'='true' and existing.deleted_at is null
      and not exists(select 1 from jsonb_array_elements(p_records) candidate
        where candidate->>'entity_type'=existing.entity_type and (candidate->>'legacy_id')::bigint=existing.legacy_id);

    for v_record in select value from jsonb_array_elements(p_records) loop
        v_module:=v_record->>'module'; v_entity_type:=v_record->>'entity_type'; v_legacy_id:=(v_record->>'legacy_id')::bigint;
        v_payload:=(v_record->'payload')||jsonb_build_object('_spectro_workspace',true,'_workspace_revision',v_next_revision);
        insert into public.erp_records(organization_id,module,entity_type,legacy_id,payload,revision,created_by,updated_by,deleted_at,deleted_by)
        values(p_organization_id,v_module,v_entity_type,v_legacy_id,v_payload,1,auth.uid(),auth.uid(),null,null)
        on conflict(organization_id,entity_type,legacy_id) where legacy_id is not null do update set
          module=excluded.module,payload=excluded.payload,revision=public.erp_records.revision+1,updated_at=now(),updated_by=auth.uid(),deleted_at=null,deleted_by=null;
    end loop;

    insert into public.audit_log(organization_id,actor_id,action,entity_type,entity_id,new_data)
    values(p_organization_id,auth.uid(),'WORKSPACE_PUBLISH','workspace_snapshot',p_organization_id,
      jsonb_build_object('revision',v_next_revision,'storage_path',p_storage_path,'sha256',p_sha256,'size_bytes',p_size_bytes,'record_count',p_record_count,'device_id',p_device_id));
    return public.get_workspace_sync_state(p_organization_id);
end $$;

revoke execute on function public.publish_workspace_snapshot(uuid,bigint,text,text,bigint,integer,uuid,jsonb) from public,anon;
grant execute on function public.publish_workspace_snapshot(uuid,bigint,text,text,bigint,integer,uuid,jsonb) to authenticated;
