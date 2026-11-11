// OgCloud's design tokens: the Layered direction on warm greys and violet (D-0209, D-0216). Web and desktop read
// the generated CSS; code that needs numbers (charts, Motion, the desktop window) imports these objects.

export const themeNames = ["light", "dark"] as const;
export type ThemeName = (typeof themeNames)[number];

export interface ThemeColours {
  canvas: string;
  /** The app background below the floating sidebar and the cards. */
  sunken: string;
  surface: string;
  surface2: string;
  surface3: string;
  borderSubtle: string;
  border: string;
  borderStrong: string;
  /** Input and checkbox outlines, at least 3:1 against the surfaces. */
  borderControl: string;
  text: string;
  text2: string;
  text3: string;
  textDisabled: string;
  accent: string;
  accentHover: string;
  accentText: string;
  accentSoft: string;
  onAccent: string;
  success: string;
  successText: string;
  successSoft: string;
  warning: string;
  warningText: string;
  warningSoft: string;
  danger: string;
  dangerText: string;
  dangerSoft: string;
  /** Danger buttons, which carry white text. */
  dangerFill: string;
  info: string;
  infoText: string;
  infoSoft: string;
  /** Chart series in their fixed order; further series fold into "Other". */
  chart1: string;
  chart2: string;
  chart3: string;
  chart4: string;
  chart5: string;
}

export interface ThemeShadows {
  card: string;
  popover: string;
  raised: string;
}

/** Every theme names the same colours and shadows, so a theme missing one does not compile. */
export interface Theme {
  colours: ThemeColours;
  shadows: ThemeShadows;
}

export const themes: Record<ThemeName, Theme> = {
  light: {
    colours: {
      canvas: "#F6F5F2",
      sunken: "#EEECE7",
      surface: "#FFFFFF",
      surface2: "#FAF9F7",
      surface3: "#F0EEEA",
      borderSubtle: "#EEEBE6",
      border: "#E2DED7",
      borderStrong: "#CBC6BD",
      borderControl: "#8F897F",
      text: "#1D1B18",
      text2: "#55504A",
      text3: "#6B655D",
      textDisabled: "#A9A39A",
      accent: "#6A4BF2",
      accentHover: "#5B3DE3",
      accentText: "#5A3BDC",
      accentSoft: "#EFEAFE",
      onAccent: "#FFFFFF",
      success: "#12A26E",
      successText: "#047857",
      successSoft: "#E3F4EC",
      warning: "#E59A0B",
      warningText: "#A3560A",
      warningSoft: "#FCF1DC",
      danger: "#E5484D",
      dangerText: "#C42B27",
      dangerSoft: "#FCE9E7",
      dangerFill: "#C42B27",
      info: "#0891B2",
      infoText: "#0E7490",
      infoSoft: "#E0F3F7",
      chart1: "#6A4BF2",
      chart2: "#DB2777",
      chart3: "#0891B2",
      chart4: "#D97706",
      chart5: "#047857",
    },
    shadows: {
      card: "0 1px 2px rgba(29, 27, 24, 0.05), 0 10px 28px -14px rgba(29, 27, 24, 0.16)",
      popover:
        "0 2px 6px rgba(29, 27, 24, 0.06), 0 18px 40px -16px rgba(29, 27, 24, 0.22)",
      raised: "0 1px 2px rgba(29, 27, 24, 0.1)",
    },
  },
  dark: {
    colours: {
      canvas: "#131210",
      sunken: "#0D0C0B",
      surface: "#1B1A17",
      surface2: "#22201D",
      surface3: "#2B2925",
      borderSubtle: "#262420",
      border: "#322F2A",
      borderStrong: "#4A463F",
      borderControl: "#716B61",
      text: "#F3F1EC",
      text2: "#BDB7AD",
      text3: "#958F85",
      textDisabled: "#5C574F",
      accent: "#6E4DF5",
      accentHover: "#7B5CFF",
      accentText: "#AB97FF",
      accentSoft: "#2C2641",
      onAccent: "#FFFFFF",
      success: "#2EB37C",
      successText: "#4CC795",
      successSoft: "#1E3327",
      warning: "#E8A33A",
      warningText: "#F0B357",
      warningSoft: "#3C301D",
      danger: "#EF5350",
      dangerText: "#F2787A",
      dangerSoft: "#3D2320",
      dangerFill: "#C9342F",
      info: "#22B8D4",
      infoText: "#5CC9DE",
      infoSoft: "#1C3032",
      chart1: "#8B6CFF",
      chart2: "#C94F8A",
      chart3: "#1FA5C0",
      chart4: "#C08414",
      chart5: "#2B9E6E",
    },
    shadows: {
      card: "inset 0 1px 0 rgba(255, 255, 255, 0.04), 0 14px 34px -18px rgba(0, 0, 0, 0.7)",
      popover: "0 24px 56px -20px rgba(0, 0, 0, 0.8)",
      raised:
        "inset 0 1px 0 rgba(255, 255, 255, 0.05), 0 1px 2px rgba(0, 0, 0, 0.5)",
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
    rowHeight: 32,
    controlHeight: 32,
    navItemHeight: 32,
    tableFontSize: 13,
    tableLineHeight: 18,
  },
  comfortable: {
    rowHeight: 40,
    controlHeight: 40,
    navItemHeight: 36,
    tableFontSize: 14,
    tableLineHeight: 20,
  },
};

export const defaultDensity: DensityName = "compact";

export const radius = { control: 10, card: 16, popover: 20 } as const;

/** Small and large buttons; medium ones take the density's control height. */
export const buttonHeights = { sm: 28, lg: 40 } as const;

export const fonts = {
  sans: [
    "Atkinson Hyperlegible Next Variable",
    "ui-sans-serif",
    "system-ui",
    "sans-serif",
  ],
  mono: ["Atkinson Hyperlegible Mono Variable", "ui-monospace", "monospace"],
  /** Previews of in-game text, drawn the way the game shows it (D-0209). */
  game: ["Monocraft", "ui-monospace", "monospace"],
} as const;

/** Labels and buttons use `label`; the variable fonts take any weight. */
export const fontWeights = {
  normal: 400,
  label: 650,
  bold: 700,
  heavy: 750,
} as const;

export interface TypeStyle {
  size: number;
  lineHeight: number;
  weight: number;
  tracking?: string;
}

/** Table text follows the density; everything else is fixed. */
export const typeScale: Record<
  "display" | "title" | "heading" | "body" | "meta" | "mono",
  TypeStyle
> = {
  display: { size: 34, lineHeight: 40, weight: 750, tracking: "-0.02em" },
  title: { size: 22, lineHeight: 28, weight: 750, tracking: "-0.01em" },
  heading: { size: 15, lineHeight: 20, weight: 700 },
  body: { size: 14, lineHeight: 20, weight: 400 },
  meta: { size: 12, lineHeight: 16, weight: 400 },
  mono: { size: 13, lineHeight: 18, weight: 400 },
};

/** Expressive but quick (D-0209): every transition settles within 400 ms, and none runs under reduced motion. */
export const motion = {
  durationMs: { fast: 150, base: 220, slow: 320 },
  easing: { standard: [0.2, 0.7, 0.2, 1] },
  spring: { stiffness: 520, damping: 42, mass: 1 },
} as const;

export const layout = {
  /** The smallest supported layout, also the desktop app's minimum window (D-0170, D-0216). */
  minViewport: { width: 1280, height: 800 },
  sidebarWidth: 232,
  sidebarInset: 8,
  headerHeight: 52,
  formWidth: 720,
} as const;
