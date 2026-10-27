-- The key a TOTP secret is encrypted with, so the key can rotate (D-0097). No rows exist yet.
alter table web_user_totp add column key_id text not null;
