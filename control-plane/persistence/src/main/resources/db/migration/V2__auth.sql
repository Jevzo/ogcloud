-- Web users, roles, MFA and the OAuth authorization server (D-0012, D-0040, D-0084, D-0085).
-- Spring's own tables keep their names and columns, with the PostgreSQL types their DDL asks for.

-- Roles are named permission sets. The permissions of built-in roles come from code, so only custom roles
-- have rows in role_permissions; built-in roles cannot be changed or deleted.
create table roles (
    id         text        primary key check (id ~ '^[a-z][a-z0-9-]{0,30}$'),
    name       text        not null,
    built_in   boolean     not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version    bigint      not null default 0
);

create table role_permissions (
    role_id    text not null references roles (id) on delete cascade,
    permission text not null,
    primary key (role_id, permission)
);

insert into roles (id, name, built_in) values
    ('owner', 'Owner', true),
    ('admin', 'Admin', true),
    ('moderator', 'Moderator', true),
    ('viewer', 'Viewer', true);

create table web_users (
    id                 uuid        primary key,
    username           text        not null check (length(username) between 3 and 32),
    email              text        not null,
    -- {bcrypt}…; null for accounts that sign in with passkeys only.
    password_hash      text,
    role_id            text        not null references roles (id),
    linked_player_uuid uuid        unique references players (uuid) on delete set null,
    enabled            boolean     not null default true,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    last_login_at      timestamptz,
    version            bigint      not null default 0
);

create unique index web_users_username on web_users (lower(username));
create unique index web_users_email on web_users (lower(email));

-- TOTP (D-0084). The secret is encrypted with a key from Infisical; last_step stops a code from being used twice.
create table web_user_totp (
    user_id          uuid        primary key references web_users (id) on delete cascade,
    secret_encrypted bytea       not null,
    confirmed_at     timestamptz,
    last_step        bigint
);

-- The one-time token that creates the first owner on a fresh install (T-0303); only its hash is stored.
create table setup_tokens (
    token_hash text        primary key,
    expires_at timestamptz not null
);

-- Passkeys: Spring Security WebAuthn (D-0085). user_entities.name is the principal name, i.e. the web user's id.
create table user_entities (
    id           varchar(1000) not null primary key,
    name         varchar(100)  not null unique,
    display_name varchar(200)
);

create table user_credentials (
    credential_id                varchar(1000) not null primary key,
    user_entity_user_id          varchar(1000) not null references user_entities (id) on delete cascade,
    public_key                   bytea         not null,
    signature_count              bigint,
    uv_initialized               boolean,
    backup_eligible              boolean       not null,
    authenticator_transports     varchar(1000),
    public_key_credential_type   varchar(100),
    backup_state                 boolean       not null,
    attestation_object           bytea,
    attestation_client_data_json bytea,
    created                      timestamptz,
    last_used                    timestamptz,
    label                        varchar(1000) not null
);

-- Spring Authorization Server: clients (dashboard, desktop, mobile, machine clients) and issued tokens.
create table oauth2_registered_client (
    id                            varchar(100)  not null primary key,
    client_id                     varchar(100)  not null unique,
    client_id_issued_at           timestamptz   not null default now(),
    client_secret                 varchar(200),
    client_secret_expires_at      timestamptz,
    client_name                   varchar(200)  not null,
    client_authentication_methods varchar(1000) not null,
    authorization_grant_types     varchar(1000) not null,
    redirect_uris                 varchar(1000),
    post_logout_redirect_uris     varchar(1000),
    scopes                        varchar(1000) not null,
    client_settings               varchar(2000) not null,
    token_settings                varchar(2000) not null
);

create table oauth2_authorization (
    id                            varchar(100) not null primary key,
    registered_client_id          varchar(100) not null,
    principal_name                varchar(200) not null,
    authorization_grant_type      varchar(100) not null,
    authorized_scopes             varchar(1000),
    attributes                    text,
    state                         varchar(500),
    authorization_code_value      text,
    authorization_code_issued_at  timestamptz,
    authorization_code_expires_at timestamptz,
    authorization_code_metadata   text,
    access_token_value            text,
    access_token_issued_at        timestamptz,
    access_token_expires_at       timestamptz,
    access_token_metadata         text,
    access_token_type             varchar(100),
    access_token_scopes           varchar(1000),
    oidc_id_token_value           text,
    oidc_id_token_issued_at       timestamptz,
    oidc_id_token_expires_at      timestamptz,
    oidc_id_token_metadata        text,
    refresh_token_value           text,
    refresh_token_issued_at       timestamptz,
    refresh_token_expires_at      timestamptz,
    refresh_token_metadata        text,
    user_code_value               text,
    user_code_issued_at           timestamptz,
    user_code_expires_at          timestamptz,
    user_code_metadata            text,
    device_code_value             text,
    device_code_issued_at         timestamptz,
    device_code_expires_at        timestamptz,
    device_code_metadata          text
);

-- The API's cleanup deletes authorizations whose tokens all expired (D-0043); greatest() skips missing tokens.
create index oauth2_authorization_expiry on oauth2_authorization
    (greatest(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at));
create index oauth2_authorization_principal on oauth2_authorization (principal_name);
-- Spring looks authorizations up by token value; hash indexes take values of any length.
create index oauth2_authorization_state on oauth2_authorization using hash (state);
create index oauth2_authorization_code on oauth2_authorization using hash (authorization_code_value);
create index oauth2_authorization_access_token on oauth2_authorization using hash (access_token_value);
create index oauth2_authorization_refresh_token on oauth2_authorization using hash (refresh_token_value);

create table oauth2_authorization_consent (
    registered_client_id varchar(100)  not null,
    principal_name       varchar(200)  not null,
    authorities          varchar(1000) not null,
    primary key (registered_client_id, principal_name)
);

-- Credentials stay with the API: the controller's default privileges (V1) must not reach them.
revoke all on roles, role_permissions, web_users, web_user_totp, setup_tokens, user_entities, user_credentials,
    oauth2_registered_client, oauth2_authorization, oauth2_authorization_consent from ${controllerRole};
