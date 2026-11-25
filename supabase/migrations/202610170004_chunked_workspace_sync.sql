-- Spectro ERP Cloud — workspace oltre 50 MB tramite manifest e blocchi verificati.
-- Migrazione additiva e ripetibile: i workspace formato 1 restano scaricabili.

alter table public.workspace_snapshots
    drop constraint if exists workspace_snapshots_size_bytes_check;
alter table public.workspace_snapshots
    add constraint workspace_snapshots_size_bytes_check
    check (size_bytes > 0 and size_bytes <= 1073741824);

alter table public.workspace_snapshots
    drop constraint if exists workspace_snapshots_format_version_check;
alter table public.workspace_snapshots
    add constraint workspace_snapshots_format_version_check
    check (format_version in (1, 2));

create or replace function public.publish_workspace_snapshot_v2(
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
    p_records jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v_path text;
    v_chunk_count integer;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id, 'sync.use') then raise exception 'Permission denied'; end if;
    if p_manifest_path !~ ('^' || p_organization_id::text || '/workspaces/[0-9a-f-]+[.]spectrobackup$') then
        raise exception 'Invalid workspace manifest path';
    end if;
    if p_manifest_sha256 !~ '^[0-9a-f]{64}$' or p_archive_sha256 !~ '^[0-9a-f]{64}$' then
        raise exception 'Invalid SHA-256';
    end if;
    if p_manifest_size_bytes <= 0 or p_manifest_size_bytes > 52428800 then
        raise exception 'Invalid workspace manifest size';
    end if;
    if p_archive_size_bytes <= 0 or p_archive_size_bytes > 1073741824 then
        raise exception 'Invalid workspace archive size';
    end if;
    if p_chunk_paths is null or jsonb_typeof(p_chunk_paths) <> 'array' then
        raise exception 'Invalid workspace chunk list';
    end if;

    v_chunk_count := jsonb_array_length(p_chunk_paths);
    if v_chunk_count < 1 or v_chunk_count > 24 then
        raise exception 'Invalid workspace chunk count';
    end if;
    if (select count(distinct value) from jsonb_array_elements_text(p_chunk_paths)) <> v_chunk_count then
        raise exception 'Duplicate workspace chunks';
    end if;
    if not exists (
        select 1 from storage.objects
        where bucket_id = 'spectro-attachments' and name = p_manifest_path
    ) then raise exception 'Uploaded workspace manifest not found'; end if;

    for v_path in select value from jsonb_array_elements_text(p_chunk_paths)
    loop
        if v_path !~ ('^' || p_organization_id::text || '/workspaces/[0-9a-f-]+-[0-9]{4}[.]spectrochunk$') then
            raise exception 'Invalid workspace chunk path';
        end if;
        if not exists (
            select 1 from storage.objects
            where bucket_id = 'spectro-attachments' and name = v_path
        ) then raise exception 'Uploaded workspace chunk not found'; end if;
    end loop;

    -- Riusa la transazione, il controllo di revisione e il mirror ERP già
    -- collaudati dal formato 1. Il manifest è un oggetto inferiore a 50 MB.
    perform public.publish_workspace_snapshot(
        p_organization_id,
        p_expected_revision,
        p_manifest_path,
        p_manifest_sha256,
        p_manifest_size_bytes,
        p_record_count,
        p_device_id,
        p_records
    );

    update public.workspace_snapshots
    set sha256 = p_archive_sha256,
        size_bytes = p_archive_size_bytes,
        format_version = 2
    where organization_id = p_organization_id
      and updated_by = auth.uid()
      and storage_path = p_manifest_path;

    if not found then raise exception 'Workspace metadata update failed'; end if;

    insert into public.audit_log(
        organization_id, actor_id, action, entity_type, entity_id, new_data
    ) values (
        p_organization_id, auth.uid(), 'WORKSPACE_CHUNKED', 'workspace_snapshot', p_organization_id,
        jsonb_build_object(
            'revision', p_expected_revision + 1,
            'manifest_path', p_manifest_path,
            'archive_sha256', p_archive_sha256,
            'archive_size_bytes', p_archive_size_bytes,
            'chunk_count', v_chunk_count
        )
    );

    return public.get_workspace_sync_state(p_organization_id);
end;
$$;

revoke execute on function public.publish_workspace_snapshot_v2(
    uuid, bigint, text, text, bigint, text, bigint, jsonb, integer, uuid, jsonb
) from public, anon;
grant execute on function public.publish_workspace_snapshot_v2(
    uuid, bigint, text, text, bigint, text, bigint, jsonb, integer, uuid, jsonb
) to authenticated;

