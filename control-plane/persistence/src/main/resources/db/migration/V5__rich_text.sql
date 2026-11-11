-- Every in-game text moves from v1's & colour codes to OgCloud's tag format (D-0200, D-0201): stored texts are
-- converted, permission groups get a name format holding {name} (D-0203), and placeholders become {name} too.

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
            result := regexp_replace(result, '&' || code.letter, code.tag, 'gi');
        end loop;
    result := regexp_replace(result, '(<reset>)+', '<reset>', 'g');
    return regexp_replace(result, '^<reset>', '');
end
$$;

update network_settings
set motd                     = pg_temp.ampersand_to_tags(motd),
    maintenance_motd         = pg_temp.ampersand_to_tags(maintenance_motd),
    version_name             = pg_temp.ampersand_to_tags(version_name),
    maintenance_version_name = pg_temp.ampersand_to_tags(maintenance_version_name),
    maintenance_kick_message = pg_temp.ampersand_to_tags(maintenance_kick_message),
    tablist_header           = regexp_replace(pg_temp.ampersand_to_tags(tablist_header),
                                              '%(server|group|proxy|onlinePlayers|maxPlayers|ping|permissionGroup)%',
                                              '{\1}', 'g'),
    tablist_footer           = regexp_replace(pg_temp.ampersand_to_tags(tablist_footer),
                                              '%(server|group|proxy|onlinePlayers|maxPlayers|ping|permissionGroup)%',
                                              '{\1}', 'g');

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
set title    = regexp_replace(pg_temp.ampersand_to_tags(title), '%(players|servers)%', '{\1}', 'g'),
    subtitle = regexp_replace(pg_temp.ampersand_to_tags(subtitle), '%(players|servers)%', '{\1}', 'g');
