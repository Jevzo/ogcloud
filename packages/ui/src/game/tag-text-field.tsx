import {
  Bold,
  Braces,
  Italic,
  Palette,
  Strikethrough,
  Underline,
} from "lucide-react";
import { Popover as PopoverPrimitive } from "radix-ui";
import { type ReactNode, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "../components/button.tsx";
import {
  type ColourChoice,
  ColourPicker,
} from "../components/colour-picker.tsx";
import { controlFrame } from "../components/input.tsx";
import { Menu } from "../components/menu.tsx";
import { popoverSurface } from "../components/popover.tsx";
import { cn } from "../lib/cn.ts";

/** Where the API found a problem, in UTF-16 units as JavaScript counts them (D-0205). */
export interface TextProblem {
  offset: number;
  length: number;
}

interface TagTextFieldProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  problems: readonly TextProblem[];
  /** The placeholders this text offers, without braces, e.g. `name` (D-0200). */
  placeholders: readonly string[];
  recentColours: readonly string[];
  /** Shows the tag source on several lines, for the tablist's header and footer. */
  multiline?: boolean;
  "aria-describedby"?: string | undefined;
  "aria-invalid"?: true | undefined;
}

type Piece = {
  text: string;
  kind: "plain" | "tag" | "placeholder" | "escape";
  problem: boolean;
};

