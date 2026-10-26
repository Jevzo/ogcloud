-- Core schema (T-0105). Users, OAuth clients, roles, MFA and push tokens follow in their own
-- migrations (T-0302 to T-0304, T-1003). The API role owns these tables and runs Flyway; the
-- controller role gets data access only (D-0009).

-- Singleton row with the network-wide settings.
create table network_settings (
    id                        smallint primary key default 1 check (id = 1),
    maintenance               boolean     not null default false,
    motd                      text        not null default '&aAn OgCloud network',
    maintenance_motd          text        not null default '&cMaintenance',
    version_name              text        not null default 'OgCloud',
    maintenance_version_name  text        not null default 'Maintenance',
    max_players               integer     not null default 1000 check (max_players > 0),
    default_group_id          text        not null default 'lobby',
    routing_strategy          text        not null default 'LEAST_FILLED'
        check (routing_strategy in ('LEAST_FILLED', 'MOST_FILLED', 'BALANCED', 'ROUND_ROBIN')),
    permission_system_enabled boolean     not null default true,
    tablist_enabled           boolean     not null default true,
    tablist_header            text        not null default '',
    tablist_footer            text        not null default '',
    maintenance_kick_message  text        not null default '&cThe network is in maintenance.',
    -- Servers do not start until a network owner accepts the Minecraft EULA (D-0046).
    eula_accepted_at          timestamptz,
    require_minecraft_link    boolean     not null default true,
    version                   bigint      not null default 0,
    updated_at                timestamptz not null default now()
);

create table groups (
    id                     text primary key check (id ~ '^[a-z]([a-z0-9-]{0,28}[a-z0-9])?$'),
    type                   text        not null check (type in ('DYNAMIC', 'STATIC', 'PROXY')),
    profile_id             text        not null,
    min_online             integer     not null check (min_online >= 0),
    max_instances          integer     not null check (max_instances >= min_online),
    players_per_server     integer     not null check (players_per_server > 0),
    scale_up_threshold     numeric(4, 3) not null check (scale_up_threshold between 0 and 1),
    scale_down_threshold   numeric(4, 3) not null check (scale_down_threshold between 0 and 1),
    cooldown_seconds       integer     not null check (cooldown_seconds >= 0),
    drain_timeout_seconds  integer     not null check (drain_timeout_seconds >= 0),
    maintenance            boolean     not null default false,
    memory_request         text        not null,
    memory_limit           text        not null,
    cpu_request            text        not null,
    cpu_limit              text,
    jvm_flags              text        not null default '',
    -- Optional custom image and the Infisical-synced pull secret it needs (T-0202).
    image_override         text,
    image_pull_secret_ref  text,
    storage_size           text,
    active_template_version_id uuid,
    -- Soft delete: purged after the retention grace (D-0043).
    deleted_at             timestamptz,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint      not null default 0,
    check (scale_down_threshold < scale_up_threshold),
    check (type <> 'STATIC' or max_instances <= 1)
);

-- Immutable template versions (D-0042); the archive lives in object storage.
create table template_versions (
    id          uuid primary key,
    group_id    text        not null references groups (id) on delete cascade,
    object_key  text        not null unique,
    sha256      text        not null check (sha256 ~ '^[0-9a-f]{64}$'),
    size_bytes  bigint      not null check (size_bytes >= 0),
    source      text        not null check (source in ('UPLOAD', 'PUSH', 'IMPORT')),
    created_by  text        not null,
    created_at  timestamptz not null default now()
);

create index template_versions_group_created on template_versions (group_id, created_at desc);

alter table groups
    add constraint groups_active_template_fk
        foreign key (active_template_version_id) references template_versions (id) on delete set null;

create table permission_groups (
    id           text primary key check (id ~ '^[a-z][a-z0-9_-]{0,31}$'),
    name         text        not null,
    weight       integer     not null,
    is_default   boolean     not null default false,
    chat_prefix  text        not null default '',
    chat_suffix  text        not null default '',
    name_color   text        not null default '',
    tab_prefix   text        not null default '',
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    version      bigint      not null default 0
);

-- Exactly one default group.
create unique index permission_groups_single_default on permission_groups (is_default) where is_default;

