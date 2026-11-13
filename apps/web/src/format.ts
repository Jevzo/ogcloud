import { useMemo } from "react";
import { useTranslation } from "react-i18next";

const SECOND_MS = 1_000;
const BYTE_UNITS = [
  "byte",
  "kilobyte",
  "megabyte",
  "gigabyte",
  "terabyte",
] as const;
const BYTE_STEP = 1_000;
const RELATIVE_UNITS: readonly [Intl.RelativeTimeFormatUnit, number][] = [
  ["day", 86_400],
  ["hour", 3_600],
  ["minute", 60],
  ["second", 1],
];

export interface Formats {
  number: (value: number) => string;
  /** A change, with its sign: "+212", "−40", "0". */
  signed: (value: number) => string;
  /** A size in the largest fitting unit: "812 kB", "1.4 GB". */
  bytes: (value: number) => string;
  /** A time of day, such as a chart's axis labels. */
  clock: (at: Date) => string;
  /** A date with its time: "Nov 12, 2026, 2:07 PM". */
  dateTime: (at: Date) => string;
  /** How long ago, in the largest whole unit: "4 minutes ago". */
  ago: (at: Date, now: number) => string;
}

/** Number and time formats in the interface's language. */
export function useFormat(): Formats {
  const { i18n } = useTranslation();
  return useMemo(() => {
    const number = new Intl.NumberFormat(i18n.language);
    const signed = new Intl.NumberFormat(i18n.language, {
      signDisplay: "exceptZero",
    });
    const bytes = BYTE_UNITS.map(
      (unit) =>
        new Intl.NumberFormat(i18n.language, {
          style: "unit",
          unit,
          unitDisplay: "short",
          maximumFractionDigits: 1,
        }),
    );
    const clock = new Intl.DateTimeFormat(i18n.language, {
      hour: "2-digit",
      minute: "2-digit",
    });
    const dateTime = new Intl.DateTimeFormat(i18n.language, {
      dateStyle: "medium",
      timeStyle: "short",
    });
    const relative = new Intl.RelativeTimeFormat(i18n.language, {
      numeric: "auto",
    });
    return {
      number: (value) => number.format(value),
      signed: (value) => signed.format(value),
      bytes: (value) => {
        const step = Math.min(
          BYTE_UNITS.length - 1,
          Math.max(
            0,
            Math.floor(Math.log(Math.max(1, value)) / Math.log(BYTE_STEP)),
          ),
        );
        return bytes[step]?.format(value / BYTE_STEP ** step) ?? String(value);
      },
      clock: (at) => clock.format(at),
      dateTime: (at) => dateTime.format(at),
      ago: (at, now) => {
        const seconds = Math.round((at.getTime() - now) / SECOND_MS);
        const [unit, size] = RELATIVE_UNITS.find(
          ([, length]) => Math.abs(seconds) >= length,
        ) ?? ["second", 1];
        return relative.format(Math.round(seconds / size), unit);
      },
    };
  }, [i18n.language]);
}
