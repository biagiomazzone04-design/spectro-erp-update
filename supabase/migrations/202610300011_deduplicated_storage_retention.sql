-- Spectro ERP Cloud — allegati deduplicati e conservazione della sola
-- revisione workspace attiva. La cancellazione fisica viene eseguita tramite
-- Storage API dall'app soltanto dopo il commit atomico di questa funzione.

alter table public.workspace_snapshots
    add column if not exists chunk_paths jsonb not null default '[]'::jsonb,
    add column if not exists attachment_paths jsonb not null default '[]'::jsonb;

create or replace function public.publish_workspace_snapshot_v3(
    p_organization_id uuid,
    p_expected_revision bigint,
    p_manifest_path text,
    p_manifest_sha256 text,
    p_manifest_size_bytes bigint,
    p_archive_sha256 text,
    p_archive_size_bytes bigint,
    p_chunk_paths jsonb,
    p_record_count integer,
    p_device_id uuid,
    p_records jsonb,
    p_attachment_paths jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v_path text;
    v_attachment_count integer;
    v_cleanup_paths jsonb := '[]'::jsonb;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id, 'sync.use') then raise exception 'Permission denied'; end if;
    if p_attachment_paths is null or jsonb_typeof(p_attachment_paths) <> 'array' then
        raise exception 'Invalid attachment path list';
    end if;

    v_attachment_count := jsonb_array_length(p_attachment_paths);
    if v_attachment_count > 10000 then raise exception 'Too many attachment paths'; end if;
    if (select count(distinct value) from jsonb_array_elements_text(p_attachment_paths)) <> v_attachment_count then
        raise exception 'Duplicate attachment paths';
    end if;

    for v_path in select value from jsonb_array_elements_text(p_attachment_paths)
    loop
        if v_path !~ ('^' || p_organization_id::text || '/attachments/by-sha256/[0-9a-f]{64}$') then
            raise exception 'Invalid content-addressed attachment path';
        end if;
        if not exists (
            select 1 from storage.objects
            where bucket_id = 'spectro-attachments' and name = v_path
        ) then raise exception 'Uploaded attachment not found'; end if;
    end loop;

    -- Riusa controlli di integrità, concorrenza e mirror già collaudati.
    perform public.publish_workspace_snapshot_v2(
        p_organization_id,
        p_expected_revision,
        p_manifest_path,
        p_manifest_sha256,
        p_manifest_size_bytes,
        p_archive_sha256,
        p_archive_size_bytes,
        p_chunk_paths,
        p_record_count,
        p_device_id,
        p_records
    );

    update public.workspace_snapshots
    set chunk_paths = p_chunk_paths,
        attachment_paths = p_attachment_paths
    where organization_id = p_organization_id
      and updated_by = auth.uid()
      and storage_path = p_manifest_path;

    if not found then raise exception 'Workspace retention metadata update failed'; end if;

    -- Restituisce soltanto percorsi appartenenti all'organizzazione e non più
    -- referenziati. La funzione non elimina mai direttamente storage.objects.
    select coalesce(jsonb_agg(o.name order by o.name), '[]'::jsonb)
    into v_cleanup_paths
    from storage.objects o
    where o.bucket_id = 'spectro-attachments'
      and (
          o.name like p_organization_id::text || '/workspaces/%'
          or o.name like p_organization_id::text || '/attachments/%'
      )
      and o.name <> p_manifest_path
      and not exists (
          select 1 from jsonb_array_elements_text(p_chunk_paths) kept
          where kept.value = o.name
      )
      and not exists (
          select 1 from jsonb_array_elements_text(p_attachment_paths) kept
          where kept.value = o.name
      );

    insert into public.audit_log(
        organization_id, actor_id, action, entity_type, entity_id, new_data
    ) values (
        p_organization_id, auth.uid(), 'CLOUD_STORAGE_COMPACTED',
        'workspace_snapshot', p_organization_id,
        jsonb_build_object(
            'revision', p_expected_revision + 1,
            'attachment_count', v_attachment_count,
            'cleanup_count', jsonb_array_length(v_cleanup_paths)
        )
    );

    return public.get_workspace_sync_state(p_organization_id)
        || jsonb_build_object('cleanup_paths', v_cleanup_paths);
end;
$$;

revoke execute on function public.publish_workspace_snapshot_v3(
    uuid, bigint, text, text, bigint, text, bigint, jsonb, integer, uuid, jsonb, jsonb
) from public, anon;
grant execute on function public.publish_workspace_snapshot_v3(
    uuid, bigint, text, text, bigint, text, bigint, jsonb, integer, uuid, jsonb, jsonb
) to authenticated;

