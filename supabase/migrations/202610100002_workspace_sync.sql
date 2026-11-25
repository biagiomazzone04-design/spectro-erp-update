-- Spectro ERP Cloud — safe, revisioned whole-workspace synchronization.
-- This migration is additive: it never drops or rewrites existing ERP data.

insert into public.permissions(key, module, label, description) values
('sync.use','sync','Sincronizzazione cloud','Pubblica e scarica il workspace aziendale versionato')
on conflict (key) do update set
    module = excluded.module,
    label = excluded.label,
    description = excluded.description;

-- Existing Admin roles receive the new capability. Other roles can receive it
-- explicitly from the user/permission administration screen.
insert into public.role_permissions(role_id, permission_key)
select r.id, 'sync.use'
from public.roles r
where r.is_system and r.name = 'Admin'
on conflict do nothing;

create table if not exists public.workspace_snapshots (
    organization_id uuid primary key references public.organizations(id) on delete cascade,
    revision bigint not null check (revision > 0),
    storage_path text not null,
    sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
    size_bytes bigint not null check (size_bytes > 0 and size_bytes <= 52428800),
    record_count integer not null check (record_count > 0),
    device_id uuid not null,
    format_version integer not null default 1 check (format_version = 1),
    updated_at timestamptz not null default now(),
    updated_by uuid not null references auth.users(id)
);

alter table public.workspace_snapshots enable row level security;
revoke all on public.workspace_snapshots from anon, authenticated;

create or replace function public.get_workspace_sync_state(p_organization_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_result jsonb;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id, 'sync.use') then raise exception 'Permission denied'; end if;

    select jsonb_build_object(
        'exists', true,
        'revision', w.revision,
        'storage_path', w.storage_path,
        'sha256', w.sha256,
        'size_bytes', w.size_bytes,
        'record_count', w.record_count,
        'device_id', w.device_id,
        'format_version', w.format_version,
        'updated_at', w.updated_at,
        'updated_by', coalesce(nullif(p.display_name, ''), p.email, '')
    )
    into v_result
    from public.workspace_snapshots w
    left join public.profiles p on p.id = w.updated_by
    where w.organization_id = p_organization_id;

    return coalesce(v_result, jsonb_build_object(
        'exists', false,
        'revision', 0,
        'size_bytes', 0,
        'record_count', 0,
        'format_version', 1
    ));
