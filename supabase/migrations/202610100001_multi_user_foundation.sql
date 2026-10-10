-- Spectro ERP Cloud — multi-tenant authentication, RBAC, RLS and audit foundation.
-- Safe to run on a new Supabase project. No existing ERP table is dropped.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null default '',
    display_name text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.organizations (
    id uuid primary key default gen_random_uuid(),
    name text not null check (length(trim(name)) between 2 and 120),
    created_at timestamptz not null default now(),
    created_by uuid references auth.users(id)
);

create table if not exists public.permissions (
    key text primary key,
    module text not null,
    label text not null,
    description text not null default ''
);

create table if not exists public.roles (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    name text not null,
    description text not null default '',
    is_system boolean not null default false,
    created_at timestamptz not null default now(),
    unique (organization_id, name)
);

create table if not exists public.role_permissions (
    role_id uuid not null references public.roles(id) on delete cascade,
    permission_key text not null references public.permissions(key) on delete cascade,
    primary key (role_id, permission_key)
);

create table if not exists public.organization_members (
    organization_id uuid not null references public.organizations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role_id uuid not null references public.roles(id),
    status text not null default 'active' check (status in ('active', 'suspended')),
    joined_at timestamptz not null default now(),
    invited_by uuid references auth.users(id),
    primary key (organization_id, user_id)
);

create table if not exists public.member_permission_overrides (
    organization_id uuid not null,
    user_id uuid not null,
    permission_key text not null references public.permissions(key) on delete cascade,
    allowed boolean not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, user_id, permission_key),
    foreign key (organization_id, user_id)
        references public.organization_members(organization_id, user_id) on delete cascade
);

-- Generic cloud record envelope. It preserves the current Android entities while
-- UUID-based domain tables are introduced module-by-module. revision enables
-- optimistic concurrency and deleted_at provides non-destructive deletion.
create table if not exists public.erp_records (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    module text not null,
    entity_type text not null,
    legacy_id bigint,
    payload jsonb not null default '{}'::jsonb,
    revision bigint not null default 1 check (revision > 0),
    created_at timestamptz not null default now(),
    created_by uuid not null default auth.uid() references auth.users(id),
    updated_at timestamptz not null default now(),
    updated_by uuid not null default auth.uid() references auth.users(id),
    deleted_at timestamptz,
    deleted_by uuid references auth.users(id),
    check (module in ('clients','orders','production','inventory','issues','cotton','zentha','finance','tasks','settings'))
);

create unique index if not exists erp_records_legacy_key
    on public.erp_records (organization_id, entity_type, legacy_id)
    where legacy_id is not null;
create index if not exists erp_records_active_lookup
    on public.erp_records (organization_id, module, entity_type, updated_at desc)
    where deleted_at is null;

create table if not exists public.erp_attachments (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(id) on delete cascade,
    record_id uuid references public.erp_records(id) on delete set null,
    storage_path text not null,
    file_name text not null,
    mime_type text not null default 'application/octet-stream',
    size_bytes bigint not null default 0 check (size_bytes >= 0),
    created_at timestamptz not null default now(),
    created_by uuid not null default auth.uid() references auth.users(id),
    unique (organization_id, storage_path)
);

create table if not exists public.audit_log (
    id bigint generated always as identity primary key,
    organization_id uuid not null references public.organizations(id) on delete cascade,
    actor_id uuid references auth.users(id),
    action text not null,
    entity_type text not null,
    entity_id uuid,
    old_data jsonb,
    new_data jsonb,
    created_at timestamptz not null default now()
);
create index if not exists audit_log_org_created on public.audit_log (organization_id, created_at desc);

