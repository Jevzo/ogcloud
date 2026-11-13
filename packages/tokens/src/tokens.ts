// OgCloud's design tokens: Ink & Honey on the warm admin guidelines (D-0231). Web and desktop read the generated
// CSS; code that needs numbers (charts, Motion, the desktop window) imports these objects.

export const themeNames = ["light", "dark"] as const;
export type ThemeName = (typeof themeNames)[number];

export interface ThemeColours {
  /** The app background behind the cards, and the sign-in backdrop. */
  page: string;
  /** The attached sidebar, a step below the page in light. */
  sunken: string;
  surface: string;
  surface2: string;
  surface3: string;
  /** Popovers, menus, dialogs and toasts: white in light, a step closer than the cards in dark. */
  floating: string;
  borderSubtle: string;
  border: string;
  borderStrong: string;
  /** Input and checkbox outlines, at least 3:1 against the surfaces. */
  borderControl: string;
  text: string;
  text2: string;
  text3: string;
  textDisabled: string;
  /** Primary actions, active nav, focus, links and the key series; also their text, which clears 4.5:1. */
  accent: string;
  accentHover: string;
  /** The lighter top of the primary button's same-hue gradient. */
  accentTop: string;
  accentSoft: string;
  onAccent: string;
  /** The second accent: highlight badges and charts only, never a button or a state. */
  honey: string;
  onHoney: string;
  /** Each status is one colour for its text, icon and fill, plus a tint behind it. */
  success: string;
  successSoft: string;
  warning: string;
  warningSoft: string;
  danger: string;
  dangerSoft: string;
  info: string;
  infoSoft: string;
  /** Chart series in their fixed order; further series fold into "Other". */
  chart1: string;
  chart2: string;
  chart3: string;
  chart4: string;
  chart5: string;
  /** Dims the page behind dialogs and drawers. */
  scrim: string;
  /** A QR code's modules and ground: dark on light in both themes, as scanners read them. */
  qrInk: string;
  qrGround: string;
}

/** Elevation by role: warm shadows in light, a hairline highlight instead of drop shadows in dark. */
export interface ThemeShadows {
  /** Secondary buttons, chips and raised icons. */
  raised: string;
  card: string;
  /** One step above a card, for a clickable card under the pointer. */
  lift: string;
  /** Popovers, menus, toasts and the save bar. */
  popover: string;
  /** Dialogs and drawers. */
  overlay: string;
  /** Next to the focus ring: a soft accent glow, dark mode only. */
  glow: string;
  /** Outlines a colour swatch so white and black ones stay visible on any surface. */
  swatch: string;
}

/** Every theme names the same colours and shadows, so a theme missing one does not compile. */
export interface Theme {
  colours: ThemeColours;
  shadows: ThemeShadows;
}

const SWATCH = "inset 0 0 0 1px rgba(127, 127, 127, 0.35)";
const DARK_EDGE = "inset 0 1px 0 rgba(255, 255, 255, 0.04)";