// `\<`, `\{` and `\\` escape; a tag is `<…>`; a placeholder `{letter…}` (docs/architecture/text-format.md).
const TOKENS = /\\[<{\\]|<[^<>\n]+>|\{[A-Za-z][A-Za-z0-9_]*\}/g;

/** Splits the source wherever a token or a problem starts or ends, so each piece has one look. */
function pieces(source: string, problems: readonly TextProblem[]): Piece[] {
  const tokens = [...source.matchAll(TOKENS)].map((match) => {
    const text = match[0];
    const kind: Piece["kind"] = text.startsWith("\\")
      ? "escape"
      : text.startsWith("<")
        ? "tag"
        : "placeholder";
    return { start: match.index, end: match.index + text.length, kind };
  });
  const cuts = new Set([0, source.length]);
  for (const token of tokens) cuts.add(token.start).add(token.end);
  for (const problem of problems)
    cuts
      .add(problem.offset)
      .add(
        Math.min(source.length, problem.offset + Math.max(1, problem.length)),
      );
  const sorted = [...cuts]
    .filter((cut) => cut <= source.length)
    .sort((a, b) => a - b);
  return sorted.slice(0, -1).map((start, index) => {
    const end = sorted[index + 1] ?? source.length;
    const token = tokens.find(
      (candidate) => candidate.start <= start && end <= candidate.end,
    );
    const problem = problems.some(
      (p) => p.offset <= start && end <= p.offset + Math.max(1, p.length),
    );
    return {
      text: source.slice(start, end),
      kind: token?.kind ?? "plain",
      problem,
    };
  });
}

const PIECE_LOOK: Record<Piece["kind"], string> = {
  plain: "",
  tag: "text-accent-text",
  placeholder: "text-info-text",
  escape: "text-text-3",
};

/** The tag source of an in-game text, highlighted, with problems underlined and a toolbar that writes tags. */
export function TagTextField({
  value,
  onChange,
  problems,
  placeholders,
  recentColours,
  multiline = false,
  ...field
}: TagTextFieldProps) {
  const { t } = useTranslation("ui");
  const editor = useRef<HTMLTextAreaElement>(null);
  const [colourOpen, setColourOpen] = useState(false);

  const replaceSelection = (open: string, close: string) => {
    const element = editor.current;
    const start = element?.selectionStart ?? value.length;
    const end = element?.selectionEnd ?? value.length;
    const next =
      value.slice(0, start) +
      open +
      value.slice(start, end) +
      close +
      value.slice(end);
    onChange(next);
    requestAnimationFrame(() => {
      element?.focus();
      element?.setSelectionRange(start + open.length, end + open.length);
    });
  };
  const wrap = (tag: string) => replaceSelection(`<${tag}>`, `</${tag}>`);
  const applyColour = (choice: ColourChoice) => {
    setColourOpen(false);
    if (choice.kind === "named") wrap(choice.name);
    else if (choice.kind === "hex")
      replaceSelection(`<${choice.hex.toLowerCase()}>`, "</color>");
    else
      replaceSelection(
        `<gradient:${choice.stops.map((stop) => stop.toLowerCase()).join(":")}>`,
        "</gradient>",
      );
  };

  return (
    <div data-skeleton="" className={cn(controlFrame, "overflow-hidden")}>
      <div
        role="toolbar"
        aria-label={t("tagText.formatting")}
        className="flex items-center gap-0.5 border-border-subtle border-b bg-surface-2 p-1"
      >
        <PopoverPrimitive.Root open={colourOpen} onOpenChange={setColourOpen}>
          <PopoverPrimitive.Trigger asChild>
            <Button kind="ghost" size="sm" icon={Palette} iconOnly>
              {t("tagText.colour")}
            </Button>
          </PopoverPrimitive.Trigger>
          <PopoverPrimitive.Portal>
            <PopoverPrimitive.Content
              align="start"
              sideOffset={6}
              collisionPadding={8}
              className={popoverSurface}
            >
              <ColourPicker
                initial={recentColours[0] ?? "#FFFFFF"}
                recent={recentColours}
                applyLabel={t("colour.insert")}
                onApply={applyColour}
                onCancel={() => setColourOpen(false)}
              />
            </PopoverPrimitive.Content>
          </PopoverPrimitive.Portal>
        </PopoverPrimitive.Root>
        <Button
          kind="ghost"
          size="sm"
          icon={Bold}
          iconOnly
          onClick={() => wrap("bold")}
        >
          {t("tagText.bold")}
        </Button>
        <Button
          kind="ghost"
          size="sm"
          icon={Italic}
          iconOnly
          onClick={() => wrap("italic")}
        >
          {t("tagText.italic")}
        </Button>
        <Button
          kind="ghost"
          size="sm"
          icon={Underline}
          iconOnly
          onClick={() => wrap("underlined")}
        >
          {t("tagText.underlined")}
        </Button>
        <Button
          kind="ghost"
          size="sm"
          icon={Strikethrough}
          iconOnly
          onClick={() => wrap("strikethrough")}
        >
          {t("tagText.strikethrough")}
        </Button>
        <span className="flex-1" />
        {placeholders.length === 0 ? null : (
          <Menu
            align="end"
            groups={[
              placeholders.map((name) => ({
                label: `{${name}}`,
                onSelect: () => replaceSelection(`{${name}}`, ""),
              })),
            ]}
            trigger={
              <Button kind="ghost" size="sm" icon={Braces}>
                {t("tagText.placeholder")}
              </Button>
            }
          />
        )}
      </div>
      <div className="relative font-mono text-mono leading-[1.6]">
        <Highlight value={value} problems={problems} />
        {/* Its text is see-through over the highlighted copy, and so is its selection, or selected text would vanish. */}
        <textarea
          ref={editor}
          {...field}
          value={value}
          rows={multiline ? 3 : 1}
          spellCheck={false}
          autoComplete="off"
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={(event) => {
            if (!multiline && event.key === "Enter") event.preventDefault();
          }}
          className="relative block w-full resize-none bg-transparent px-2.5 py-2 text-transparent caret-text outline-none [field-sizing:content] selection:bg-accent/25 selection:text-transparent"
        />
      </div>
    </div>
  );
}

/** The coloured copy of the source behind the transparent textarea, so the caret and selection stay native. */
function Highlight({
  value,
  problems,
}: {
  value: string;
  problems: readonly TextProblem[];
}): ReactNode {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 whitespace-pre-wrap break-words px-2.5 py-2 text-text"
    >
      {pieces(value, problems).map((piece, index) => (
        <span
          key={index}
          className={cn(
            PIECE_LOOK[piece.kind],
            piece.problem &&
              "text-danger-text underline decoration-danger-text decoration-wavy underline-offset-3",
          )}
        >
          {piece.text}
        </span>
      ))}
      {"​"}
    </div>
  );
}
