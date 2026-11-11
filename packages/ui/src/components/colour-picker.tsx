import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { HexColorInput, HexColorPicker } from "react-colorful";
import { useTranslation } from "react-i18next";
import { type MinecraftColour, minecraftColours } from "../game/minecraft.ts";
import { cn } from "../lib/cn.ts";
import { Button } from "./button.tsx";
import { controlFrame } from "./input.tsx";
import { Segmented } from "./segmented.tsx";

/** A solid colour (a hex or one of Minecraft's names) or a gradient of hex stops (D-0200). */
export type ColourChoice =
  | { kind: "named"; name: MinecraftColour }
  | { kind: "hex"; hex: string }
  | { kind: "gradient"; stops: string[] };

const MIN_STOPS = 2;
const MAX_STOPS = 5;
const DEFAULT_GRADIENT = ["#FF8800", "#FF3D7F"];

interface ColourPickerProps {
  initial: string;
  recent: readonly string[];
  allowGradient?: boolean;
  applyLabel: string;
  onApply: (choice: ColourChoice) => void;
  onCancel: () => void;
}

/** The picker's panel; the tag-text toolbar and colour fields open it in a popover. */
export function ColourPicker({
  initial,
  recent,
  allowGradient = true,
  applyLabel,
  onApply,
  onCancel,
}: ColourPickerProps) {
  const { t } = useTranslation("ui");
  const [mode, setMode] = useState<"solid" | "gradient">("solid");
  const [solid, setSolid] = useState<ColourChoice & { kind: "hex" | "named" }>({
    kind: "hex",
    hex: initial,
  });
  const [stops, setStops] = useState<string[]>(DEFAULT_GRADIENT);
  const [stopIndex, setStopIndex] = useState(0);

  const solidHex =
    solid.kind === "hex" ? solid.hex : minecraftColours[solid.name];
  const editing = mode === "solid" ? solidHex : (stops[stopIndex] ?? solidHex);
  const setEditing = (hex: string) => {
    if (mode === "solid") {
      setSolid({ kind: "hex", hex: hex.toUpperCase() });
    } else {
      setStops((current) =>
        current.map((stop, index) =>
          index === stopIndex ? hex.toUpperCase() : stop,
        ),
      );
    }
  };

  return (
    <div className="flex w-colour-picker flex-col gap-2.5 p-3">
      {allowGradient ? (
        <Segmented
          label={t("colour.solid")}
          options={[
            { value: "solid", label: t("colour.solid") },
            { value: "gradient", label: t("colour.gradient") },
          ]}
          value={mode}
          onValueChange={setMode}
          className="w-full [&>button]:flex-1"
        />
      ) : null}
      {mode === "gradient" ? (
        <div className="flex flex-col gap-2">
          <div
            aria-hidden="true"
            className="h-3 rounded-full"
            style={{
              background: `linear-gradient(90deg, ${stops.join(", ")})`,
            }}
          />
          <div className="flex items-center gap-1.5">
            {stops.map((stop, index) => (
              <button
                key={index}
                type="button"
                aria-label={t("colour.stop", { index: index + 1 })}
                aria-pressed={index === stopIndex}
                onClick={() => setStopIndex(index)}
                className={cn(
                  "size-6 cursor-pointer rounded-small shadow-swatch",
                  index === stopIndex &&
                    "outline-2 outline-accent outline-offset-2",
                )}
                style={{ backgroundColor: stop }}
              />
            ))}
            <span className="flex-1" />
            <Button
              kind="ghost"
              size="sm"
              icon={Plus}
              iconOnly
              disabled={stops.length >= MAX_STOPS}
              onClick={() => {
                setStops((current) => [...current, current.at(-1) ?? editing]);
                setStopIndex(stops.length);
              }}
            >
              {t("colour.addStop")}
            </Button>
            <Button
              kind="ghost"
              size="sm"
              icon={Trash2}
              iconOnly
              disabled={stops.length <= MIN_STOPS}
              onClick={() => {
                setStops((current) =>
                  current.filter((_, index) => index !== stopIndex),
                );
                setStopIndex(0);
              }}
            >
              {t("colour.removeStop")}
            </Button>
          </div>
        </div>
      ) : null}
      <HexColorPicker
        color={editing}
        onChange={setEditing}
        aria-label={t("colour.saturation")}
        className="og-colour-picker"
      />
      <HexColorInput
        color={editing}
        onChange={setEditing}
        prefixed
        aria-label={t("colour.hex")}
        className={cn(
          controlFrame,
          "h-button-sm px-2 font-mono text-mono uppercase outline-none",
        )}
      />
      <span className="font-label text-meta text-text-2">
        {t("colour.minecraft")}
      </span>
      <div className="grid grid-cols-8 gap-1.5">
        {(Object.entries(minecraftColours) as [MinecraftColour, string][]).map(
          ([name, hex]) => (
            <button
              key={name}
              type="button"
              title={t(`colour.names.${name}`)}
              aria-label={t(`colour.names.${name}`)}
              onClick={() =>
                mode === "solid"
                  ? setSolid({ kind: "named", name })
                  : setEditing(hex)
              }
              className={cn(
                "aspect-square cursor-pointer rounded-small shadow-swatch",
                mode === "solid" &&
                  solid.kind === "named" &&
                  solid.name === name &&
                  "outline-2 outline-accent outline-offset-2",
              )}
              style={{ backgroundColor: hex }}
            />
          ),
        )}
      </div>
      {recent.length === 0 ? null : (
        <>
          <span className="font-label text-meta text-text-2">
            {t("colour.recent")}
          </span>
          <div className="flex gap-1.5">
            {recent.map((hex) => (
              <button
                key={hex}
                type="button"
                aria-label={hex}
                onClick={() => setEditing(hex)}
                className="size-6 cursor-pointer rounded-small shadow-swatch"
                style={{ backgroundColor: hex }}
              />
            ))}
          </div>
        </>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <Button kind="ghost" size="sm" onClick={onCancel}>
          {t("actions.cancel")}
        </Button>
        <Button
          kind="primary"
          size="sm"
          onClick={() =>
            onApply(mode === "solid" ? solid : { kind: "gradient", stops })
          }
        >
          {applyLabel}
        </Button>
      </div>
    </div>
  );
}
