# In-game text format

Every text OgCloud shows in game uses this tag format (D-0200), modelled on MiniMessage and parsed by OgCloud
itself into a tree it renders to Adventure components, legacy `§` text, chat-component JSON or plain text:
- the message catalog;
- permission groups' prefixes, suffixes and name formats;
- the network settings' MOTDs, version names, tablist and maintenance kick message;
- the kick message of an API transfer;
- NPC titles and subtitles, runtime NPCs' included.

The module `contracts/text` is the only implementation (D-0202): the plugins render with it, the API checks and
previews with it, and the controller renders the load balancer's texts.

## Tags

| Tag | Effect |
|---|---|
| `<black>` `<dark_blue>` `<dark_green>` `<dark_aqua>` `<dark_red>` `<dark_purple>` `<gold>` `<gray>` `<dark_gray>` `<blue>` `<green>` `<aqua>` `<red>` `<light_purple>` `<yellow>` `<white>` | A named colour |
| `<#ff8800>`, `<color:#ff8800>`, `<color:gold>` | A colour |
| `<gradient:#ff0000:#0000ff>`, `<gradient:red:#00ff00:blue>` | A gradient through two or more colours |
| `<rainbow>` | Once round the colour wheel |
| `<bold>` `<italic>` `<underlined>` `<strikethrough>` `<obfuscated>` | A decoration |
| `<reset>` | Ends every open tag |

- Tag names and hex digits may be in any case. MiniMessage's short names (`<b>`, `<grey>`, `<c:…>`) are not tags.
- `</name>` ends the innermost open tag of that name (the part before the first `:`), and every tag opened inside
  it. A named or hex colour also ends with `</color>`, and `<color:…>` ends only with it; a gradient ends only with
  `</gradient>`, a rainbow only with `</rainbow>`.
- A tag never closed runs to the end of the text.
- A colour, gradient or rainbow inside a gradient or rainbow takes its stretch out; the outer one spreads over the
  rest, and decorations inside do not interrupt it.
- Gradients and rainbows colour each Unicode code point, so a character outside the BMP counts once, though an emoji
  built from several code points counts as several. They count the text placeholders were filled with, and an
  unfilled placeholder as written.
- The colour in effect at the end colours what follows, such as a typed chat message: the last character's, or that
  of an empty colour tag at the very end (`<gray>: <white>`). After a closing tag or `<reset>`, it is the default.
- `>` outside a tag is plain text, and `§` is never: it is refused when saved and dropped from what renders, so no
  text or value adds legacy codes (D-0206).

## Placeholders

`{name}` stands for a value the text is shown with: `{`, a letter, then letters, digits or `_`, then `}`. Names are
case-sensitive, unlike tags; any other `{` is plain text. Values are filled in after parsing, as literal text, so a
value never adds formatting. Each stored text offers only its own placeholders (`TextField`):

| Text | Placeholders |
|---|---|
| Name format | `{name}`, which it must show |
| Tablist header and footer | `{server}` `{group}` `{proxy}` `{onlinePlayers}` `{maxPlayers}` `{ping}` `{permissionGroup}` |
| NPC title and subtitle | `{players}` `{servers}`, the live counts of the click target's group (0 for runtime NPCs) |
| Everything else | none |

## Escapes

`\<`, `\{` and `\\` stand for `<`, `{` and `\`. Any other backslash is itself.

## Errors

A text is refused when it is saved, or when a runtime NPC spawns, if it:
- has an unknown tag, or a `<` that starts no tag;
- names a colour that is neither named nor `#rrggbb`;
- gives `<color:…>` other than one colour, a gradient fewer than two, or `<rainbow>` any;
- has a closing tag that closes nothing;
- uses a placeholder it does not offer, or is a name format without `{name}`;
- contains `§`;
- is longer than 2,048 characters (D-0205).

Each error has a code, an offset and a length, both in UTF-16 code units as a JavaScript string counts them, and an
English reason. Tag errors are reported together; placeholder errors only once the tags parse; a missing `{name}`
is reported at the end of the text. A stored text that fails anyway, as from an older release, is shown as written.

## Older clients (D-0204)

Clients before 1.16 have only the named colours. Where OgCloud writes legacy text itself (PandaSpigot, the server
list for old clients), each hex colour becomes its nearest named colour by Adventure's `NamedTextColor.nearestTo`,
one per character in a gradient; on Paper and Velocity, Velocity and ViaBackwards on the proxy downsample with their
own choice. Where 1.8 caps a text's length, as a team prefix's 16 characters, a gradient or rainbow takes its first
colour, and the prefix is cut before the name's colour code. Version names are always legacy text, since the server
list takes them as a plain string.