insert into public.permissions(key, module, label, description) values
('dashboard.view','dashboard','Dashboard','Visualizza dashboard e indicatori'),
('clients.view','clients','Clienti: lettura','Visualizza anagrafiche e storico clienti'),
('clients.manage','clients','Clienti: gestione','Crea e modifica clienti'),
('orders.view','orders','Ordini: lettura','Visualizza ordini e timeline'),
('orders.manage','orders','Ordini: gestione','Crea, modifica e chiude ordini'),
('production.view','production','Produzione: lettura','Visualizza lavorazioni'),
('production.manage','production','Produzione: gestione','Registra e modifica lavorazioni'),
('inventory.view','inventory','Magazzino: lettura','Visualizza scorte e movimenti'),
('inventory.manage','inventory','Magazzino: gestione','Gestisce carichi, colli e rettifiche'),
('issues.view','issues','Criticità: lettura','Visualizza criticità'),
('issues.manage','issues','Criticità: gestione','Gestisce criticità e risoluzioni'),
('cotton.view','cotton','Cotone: lettura','Visualizza ordini cotone'),
('cotton.manage','cotton','Cotone: gestione','Gestisce ordini cotone'),
('zentha.view','zentha','Zentha: lettura','Visualizza area Zentha'),
('zentha.manage','zentha','Zentha: gestione','Gestisce catalogo e ordini Zentha'),
('finance.view','finance','Economia: lettura','Visualizza fatturato, costi, utili e pagamenti'),
('finance.manage','finance','Economia: gestione','Gestisce costi, ricavi e pagamenti'),
('tasks.view','tasks','Attività: lettura','Visualizza attività operative'),
('tasks.manage','tasks','Attività: gestione','Gestisce attività operative'),
('reports.export','reports','Esportazione report','Esporta PDF e report'),
('backup.manage','backup','Backup','Esporta, ripristina o azzera archivi'),
('settings.manage','settings','Impostazioni','Modifica impostazioni aziendali'),
('users.manage','users','Utenti e ruoli','Gestisce utenti, ruoli e permessi'),
('audit.view','audit','Registro attività','Consulta il registro delle modifiche')
on conflict (key) do update set
    module = excluded.module,
    label = excluded.label,
    description = excluded.description;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles(id, email, display_name)
    values (
        new.id,
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data ->> 'display_name', split_part(coalesce(new.email, ''), '@', 1))
    )
    on conflict (id) do update set
        email = excluded.email,
        display_name = case when public.profiles.display_name = '' then excluded.display_name else public.profiles.display_name end,
        updated_at = now();
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert or update of email, raw_user_meta_data on auth.users
for each row execute procedure public.handle_new_user();

insert into public.profiles(id, email, display_name)
select
    u.id,
    coalesce(u.email, ''),
    coalesce(u.raw_user_meta_data ->> 'display_name', split_part(coalesce(u.email, ''), '@', 1))
from auth.users u
on conflict (id) do update set
    email = excluded.email,
    display_name = case when public.profiles.display_name = '' then excluded.display_name else public.profiles.display_name end,
    updated_at = now();

create or replace function public.is_org_member(p_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.organization_members m
        where m.organization_id = p_organization_id
          and m.user_id = auth.uid()
          and m.status = 'active'
    );
$$;

