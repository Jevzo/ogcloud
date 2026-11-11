# In-game text format

Every text OgCloud shows in game uses this tag format (D-0200), modelled on MiniMessage and parsed by OgCloud
itself into plain Adventure components:
- the message catalog;
- permission groups' prefixes, suffixes and name formats;
- the network settings' MOTDs, version names, tablist and maintenance kick message;
- NPC titles.

This module is the only implementation (D-0202): the plugins render with it, the API validates and previews with it,
and the controller renders the load balancer's texts.

## Tags

| Tag | Effect |
|---|---|
| `<black>` `<dark_blue>` `<dark_green>` `<dark_aqua>` `<dark_red>` `<dark_purple>` `<gold>` `<gray>` `<dark_gray>` `<blue>` `<green>` `<aqua>` `<red>` `<light_purple>` `<yellow>` `<white>` | A named colour |
| `<#ff8800>`, `<color:#ff8800>`, `<color:gold>` | A colour |
| `<gradient:#ff0000:#0000ff>`, `<gradient:red:#00ff00:blue>` | A gradient through two or more colours |
| `<rainbow>` | Once round the colour wheel |
| `<bold>` `<italic>` `<underlined>` `<strikethrough>` `<obfuscated>` | A decoration |
| `<reset>` | Ends every open tag |

- Tag names and hex digits may be in any case.
- `</name>` ends the innermost open tag of that name, and the tags opened inside it. Any colour also ends with
  `</color>`.
- A tag never closed runs to the end of the text.
- A colour or gradient inside a gradient replaces it for its span; the outer gradient spreads over the rest.
- Gradients and rainbows colour each character (each Unicode code point, so an emoji is one character), and they
  count the text placeholders were filled with.

## Placeholders

`{name}` stands for a value the text is shown with, such as `{player}` in a catalog message or `{name}` in a
permission group's name format. Values are filled in after parsing, as literal text: a value never adds formatting.

## Escapes

`\<`, `\{` and `\\` stand for `<`, `{` and `\`. Any other backslash is itself.

## Errors

A text is refused when it is saved if it has:
- an unknown tag, or a `<` that starts no tag;
- a colour that is neither named nor `#rrggbb`;
- a gradient with fewer than two colours;
- a closing tag that closes nothing.

Each error names its character offset.

## Older clients (D-0204)

Clients before 1.16 have only the named colours. Each hex colour is shown as its nearest named colour, by
Adventure's `NamedTextColor.nearestTo`; a gradient keeps a nearest colour per character. Where 1.8 caps a text's
length, as a team prefix's 16 characters, a gradient or rainbow takes its first colour instead.