export const themes: Record<ThemeName, Theme> = {
  light: {
    colours: {
      page: "#FBF8F1",
      sunken: "#F1ECE2",
      surface: "#FFFFFF",
      surface2: "#F7F4ED",
      surface3: "#F1ECE2",
      floating: "#FFFFFF",
      borderSubtle: "#EFE9DD",
      border: "#E8E0CF",
      borderStrong: "#D0C8B6",
      borderControl: "#8B8475",
      text: "#25231F",
      text2: "#4D493F",
      text3: "#6A6457",
      textDisabled: "#ADA79A",
      accent: "#2F3E73",
      accentHover: "#243163",
      accentTop: "#3C4C82",
      accentSoft: "#E6E9F4",
      onAccent: "#FFFFFF",
      honey: "#E0A12B",
      onHoney: "#25231F",
      success: "#37733A",
      successSoft: "#E6F1E3",
      warning: "#8A5A00",
      warningSoft: "#FBF0D5",
      danger: "#B3204A",
      dangerSoft: "#FAE3E6",
      info: "#37689E",
      infoSoft: "#E3ECF6",
      chart1: "#4050A0",
      chart2: "#B27901",
      chart3: "#009399",
      chart4: "#BD5833",
      chart5: "#8E3A6E",
      scrim: "rgba(30, 20, 10, 0.4)",
      qrInk: "#000000",
      qrGround: "#FFFFFF",
    },
    shadows: {
      raised:
        "0 1px 2px rgba(60, 40, 20, 0.06), 0 1px 1px rgba(60, 40, 20, 0.04)",
      card: "0 1px 2px rgba(60, 40, 20, 0.06), 0 1px 1px rgba(60, 40, 20, 0.04)",
      lift: "0 4px 12px rgba(60, 40, 20, 0.08), 0 1px 3px rgba(60, 40, 20, 0.06)",
      popover:
        "0 4px 12px rgba(60, 40, 20, 0.08), 0 1px 3px rgba(60, 40, 20, 0.06)",
      overlay:
        "0 16px 40px rgba(60, 40, 20, 0.14), 0 4px 12px rgba(60, 40, 20, 0.08)",
      glow: "0 0 0 0 transparent",
      swatch: SWATCH,
    },
  },
  dark: {
    colours: {
      page: "#171612",
      sunken: "#171612",
      surface: "#211F19",
      surface2: "#28261F",
      surface3: "#2F2D25",
      floating: "#28261F",
      borderSubtle: "#2D2A23",
      border: "#39362B",
      borderStrong: "#575245",
      borderControl: "#807C6D",
      text: "#F1ECDF",
      text2: "#B4AD9C",
      text3: "#989485",
      textDisabled: "#5E5B50",
      accent: "#A3B2EE",
      accentHover: "#B7C5F9",
      accentTop: "#AEBDFA",
      accentSoft: "#262B45",
      onAccent: "#171612",
      honey: "#F0B849",
      onHoney: "#171612",
      success: "#7FC97A",
      successSoft: "#1E2E1D",
      warning: "#F2C158",
      warningSoft: "#33290F",
      danger: "#F0798C",
      dangerSoft: "#3A1C22",
      info: "#86B3E6",
      infoSoft: "#1B2838",
      chart1: "#6679CD",
      chart2: "#B27901",
      chart3: "#179FA5",
      chart4: "#D26B46",
      chart5: "#A24C80",
      scrim: "rgba(10, 8, 4, 0.6)",
      qrInk: "#000000",
      qrGround: "#FFFFFF",
    },
    shadows: {
      raised: DARK_EDGE,
      card: DARK_EDGE,
      lift: "inset 0 1px 0 rgba(255, 255, 255, 0.07)",
      popover: "inset 0 1px 0 rgba(255, 255, 255, 0.05)",
      overlay: "inset 0 1px 0 rgba(255, 255, 255, 0.05)",
      glow: "0 0 0 6px rgba(163, 178, 238, 0.25)",
      swatch: SWATCH,
    },
  },
};

export const densityNames = ["compact", "comfortable"] as const;
export type DensityName = (typeof densityNames)[number];

/** Pixel sizes that change with the user's density; medium buttons share the control height. */
export interface Density {
  rowHeight: number;
  controlHeight: number;
  navItemHeight: number;
  tableFontSize: number;
  tableLineHeight: number;
}

export const densities: Record<DensityName, Density> = {
  compact: {
    rowHeight: 36,
    controlHeight: 36,
    navItemHeight: 36,
    tableFontSize: 13,
    tableLineHeight: 18,
  },
  comfortable: {
    rowHeight: 48,
    controlHeight: 40,
    navItemHeight: 40,
    tableFontSize: 14,
    tableLineHeight: 20,
  },
};

export const defaultDensity: DensityName = "compact";

/** `small` rounds keys, checkboxes and chart bars; `game` the frames of in-game previews. */
export const radius = {
  game: 2,
  small: 3,
  control: 6,
  card: 10,
  popover: 12,
} as const;

/** Small and large buttons; medium ones take the density's control height. */
export const buttonHeights = { sm: 32, lg: 40 } as const;

export const fonts = {
  sans: [
    "Atkinson Hyperlegible Next Variable",
    "ui-sans-serif",
    "system-ui",
    "sans-serif",
  ],
  mono: ["Atkinson Hyperlegible Mono Variable", "ui-monospace", "monospace"],
  /** Previews of in-game text, drawn the way the game shows it. */
  game: ["Monocraft", "ui-monospace", "monospace"],
} as const;

