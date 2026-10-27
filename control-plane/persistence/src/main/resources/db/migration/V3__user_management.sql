-- Linking a Minecraft account is opt-in (D-0094); no installation predates this migration.
alter table network_settings alter column require_minecraft_link set default false;
update network_settings set require_minecraft_link = false;
