import type {ReactNode} from "react";
import type {IconType} from "react-icons";

interface DetailStatCardProps {
    label: string;
    value: ReactNode;
    meta?: ReactNode;
    icon?: IconType;
    tone?: "primary" | "success" | "warning" | "neutral";
    compact?: boolean;
}

const TONE_CLASSES: Record<
    NonNullable<DetailStatCardProps["tone"]>,
    { line: string; badge: string; value: string }
> = {
    primary: {
        line: "from-primary/70 via-secondary/40 to-transparent",
        badge: "bg-primary/12 text-primary ring-1 ring-primary/15",
        value: "text-white",
    },
    success: {
        line: "from-emerald-400/70 via-emerald-400/30 to-transparent",
        badge: "bg-emerald-500/12 text-emerald-300 ring-1 ring-emerald-400/15",
        value: "text-emerald-300",
    },
    warning: {
        line: "from-amber-400/70 via-amber-300/30 to-transparent",
        badge: "bg-amber-500/12 text-amber-300 ring-1 ring-amber-400/15",
        value: "text-amber-200",
    },
    neutral: {
        line: "from-slate-500/65 via-slate-400/20 to-transparent",
        badge: "bg-slate-800 text-slate-300 ring-1 ring-slate-700/70",
        value: "text-white",
    },
};

const DetailStatCard = ({
                            label,
                            value,
                            meta,
                            icon: Icon,
                            tone = "neutral",
                            compact = false,
                        }: DetailStatCardProps) => {
    const toneStyle = TONE_CLASSES[tone];

    return (
        <div
            className={`relative overflow-hidden rounded-xl border border-slate-800/90 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 shadow-[inset_0_1px_0_rgba(148,163,184,0.04)] ${
                compact ? "p-3.5" : "p-4"
            }`}
        >
            <div
                className={`pointer-events-none absolute inset-x-0 top-0 h-px bg-linear-to-r ${toneStyle.line}`}
                aria-hidden="true"
            />

            <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                    <p
                        className={`font-semibold uppercase text-slate-500 ${
                            compact
                                ? "text-[10px] tracking-[0.16em]"
                                : "text-[11px] tracking-[0.18em]"
                        }`}
                    >
                        {label}
                    </p>
                    <div
                        className={`font-bold ${toneStyle.value} ${
                            compact ? "mt-2.5 text-base" : "mt-3 text-lg"
                        }`}
                    >
                        {value}
                    </div>
                    {meta ? (
                        <div className={`text-slate-500 ${compact ? "mt-1 text-[11px]" : "mt-1.5 text-xs"}`}>
                            {meta}
                        </div>
                    ) : null}
                </div>

                {Icon ? (
                    <div
                        className={`flex shrink-0 items-center justify-center rounded-xl ${toneStyle.badge} ${
                            compact ? "h-9 w-9" : "h-10 w-10"
                        }`}
                    >
                        <Icon className={compact ? "h-4 w-4" : "h-4.5 w-4.5"}/>
                    </div>
                ) : null}
            </div>
        </div>
    );
};

export default DetailStatCard;
