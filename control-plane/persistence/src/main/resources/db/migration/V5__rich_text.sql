-- Every in-game text moves from the & colour codes v2 used until now to OgCloud's tag format (D-0200, D-0201): stored
-- texts are converted, permission groups get a name format holding {name} (D-0203), placeholders become {name} too,
-- and name_color goes in this release (D-0206).

-- A colour code ends every format before it, as it did in legacy text, so it becomes <reset> and the colour; the
-- characters the tag format reads are escaped first.
create function pg_temp.ampersand_to_tags(source text) returns text
    language plpgsql
    immutable
as
$$
declare
    result text := source;
    code   record;
begin
    result := replace(result, '\', '\\');
    result := replace(result, '<', '\<');
    result := replace(result, '{', '\{');
    for code in
        select *
        from (values ('0', '<reset><black>'), ('1', '<reset><dark_blue>'), ('2', '<reset><dark_green>'),
                     ('3', '<reset><dark_aqua>'), ('4', '<reset><dark_red>'), ('5', '<reset><dark_purple>'),
                     ('6', '<reset><gold>'), ('7', '<reset><gray>'), ('8', '<reset><dark_gray>'),
                     ('9', '<reset><blue>'), ('a', '<reset><green>'), ('b', '<reset><aqua>'),
                     ('c', '<reset><red>'), ('d', '<reset><light_purple>'), ('e', '<reset><yellow>'),
                     ('f', '<reset><white>'), ('k', '<obfuscated>'), ('l', '<bold>'), ('m', '<strikethrough>'),
                     ('n', '<underlined>'), ('o', '<italic>'), ('r', '<reset>')) as codes (letter, tag)
        loop
            -- Legacy codes typed straight as § convert too, since saves now refuse '§' (D-0206).
            result := regexp_replace(result, '[&§]' || code.letter, code.tag, 'gi');
        end loop;
    -- Only generated resets merge; an escaped literal \<reset> from the source stays as it was.
    result := regexp_replace(result, '(?<!\\)(<reset>)+', '<reset>', 'g');
    result := regexp_replace(result, '^<reset>', '');
    -- A text of nothing but codes keeps one reset, since an empty suffix or name format means the catalog's default.
    return case when result = '' and source <> '' then '<reset>' else result end;
end
$$;

-- v1's %name% placeholders become {name}, as every text writes them now (D-0200).
create function pg_temp.braced(source text, names text) returns text
    language sql
    immutable
as
$$
select regexp_replace(source, '%(' || names || ')%', '{\1}', 'g')
$$;

create function pg_temp.tablist_text(source text) returns text
    language sql
    immutable
as
$$
select pg_temp.braced(pg_temp.ampersand_to_tags(source),
                      'server|group|proxy|onlinePlayers|maxPlayers|ping|permissionGroup')
$$;

create function pg_temp.npc_text(source text) returns text
    language sql
    immutable
as
$$
select pg_temp.braced(pg_temp.ampersand_to_tags(source), 'players|servers')
$$;

update network_settings
set motd                     = pg_temp.ampersand_to_tags(motd),
    maintenance_motd         = pg_temp.ampersand_to_tags(maintenance_motd),
    version_name             = pg_temp.ampersand_to_tags(version_name),
    maintenance_version_name = pg_temp.ampersand_to_tags(maintenance_version_name),
    maintenance_kick_message = pg_temp.ampersand_to_tags(maintenance_kick_message),
    tablist_header           = pg_temp.tablist_text(tablist_header),
    tablist_footer           = pg_temp.tablist_text(tablist_footer);

alter table network_settings
    alter column motd set default '<green>An OgCloud network',
    alter column maintenance_motd set default '<red>Maintenance',
    alter column maintenance_kick_message set default '<red>The network is in maintenance.';

-- An empty name format shows the name in the catalog's default colour, as an empty name colour did.
alter table permission_groups
    add column name_format text not null default '';

update permission_groups
set chat_prefix = pg_temp.ampersand_to_tags(chat_prefix),
    chat_suffix = pg_temp.ampersand_to_tags(chat_suffix),
    tab_prefix  = pg_temp.ampersand_to_tags(tab_prefix),
    name_format = case when name_color = '' then '' else pg_temp.ampersand_to_tags(name_color) || '{name}' end;

alter table permission_groups
    drop column name_color;

update npcs
set title    = pg_temp.npc_text(title),
    subtitle = pg_temp.npc_text(subtitle);
