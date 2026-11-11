import { Button, Input } from "@ogcloud/ui";
import { Plus, X } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";

// Permission nodes are dotted words, such as ogcloud.admin.server or lobby.fly; `*` grants everything.
const NODE = /^[\w*.-]+$/;

interface PermissionsEditorProps {
  value: Readonly<Record<string, string>>;
  onChange: (value: Record<string, string>) => void;
  disabled: boolean;
}

/** The nodes a permission group grants, each with a note on why; added and removed in place. */
export function PermissionsEditor({
  value,
  onChange,
  disabled,
}: PermissionsEditorProps) {
  const { t } = useTranslation("web");
  const [node, setNode] = useState("");
  const [note, setNote] = useState("");
  const entries = Object.entries(value).sort(([a], [b]) => a.localeCompare(b));
  const valid = NODE.test(node.trim());

  // Enter adds too; the editor sits inside the page's form, so it must not submit it.
  const add = (event: { preventDefault: () => void }) => {
    event.preventDefault();
    if (!valid) return;
    onChange({ ...value, [node.trim()]: note.trim() });
    setNode("");
    setNote("");
  };
  const remove = (key: string) =>
    onChange(
      Object.fromEntries(entries.filter(([candidate]) => candidate !== key)),
    );
  const describe = (key: string, text: string) =>
    onChange({ ...value, [key]: text });

  return (
    <div className="flex flex-col gap-2">
      {entries.length === 0 ? (
        <p className="text-small text-text-3">{t("permissions.nodes.none")}</p>
      ) : (
        <ul className="flex flex-col gap-1.5">
          {entries.map(([key, text]) => (
            <li
              key={key}
              className="grid grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)_auto] items-center gap-2"
            >
              <span className="truncate font-mono text-mono">{key}</span>
              <Input
                aria-label={t("permissions.nodes.noteFor", { node: key })}
                placeholder={t("permissions.nodes.note")}
                value={text}
                disabled={disabled}
                onChange={(event) => describe(key, event.target.value)}
              />
              <Button
                kind="ghost"
                icon={X}
                iconOnly
                disabled={disabled}
                onClick={() => remove(key)}
              >
                {t("permissions.nodes.remove", { node: key })}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {disabled ? null : (
        <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)_auto] items-center gap-2 border-border-subtle border-t pt-3">
          <Input
            mono
            aria-label={t("permissions.nodes.node")}
            placeholder="lobby.fly"
            value={node}
            onChange={(event) => setNode(event.target.value)}
            onKeyDown={(event) =>
              event.key === "Enter" ? add(event) : undefined
            }
          />
          <Input
            aria-label={t("permissions.nodes.note")}
            placeholder={t("permissions.nodes.note")}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            onKeyDown={(event) =>
              event.key === "Enter" ? add(event) : undefined
            }
          />
          <Button icon={Plus} onClick={add} disabled={!valid}>
            {t("permissions.nodes.add")}
          </Button>
        </div>
      )}
    </div>
  );
}
