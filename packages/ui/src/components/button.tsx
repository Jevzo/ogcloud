import { cva } from "class-variance-authority";
import { LoaderCircle, type LucideIcon } from "lucide-react";
import type { ComponentProps, ReactNode } from "react";
import { cn } from "../lib/cn.ts";
import { Tooltip } from "./tooltip.tsx";

export type ButtonKind = "primary" | "secondary" | "ghost" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

const ICON_SIZES: Record<ButtonSize, number> = { sm: 15, md: 16, lg: 18 };

const button = cva(
  "inline-flex shrink-0 cursor-pointer select-none items-center justify-center whitespace-nowrap rounded-control border font-label disabled:cursor-not-allowed disabled:opacity-45 aria-busy:cursor-progress [&_svg]:shrink-0",
  {
    variants: {
      kind: {
        primary:
          "border-transparent bg-accent text-on-accent hover:bg-accent-hover",
        secondary:
          "border-border bg-surface text-text shadow-raised hover:bg-surface-2",
        ghost: "border-transparent bg-transparent text-text hover:bg-surface-3",
        danger:
          "border-transparent bg-danger-fill text-on-accent hover:bg-danger-fill/90",
      },
      size: {
        sm: "h-button-sm gap-1.5 px-2.5 text-small",
        md: "h-control gap-2 px-3 text-body",
        lg: "h-button-lg gap-2 px-4 text-body",
      },
      iconOnly: {
        true: "px-0",
        false: "",
      },
    },
    compoundVariants: [
      { iconOnly: true, size: "sm", className: "w-button-sm" },
      { iconOnly: true, size: "md", className: "w-control" },
      { iconOnly: true, size: "lg", className: "w-button-lg" },
    ],
    defaultVariants: { kind: "secondary", size: "md", iconOnly: false },
  },
);

interface CommonProps extends Omit<ComponentProps<"button">, "children"> {
  size?: ButtonSize;
  /** Shows a spinner in place of the icon and keeps the button from being pressed again. */
  loading?: boolean;
}

interface TextButtonProps extends CommonProps {
  kind?: ButtonKind;
  icon?: LucideIcon;
  iconOnly?: false;
  children: ReactNode;
}

/** Icon-only buttons are secondary or ghost; their text is the tooltip and the accessible name (D-0210). */
interface IconButtonProps extends CommonProps {
  kind?: "secondary" | "ghost";
  icon: LucideIcon;
  iconOnly: true;
  children: string;
}

export type ButtonProps = TextButtonProps | IconButtonProps;

/** The one button: four kinds, three sizes, with a label, an icon and a label, or an icon alone. */
export function Button(props: ButtonProps) {
  const {
    kind,
    size = "md",
    loading = false,
    icon: Icon,
    iconOnly = false,
    className,
    children,
    disabled,
    ...rest
  } = props;
  const glyph = loading ? (
    <LoaderCircle
      aria-hidden="true"
      size={ICON_SIZES[size]}
      className="animate-spin"
    />
  ) : Icon ? (
    <Icon aria-hidden="true" size={ICON_SIZES[size]} strokeWidth={1.75} />
  ) : null;
  const element = (
    <button
      type="button"
      {...rest}
      disabled={disabled === true || loading}
      aria-busy={loading || undefined}
      aria-label={iconOnly ? (children as string) : rest["aria-label"]}
      className={cn(button({ kind, size, iconOnly }), className)}
    >
      {glyph}
      {iconOnly ? null : children}
    </button>
  );
  return iconOnly ? <Tooltip content={children}>{element}</Tooltip> : element;
}