/** Labels, nav and buttons use `label`; titles and KPIs `bold`. No lighter weights. */
export const fontWeights = {
  normal: 400,
  label: 500,
  bold: 600,
} as const;

export interface TypeStyle {
  size: number;
  lineHeight: number;
  weight: number;
  tracking?: string;
}

/** Table text follows the density; everything else is fixed. */
export const typeScale: Record<
  | "kpi"
  | "title"
  | "heading"
  | "body"
  | "small"
  | "meta"
  | "mono"
  | "wordmark"
  | "wordmarkLarge"
  | "game",
  TypeStyle
> = {
  /** The number a screen is for, in a KPI tile. */
  kpi: { size: 32, lineHeight: 36, weight: 600, tracking: "-0.02em" },
  title: { size: 24, lineHeight: 30, weight: 600, tracking: "-0.01em" },
  heading: { size: 16, lineHeight: 20, weight: 600 },
  body: { size: 14, lineHeight: 20, weight: 400 },
  /** Small buttons, hints and field help. */
  small: { size: 13, lineHeight: 18, weight: 400 },
  meta: { size: 12, lineHeight: 16, weight: 400 },
  mono: { size: 13, lineHeight: 18, weight: 400 },
  /** "OgCloud" beside the mascot: in the sidebar, and large on the sign-in and setup pages. */
  wordmark: { size: 17, lineHeight: 24, weight: 600, tracking: "-0.02em" },
  wordmarkLarge: { size: 26, lineHeight: 32, weight: 600, tracking: "-0.02em" },
  /** In-game text in Monocraft, at the size the game draws it. */
  game: { size: 15, lineHeight: 22, weight: 400 },
};

/**
 * Fast, quiet motion (D-0231): the more often something is seen, the shorter it runs; exits take three quarters of
 * their entrance; no springs. None of it runs under reduced motion.
 */
export const motion = {
  durationMs: { instant: 70, quick: 110, fast: 150, base: 240, slow: 400 },
  easing: {
    standard: [0.2, 0, 0.38, 0.9],
    enter: [0, 0, 0.38, 0.9],
    exit: [0.2, 0, 1, 0.9],
    expressive: [0.4, 0.14, 0.3, 1],
  },
  exitRatio: 0.75,
  /** One loop of the skeleton shimmer. */
  shimmerMs: 1400,
  /** A changed value's accent flash, fading out. */
  flashMs: 1000,
  /** KPIs count up once, on first load. */
  countUpMs: 500,
} as const;

/** The guidelines' effects, each with one job; at most two kinds in one view (D-0231). */
export const effects = {
  /** Floating layers over scrolling content: the top bar, ⌘K, popovers and toasts. */
  glass: { opacity: 0.78, blurPx: 12, saturate: 1.3 },
  /** Noise over the page background and empty states. */
  grainOpacity: 0.03,
  /** The faint accent-tint wash at the top of each page. */
  wash: { strength: 0.55, heightPx: 240 },
  /** How far a clickable card rises under the pointer. */
  liftPx: 2,
} as const;

export const layout = {
  /** The smallest supported layout, also the desktop app's minimum window (D-0170). */
  minViewport: { width: 1280, height: 800 },
  sidebarWidth: 248,
  /** The collapsed sidebar: icons only. */
  sidebarRailWidth: 64,
  headerHeight: 56,
  formWidth: 720,
  /** The sign-in pages' card, and the setup wizard with its steps beside it. */
  brandedWidth: 416,
  setupWidth: 800,
  /** The colour picker's popover, sized to its colour area. */
  colourPickerWidth: 284,
  /** The side panel beside a settings form, between these widths (D-0210); wider on large monitors. */
  asideMinWidth: 280,
  asideWidth: 440,
  asideWideWidth: 560,
  /** The narrower column of a detail page or the overview. */
  sideMinWidth: 320,
  /** A record's drawer beside its list. */
  drawerWidth: 480,
  /** A number field beside a text field, such as a weight or a player limit. */
  numberFieldWidth: 160,
  /** The widest a toast grows before its message wraps. */
  toastMaxWidth: 400,
  /** Screenshot reviews run at the smallest layout and on a large monitor (AGENTS.md §13, D-0170). */
  reviewViewports: [
    { name: "laptop", width: 1280, height: 800 },
    { name: "monitor", width: 2560, height: 1440 },
  ],
} as const;