create table permission_group_permissions (
    permission_group_id text not null references permission_groups (id) on delete cascade,
    permission          text not null,
    description         text not null default '',
    primary key (permission_group_id, permission)
);

create table players (
    uuid                 uuid primary key,
    name                 text        not null,
    permission_group_id  text        not null references permission_groups (id),
    -- Null for permanent ranks.
    rank_expires_at      timestamptz,
    rank_version         bigint      not null default 0,
    first_seen_at        timestamptz not null default now(),
    last_seen_at         timestamptz not null default now()
);

create index players_name_lower on players (lower(name));
create index players_rank_expiry on players (rank_expires_at) where rank_expires_at is not null;

create table npcs (
    id               uuid primary key,
    group_id         text             not null references groups (id) on delete cascade,
    world            text             not null,
    x                double precision not null,
    y                double precision not null,
    z                double precision not null,
    yaw              real             not null default 0,
    pitch            real             not null default 0,
    title            text             not null default '',
    subtitle         text             not null default '',
    skin_texture     text,
    skin_signature   text,
    look_at_players  boolean          not null default true,
    -- {"transfer": {"groupId": "...", "routingStrategy": "..."}} or null for no action.
    left_click       jsonb,
    right_click      jsonb,
    created_at       timestamptz      not null default now(),
    updated_at       timestamptz      not null default now(),
    version          bigint           not null default 0
);

create index npcs_group on npcs (group_id);

-- Long-running operations executed by the controller leader (D-0039).
create table jobs (
    id               uuid primary key,
    kind             text        not null,
    state            text        not null check (state in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    payload          jsonb       not null default '{}',
    -- Resume point for a new leader.
    progress         jsonb       not null default '{}',
    completed_steps  integer     not null default 0,
    total_steps      integer     not null default 0,
    message          text        not null default '',
    created_by       text        not null,
    created_at       timestamptz not null default now(),
    started_at       timestamptz,
    finished_at      timestamptz,
    -- Fencing: a deposed leader's update of an older version is rejected.
    version          bigint      not null default 0
);

create index jobs_active on jobs (created_at) where state in ('QUEUED', 'RUNNING');
create index jobs_finished on jobs (finished_at) where finished_at is not null;

create table audit_log (
    id           uuid primary key,
    occurred_at  timestamptz not null,
    actor_type   text        not null check (actor_type in ('USER', 'CLIENT', 'PLAYER', 'SYSTEM')),
    -- Pseudonymised when the actor's data is erased (D-0049).
    actor_id     text        not null,
    action       text        not null,
    target_type  text        not null,
    target_id    text        not null,
    summary      text        not null default '',
    metadata     jsonb       not null default '{}'
);

create index audit_log_occurred on audit_log (occurred_at);
create index audit_log_actor on audit_log (actor_type, actor_id);

create table scaling_log (
    id           uuid primary key,
    occurred_at  timestamptz not null,
    group_id     text        not null,
    action       text        not null check (action in ('START', 'DRAIN', 'UNDRAIN', 'STOP')),
    server_id    uuid,
    reason       text        not null,
    details      text        not null default ''
);

create index scaling_log_occurred on scaling_log (occurred_at);
create index scaling_log_group on scaling_log (group_id, occurred_at desc);

-- Runtime-bundle artifacts copied to object storage, keyed by profile and path (T-0209).
create table runtime_artifacts (
    profile_id   text        not null,
    path         text        not null,
    object_key   text        not null,
    source_url   text        not null,
    sha256       text        not null check (sha256 ~ '^[0-9a-f]{64}$'),
    size_bytes   bigint      not null check (size_bytes >= 0),
    stored_at    timestamptz not null default now(),
    primary key (profile_id, path)
);

-- Seed data.
insert into network_settings (id) values (1);

insert into permission_groups (id, name, weight, is_default)
values ('default', 'Default', 100, true);

-- The controller needs data access but must not change the schema. Default privileges extend this
-- to tables created by later migrations, which run as the same owner.
grant select, insert, update, delete on all tables in schema public to ${controllerRole};
alter default privileges in schema public grant select, insert, update, delete on tables to ${controllerRole};
