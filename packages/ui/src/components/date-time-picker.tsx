import { addDays, addHours, setHours, setMinutes } from "date-fns";
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { DayPicker } from "react-day-picker";
import { useTranslation } from "react-i18next";
import { cn } from "../lib/cn.ts";
import { Button } from "./button.tsx";
import { controlFrame } from "./input.tsx";
import { Popover } from "./popover.tsx";

/** No end date: a permanent rank, for instance. */
export const PERMANENT = null;

const QUICK_PICKS = [
  { key: "hour", at: (now: Date) => addHours(now, 1) },
  { key: "day", at: (now: Date) => addDays(now, 1) },
  { key: "week", at: (now: Date) => addDays(now, 7) },
  { key: "month", at: (now: Date) => addDays(now, 30) },
] as const;

const HOURS_AND_MINUTES = /^(\d{1,2}):(\d{2})$/;

interface DateTimePickerProps {
  id?: string;
  value: Date | null;
  onChange: (value: Date | null) => void;
  /** Quick picks count from here; injected so pages and tests share one clock (AGENTS.md §10). */
  now: Date;
  allowPermanent?: boolean;
  /** Shown while no time is picked and permanence is not offered, e.g. "Any time" in a filter. */
  placeholder?: string;
  "aria-describedby"?: string | undefined;
  "aria-invalid"?: true | undefined;
}

/** A date and time with quick picks; changes apply only when confirmed. */
export function DateTimePicker({
  value,
  onChange,
  now,
  allowPermanent = false,
  placeholder,
  ...trigger
}: DateTimePickerProps) {
  const { t, i18n } = useTranslation("ui");
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<Date | null>(value);
  const [time, setTime] = useState(formatTime(value ?? now, i18n.language));
  const format = new Intl.DateTimeFormat(i18n.language, {
    dateStyle: "medium",
    timeStyle: "short",
  });

  const openWith = (next: boolean) => {
    if (next) {
      setDraft(value);
      setTime(formatTime(value ?? now, i18n.language));
    }
    setOpen(next);
  };
  const pick = (date: Date | null) => {
    setDraft(date);
    if (date !== null) setTime(formatTime(date, i18n.language));
  };
  const withTime = (date: Date): Date => {
    const match = HOURS_AND_MINUTES.exec(time);
    return match
      ? setMinutes(setHours(date, Number(match[1])), Number(match[2]))
      : date;
  };

  return (
    <Popover
      open={open}
      onOpenChange={openWith}
      className="flex overflow-hidden"
      trigger={
        <button
          type="button"
          data-skeleton=""
          aria-expanded={open}
          {...trigger}
          className={cn(
            controlFrame,
            "flex h-control w-full cursor-pointer items-center gap-2 px-2.5 text-left outline-none focus-visible:border-accent focus-visible:ring-3 focus-visible:ring-accent/30 aria-expanded:border-accent aria-expanded:ring-3 aria-expanded:ring-accent/30",
          )}
        >
          <CalendarDays
            aria-hidden="true"
            size={16}
            strokeWidth={1.75}
            className="text-text-2"
          />
          <span className="flex-1 truncate tabular-nums">
            {value === null
              ? allowPermanent
                ? t("date.permanent")
                : (placeholder ?? t("date.none"))
              : format.format(value)}
          </span>
        </button>
      }
    >
      <div className="flex w-32 flex-col gap-0.5 border-border-subtle border-r p-2">
        {QUICK_PICKS.map((quick) => {
          const at = quick.at(now);
          return (
            <QuickPick
              key={quick.key}
              active={draft?.getTime() === at.getTime()}
              onPress={() => pick(at)}
            >
              {t(`date.quick.${quick.key}`)}
            </QuickPick>
          );
        })}
        {allowPermanent ? (
          <QuickPick active={draft === null} onPress={() => pick(PERMANENT)}>
            {t("date.permanent")}
          </QuickPick>
        ) : null}
      </div>
      <div className="flex flex-col p-3">
        <DayPicker
          mode="single"
          required={false}
          selected={draft ?? undefined}
          onSelect={(date) => pick(date === undefined ? null : withTime(date))}
          defaultMonth={draft ?? now}
          disabled={{ before: now }}
          weekStartsOn={1}
          today={now}
          components={{
            Chevron: ({ orientation }) =>
              orientation === "left" ? (
                <ChevronLeft aria-hidden="true" size={16} />
              ) : (
                <ChevronRight aria-hidden="true" size={16} />
              ),
          }}
          labels={{
            labelPrevious: () => t("date.previousMonth"),
            labelNext: () => t("date.nextMonth"),
          }}
          classNames={{
            root: "relative",
            months: "flex",
            month: "flex flex-col gap-2",
            month_caption: "flex h-7 items-center",
            caption_label: "font-bold",
            nav: "absolute top-0 right-0 flex gap-0.5",
            button_previous:
              "grid size-7 cursor-pointer place-items-center rounded-control text-text-2 hover:bg-surface-3",
            button_next:
              "grid size-7 cursor-pointer place-items-center rounded-control text-text-2 hover:bg-surface-3",
            month_grid: "border-separate border-spacing-0.5",
            weekday: "h-6 w-8.5 font-normal text-meta text-text-3",
            day: "p-0",
            day_button:
              "grid h-7.5 w-8.5 cursor-pointer place-items-center rounded-control text-small tabular-nums hover:bg-surface-3",
            today:
              "font-bold [&>button]:shadow-[inset_0_0_0_1px_var(--og-border-control)]",
            selected:
              "[&>button]:bg-accent [&>button]:font-bold [&>button]:text-on-accent",
            outside: "text-text-disabled",
            disabled:
              "text-text-disabled [&>button]:cursor-not-allowed [&>button]:hover:bg-transparent",
          }}
        />
        <div className="mt-2.5 flex items-center gap-2 border-border-subtle border-t pt-2.5">
          <label className="flex items-center gap-2 text-small text-text-2">
            {t("date.time")}
            <input
              value={time}
              inputMode="numeric"
              onChange={(event) => setTime(event.target.value)}
              className={cn(
                controlFrame,
                "h-button-sm w-18 px-2 text-small tabular-nums outline-none",
              )}
            />
          </label>
          <span className="flex-1" />
          <Button kind="ghost" size="sm" onClick={() => setOpen(false)}>
            {t("actions.cancel")}
          </Button>
          <Button
            kind="primary"
            size="sm"
            onClick={() => {
              onChange(draft === null ? null : withTime(draft));
              setOpen(false);
            }}
          >
            {t("actions.apply")}
          </Button>
        </div>
      </div>
    </Popover>
  );
}

function QuickPick({
  active,
  onPress,
  children,
}: {
  active: boolean;
  onPress: () => void;
  children: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onPress}
      className={cn(
        "h-7.5 cursor-pointer rounded-control px-2 text-left text-small",
        active
          ? "bg-accent-soft font-label text-accent-text"
          : "hover:bg-surface-3",
      )}
    >
      {children}
    </button>
  );
}

function formatTime(date: Date, language: string): string {
  return new Intl.DateTimeFormat(language, {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(date);
}