create or replace function public.has_permission(p_organization_id uuid, p_permission_key text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(
        (
            select o.allowed
            from public.member_permission_overrides o
            join public.organization_members m
              on m.organization_id = o.organization_id and m.user_id = o.user_id
            where o.organization_id = p_organization_id
              and o.user_id = auth.uid()
              and o.permission_key = p_permission_key
              and m.status = 'active'
        ),
        exists (
            select 1
            from public.organization_members m
            join public.role_permissions rp on rp.role_id = m.role_id
            where m.organization_id = p_organization_id
              and m.user_id = auth.uid()
              and m.status = 'active'
              and rp.permission_key = p_permission_key
        ),
        false
    );
$$;

create or replace function public.module_permission(p_module text, p_write boolean default false)
returns text
language sql
immutable
as $$
    select case p_module
        when 'clients' then case when p_write then 'clients.manage' else 'clients.view' end
        when 'orders' then case when p_write then 'orders.manage' else 'orders.view' end
        when 'production' then case when p_write then 'production.manage' else 'production.view' end
        when 'inventory' then case when p_write then 'inventory.manage' else 'inventory.view' end
        when 'issues' then case when p_write then 'issues.manage' else 'issues.view' end
        when 'cotton' then case when p_write then 'cotton.manage' else 'cotton.view' end
        when 'zentha' then case when p_write then 'zentha.manage' else 'zentha.view' end
        when 'finance' then case when p_write then 'finance.manage' else 'finance.view' end
        when 'tasks' then case when p_write then 'tasks.manage' else 'tasks.view' end
        when 'settings' then 'settings.manage'
        else '__denied__'
    end;
$$;

create or replace function public.my_access_context()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
    with membership as (
        select m.organization_id, m.role_id, o.name organization_name, r.name role_name
        from public.organization_members m
        join public.organizations o on o.id = m.organization_id
        join public.roles r on r.id = m.role_id
        where m.user_id = auth.uid() and m.status = 'active'
        order by m.joined_at
        limit 1
    ), effective_permissions as (
        select p.key
        from membership m
        cross join public.permissions p
        where public.has_permission(m.organization_id, p.key)
    )
    select jsonb_build_object(
        'user_id', auth.uid(),
        'display_name', coalesce(pr.display_name, ''),
        'organization_id', m.organization_id,
        'organization_name', m.organization_name,
        'role_name', m.role_name,
        'permissions', coalesce((select jsonb_agg(ep.key order by ep.key) from effective_permissions ep), '[]'::jsonb)
    )
    from public.profiles pr
    left join membership m on true
    where pr.id = auth.uid();
$$;

-- One-time, race-safe bootstrap. Only the first authenticated user can create
-- the initial organization; that user receives the complete Admin role.
create or replace function public.bootstrap_spectro_organization(p_name text default 'Spectro')
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_org uuid;
    v_admin_role uuid;
    v_role uuid;
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    perform pg_advisory_xact_lock(73190842);
    if exists (select 1 from public.organizations) then
        raise exception 'An organization already exists';
    end if;

    insert into public.organizations(name, created_by) values (trim(p_name), auth.uid()) returning id into v_org;
    insert into public.roles(organization_id, name, description, is_system)
    values (v_org, 'Admin', 'Accesso completo', true) returning id into v_admin_role;
    insert into public.role_permissions(role_id, permission_key)
    select v_admin_role, key from public.permissions;

    insert into public.roles(organization_id, name, description, is_system)
    values (v_org, 'Produzione', 'Ordini, produzione, magazzino, criticità e attività', true) returning id into v_role;
    insert into public.role_permissions(role_id, permission_key)
    select v_role, unnest(array['clients.view','orders.view','orders.manage','production.view','production.manage','inventory.view','inventory.manage','issues.view','issues.manage','tasks.view','tasks.manage','reports.export']);

    insert into public.roles(organization_id, name, description, is_system)
    values (v_org, 'Commerciale', 'Clienti, ordini, dashboard ed economia', true) returning id into v_role;
    insert into public.role_permissions(role_id, permission_key)
    select v_role, unnest(array['dashboard.view','clients.view','clients.manage','orders.view','orders.manage','production.view','finance.view','finance.manage','tasks.view','reports.export']);

    insert into public.roles(organization_id, name, description, is_system)
    values (v_org, 'Zentha', 'Area Zentha e clienti', true) returning id into v_role;
    insert into public.role_permissions(role_id, permission_key)
    select v_role, unnest(array['clients.view','zentha.view','zentha.manage','tasks.view','tasks.manage','reports.export']);

    insert into public.roles(organization_id, name, description, is_system)
    values (v_org, 'Sola lettura', 'Consultazione senza modifiche', true) returning id into v_role;
    insert into public.role_permissions(role_id, permission_key)
    select v_role, unnest(array['dashboard.view','clients.view','orders.view','production.view','inventory.view','issues.view','cotton.view','zentha.view','tasks.view']);

    insert into public.organization_members(organization_id, user_id, role_id, invited_by)
    values (v_org, auth.uid(), v_admin_role, auth.uid());
    return v_org;
end;
$$;

create or replace function public.assign_member_role(p_organization_id uuid, p_user_id uuid, p_role_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.has_permission(p_organization_id, 'users.manage') then raise exception 'Permission denied'; end if;
    if not exists (select 1 from public.roles where id = p_role_id and organization_id = p_organization_id) then
        raise exception 'Role does not belong to this organization';
    end if;
    insert into public.organization_members(organization_id, user_id, role_id, invited_by)
    values (p_organization_id, p_user_id, p_role_id, auth.uid())
    on conflict (organization_id, user_id) do update
        set role_id = excluded.role_id, status = 'active', invited_by = auth.uid();
end;
$$;

create or replace function public.admin_assign_member_by_email(p_organization_id uuid, p_email text, p_role_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid;
begin
    if not public.has_permission(p_organization_id, 'users.manage') then raise exception 'Permission denied'; end if;
    select id into v_user_id from public.profiles where lower(email) = lower(trim(p_email)) limit 1;
    if v_user_id is null then raise exception 'Account non trovato: l’utente deve prima registrarsi e confermare l’email'; end if;
    perform public.assign_member_role(p_organization_id, v_user_id, p_role_id);
    return v_user_id;
end;
$$;

create or replace function public.admin_list_members(p_organization_id uuid)
returns table (
    user_id uuid,
    email text,
    display_name text,
    status text,
    role_id uuid,
    role_name text,
    permission_overrides jsonb
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if not public.has_permission(p_organization_id, 'users.manage') then raise exception 'Permission denied'; end if;
    return query
    select
        m.user_id,
        p.email,
        p.display_name,
        m.status,
        m.role_id,
        r.name,
        coalesce(
            (select jsonb_object_agg(o.permission_key, o.allowed) from public.member_permission_overrides o where o.organization_id = m.organization_id and o.user_id = m.user_id),
            '{}'::jsonb
        )
    from public.organization_members m
    join public.profiles p on p.id = m.user_id
    join public.roles r on r.id = m.role_id
    where m.organization_id = p_organization_id
    order by lower(p.display_name), lower(p.email);
end;
$$;

create or replace function public.admin_set_permission_override(
    p_organization_id uuid,
    p_user_id uuid,
    p_permission_key text,
    p_allowed boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.has_permission(p_organization_id, 'users.manage') then raise exception 'Permission denied'; end if;
    insert into public.member_permission_overrides(organization_id, user_id, permission_key, allowed, updated_by)
    values (p_organization_id, p_user_id, p_permission_key, p_allowed, auth.uid())
    on conflict (organization_id, user_id, permission_key) do update
        set allowed = excluded.allowed, updated_at = now(), updated_by = auth.uid();
end;
$$;

create or replace function public.admin_clear_permission_override(
    p_organization_id uuid,
    p_user_id uuid,
    p_permission_key text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.has_permission(p_organization_id, 'users.manage') then raise exception 'Permission denied'; end if;
    delete from public.member_permission_overrides
    where organization_id = p_organization_id and user_id = p_user_id and permission_key = p_permission_key;
end;
$$;

create or replace function public.audit_erp_record_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.audit_log(organization_id, actor_id, action, entity_type, entity_id, old_data, new_data)
    values (
        coalesce(new.organization_id, old.organization_id), auth.uid(), tg_op,
        coalesce(new.entity_type, old.entity_type), coalesce(new.id, old.id),
        case when tg_op in ('UPDATE','DELETE') then to_jsonb(old) else null end,
        case when tg_op in ('INSERT','UPDATE') then to_jsonb(new) else null end
    );
    return coalesce(new, old);
end;
$$;

drop trigger if exists erp_records_audit on public.erp_records;
create trigger erp_records_audit
after insert or update or delete on public.erp_records
for each row execute procedure public.audit_erp_record_change();

alter table public.profiles enable row level security;
alter table public.organizations enable row level security;
alter table public.permissions enable row level security;
alter table public.roles enable row level security;
alter table public.role_permissions enable row level security;
alter table public.organization_members enable row level security;
alter table public.member_permission_overrides enable row level security;
alter table public.erp_records enable row level security;
alter table public.erp_attachments enable row level security;
alter table public.audit_log enable row level security;

create policy "profiles_self_read" on public.profiles for select to authenticated using (id = auth.uid());
create policy "profiles_self_update" on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());
create policy "organizations_member_read" on public.organizations for select to authenticated using (public.is_org_member(id));
create policy "organizations_admin_update" on public.organizations for update to authenticated using (public.has_permission(id, 'settings.manage')) with check (public.has_permission(id, 'settings.manage'));
create policy "permissions_authenticated_read" on public.permissions for select to authenticated using (true);
create policy "roles_member_read" on public.roles for select to authenticated using (public.is_org_member(organization_id));
create policy "roles_admin_all" on public.roles for all to authenticated using (public.has_permission(organization_id, 'users.manage')) with check (public.has_permission(organization_id, 'users.manage'));
create policy "role_permissions_member_read" on public.role_permissions for select to authenticated using (exists (select 1 from public.roles r where r.id = role_id and public.is_org_member(r.organization_id)));
create policy "role_permissions_admin_all" on public.role_permissions for all to authenticated using (exists (select 1 from public.roles r where r.id = role_id and public.has_permission(r.organization_id, 'users.manage'))) with check (exists (select 1 from public.roles r where r.id = role_id and public.has_permission(r.organization_id, 'users.manage')));
create policy "members_self_or_admin_read" on public.organization_members for select to authenticated using (user_id = auth.uid() or public.has_permission(organization_id, 'users.manage'));
create policy "members_admin_all" on public.organization_members for all to authenticated using (public.has_permission(organization_id, 'users.manage')) with check (public.has_permission(organization_id, 'users.manage'));
create policy "overrides_self_or_admin_read" on public.member_permission_overrides for select to authenticated using (user_id = auth.uid() or public.has_permission(organization_id, 'users.manage'));
create policy "overrides_admin_all" on public.member_permission_overrides for all to authenticated using (public.has_permission(organization_id, 'users.manage')) with check (public.has_permission(organization_id, 'users.manage'));
create policy "records_module_read" on public.erp_records for select to authenticated using (public.has_permission(organization_id, public.module_permission(module, false)));
create policy "records_module_insert" on public.erp_records for insert to authenticated with check (public.has_permission(organization_id, public.module_permission(module, true)) and created_by = auth.uid() and updated_by = auth.uid());
create policy "records_module_update" on public.erp_records for update to authenticated using (public.has_permission(organization_id, public.module_permission(module, true))) with check (public.has_permission(organization_id, public.module_permission(module, true)) and updated_by = auth.uid());
create policy "attachments_module_read" on public.erp_attachments for select to authenticated using (public.is_org_member(organization_id));
create policy "attachments_module_insert" on public.erp_attachments for insert to authenticated with check (public.is_org_member(organization_id) and created_by = auth.uid());
create policy "attachments_module_delete" on public.erp_attachments for delete to authenticated using (public.has_permission(organization_id, 'settings.manage') or created_by = auth.uid());
create policy "audit_authorized_read" on public.audit_log for select to authenticated using (public.has_permission(organization_id, 'audit.view'));

-- No DELETE policy is defined on erp_records: business data must be soft-deleted.
revoke all on public.audit_log from anon, authenticated;
grant select on public.audit_log to authenticated;
grant select on public.permissions to authenticated;
grant select, update on public.profiles to authenticated;
grant select, update on public.organizations to authenticated;
grant select, insert, update, delete on public.roles, public.role_permissions, public.organization_members, public.member_permission_overrides to authenticated;
grant select, insert, update on public.erp_records to authenticated;
grant select, insert, delete on public.erp_attachments to authenticated;
revoke execute on function public.my_access_context() from public, anon;
revoke execute on function public.bootstrap_spectro_organization(text) from public, anon;
revoke execute on function public.assign_member_role(uuid, uuid, uuid) from public, anon;
revoke execute on function public.admin_assign_member_by_email(uuid, text, uuid) from public, anon;
revoke execute on function public.admin_list_members(uuid) from public, anon;
revoke execute on function public.admin_set_permission_override(uuid, uuid, text, boolean) from public, anon;
revoke execute on function public.admin_clear_permission_override(uuid, uuid, text) from public, anon;
grant execute on function public.my_access_context() to authenticated;
grant execute on function public.bootstrap_spectro_organization(text) to authenticated;
grant execute on function public.assign_member_role(uuid, uuid, uuid) to authenticated;
grant execute on function public.admin_assign_member_by_email(uuid, text, uuid) to authenticated;
grant execute on function public.admin_list_members(uuid) to authenticated;
grant execute on function public.admin_set_permission_override(uuid, uuid, text, boolean) to authenticated;
grant execute on function public.admin_clear_permission_override(uuid, uuid, text) to authenticated;

insert into storage.buckets(id, name, public, file_size_limit)
values ('spectro-attachments', 'spectro-attachments', false, 52428800)
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit;

create policy "spectro_files_read" on storage.objects for select to authenticated
using (bucket_id = 'spectro-attachments' and public.is_org_member((storage.foldername(name))[1]::uuid));
create policy "spectro_files_insert" on storage.objects for insert to authenticated
with check (bucket_id = 'spectro-attachments' and public.is_org_member((storage.foldername(name))[1]::uuid));
create policy "spectro_files_update" on storage.objects for update to authenticated
using (bucket_id = 'spectro-attachments' and public.is_org_member((storage.foldername(name))[1]::uuid));
create policy "spectro_files_delete" on storage.objects for delete to authenticated
using (bucket_id = 'spectro-attachments' and public.has_permission((storage.foldername(name))[1]::uuid, 'settings.manage'));