end;
$$;

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
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v_current_revision bigint;
    v_next_revision bigint;
    v_record jsonb;
    v_module text;
    v_entity_type text;
    v_legacy_id bigint;
    v_payload jsonb;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if not public.has_permission(p_organization_id, 'sync.use') then raise exception 'Permission denied'; end if;
    if p_expected_revision < 0 then raise exception 'Invalid expected revision'; end if;
    if p_storage_path !~ ('^' || p_organization_id::text || '/workspaces/[0-9a-f-]+[.]spectrobackup$') then
        raise exception 'Invalid workspace storage path';
    end if;
    if p_sha256 !~ '^[0-9a-f]{64}$' then raise exception 'Invalid SHA-256'; end if;
    if p_size_bytes <= 0 or p_size_bytes > 52428800 then raise exception 'Invalid workspace size'; end if;
    if p_record_count <= 0 then raise exception 'Invalid record count'; end if;
    if p_records is null or jsonb_typeof(p_records) <> 'array' or jsonb_array_length(p_records) <> p_record_count then
        raise exception 'Invalid record mirror';
    end if;
    if not exists (
        select 1 from storage.objects
        where bucket_id = 'spectro-attachments' and name = p_storage_path
    ) then raise exception 'Uploaded workspace object not found'; end if;

    perform pg_advisory_xact_lock(hashtextextended(p_organization_id::text, 908172));
    select revision into v_current_revision
    from public.workspace_snapshots
    where organization_id = p_organization_id;
    v_current_revision := coalesce(v_current_revision, 0);

    if v_current_revision <> p_expected_revision then
        raise exception 'SYNC_CONFLICT current_revision=% expected_revision=%', v_current_revision, p_expected_revision;
    end if;

    v_next_revision := v_current_revision + 1;

    -- Validate the complete mirror before mutating workspace metadata.
    for v_record in select value from jsonb_array_elements(p_records)
    loop
        v_module := v_record ->> 'module';
        v_entity_type := v_record ->> 'entity_type';
        v_legacy_id := (v_record ->> 'legacy_id')::bigint;
        v_payload := v_record -> 'payload';
        if v_module is null
           or v_entity_type is null
           or v_payload is null
           or public.module_permission(v_module, false) = '__denied__'
           or v_entity_type !~ '^[a-z][a-z0-9_]{1,79}$'
           or v_legacy_id <= 0
           or jsonb_typeof(v_payload) <> 'object' then
            raise exception 'Invalid ERP mirror record';
        end if;
    end loop;

    insert into public.workspace_snapshots(
        organization_id, revision, storage_path, sha256, size_bytes,
        record_count, device_id, format_version, updated_at, updated_by
    ) values (
        p_organization_id, v_next_revision, p_storage_path, p_sha256, p_size_bytes,
        p_record_count, p_device_id, 1, now(), auth.uid()
    )
    on conflict (organization_id) do update set
        revision = excluded.revision,
        storage_path = excluded.storage_path,
        sha256 = excluded.sha256,
        size_bytes = excluded.size_bytes,
        record_count = excluded.record_count,
        device_id = excluded.device_id,
        format_version = excluded.format_version,
        updated_at = excluded.updated_at,
        updated_by = excluded.updated_by;

    -- Records removed on Android are soft-deleted. Rows still present are
    -- upserted under the existing optimistic-revision envelope so the web app
    -- can enforce module-specific RLS without downloading the private archive.
    update public.erp_records existing
    set deleted_at = now(),
        deleted_by = auth.uid(),
        updated_at = now(),
        updated_by = auth.uid(),
        revision = existing.revision + 1
    where existing.organization_id = p_organization_id
      and existing.payload ->> '_spectro_workspace' = 'true'
      and existing.deleted_at is null
      and not exists (
          select 1
          from jsonb_array_elements(p_records) candidate
          where candidate ->> 'entity_type' = existing.entity_type
            and (candidate ->> 'legacy_id')::bigint = existing.legacy_id
      );

    for v_record in select value from jsonb_array_elements(p_records)
    loop
        v_module := v_record ->> 'module';
        v_entity_type := v_record ->> 'entity_type';
        v_legacy_id := (v_record ->> 'legacy_id')::bigint;
        v_payload := (v_record -> 'payload') || jsonb_build_object(
            '_spectro_workspace', true,
            '_workspace_revision', v_next_revision
        );

        insert into public.erp_records(
            organization_id, module, entity_type, legacy_id, payload,
            revision, created_by, updated_by, deleted_at, deleted_by
        ) values (
            p_organization_id, v_module, v_entity_type, v_legacy_id, v_payload,
            1, auth.uid(), auth.uid(), null, null
        )
        on conflict (organization_id, entity_type, legacy_id) where legacy_id is not null
        do update set
            module = excluded.module,
            payload = excluded.payload,
            revision = public.erp_records.revision + 1,
            updated_at = now(),
            updated_by = auth.uid(),
            deleted_at = null,
            deleted_by = null;
    end loop;

    insert into public.audit_log(
        organization_id, actor_id, action, entity_type, entity_id, new_data
    ) values (
        p_organization_id, auth.uid(), 'WORKSPACE_PUBLISH', 'workspace_snapshot', p_organization_id,
        jsonb_build_object(
            'revision', v_next_revision,
            'storage_path', p_storage_path,
            'sha256', p_sha256,
            'size_bytes', p_size_bytes,
            'record_count', p_record_count,
            'device_id', p_device_id
        )
    );

    return public.get_workspace_sync_state(p_organization_id);
end;
$$;

revoke execute on function public.get_workspace_sync_state(uuid) from public, anon;
revoke execute on function public.publish_workspace_snapshot(uuid, bigint, text, text, bigint, integer, uuid, jsonb) from public, anon;
grant execute on function public.get_workspace_sync_state(uuid) to authenticated;
grant execute on function public.publish_workspace_snapshot(uuid, bigint, text, text, bigint, integer, uuid, jsonb) to authenticated;

-- Ordinary members can read module-filtered mirror rows, not the complete
-- workspace archive which may contain modules outside their role.
drop policy if exists "spectro_files_read" on storage.objects;
create policy "spectro_files_read" on storage.objects for select to authenticated
using (
    bucket_id = 'spectro-attachments'
    and public.is_org_member((storage.foldername(name))[1]::uuid)
    and (
        coalesce((storage.foldername(name))[2], '') <> 'workspaces'
        or public.has_permission((storage.foldername(name))[1]::uuid, 'sync.use')
    )
);

drop policy if exists "spectro_workspace_files_delete" on storage.objects;
create policy "spectro_workspace_files_delete" on storage.objects for delete to authenticated
using (
    bucket_id = 'spectro-attachments'
    and (storage.foldername(name))[2] = 'workspaces'
    and public.has_permission((storage.foldername(name))[1]::uuid, 'sync.use')
);
