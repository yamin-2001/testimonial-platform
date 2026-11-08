/**
 * The rendering of each theme.
 *
 * The backend owns *which* themes exist and whether one needs Premium (PRD
 * 4.4); it does not own how they look. The keys here must match the
 * backend's `Theme` enum, and the premium flags must match the backend's
 * `Tier`, or the preview will show something the backend will not accept.
 */

export interface ThemeStyle {
  /** The page background. */
  readonly background: string;
  /** The card background. */
  readonly surface: string;
  /** The primary text colour. */
  readonly text: string;
  /** The muted text colour. */
  readonly muted: string;
  /** The border colour. */
  readonly border: string;
  /** The accent colour, used for buttons and highlights. */
  readonly accent: string;
  /** The text colour on the accent. */
  readonly accentText: string;
  /** The heading font stack. */
  readonly headingFont: string;
  /** The body font stack. */
  readonly bodyFont: string;
  /** The heading weight. */
  readonly headingWeight: number;
  /** Whether the heading is italic, for the editorial feel. */
  readonly headingItalic: boolean;
  /** The extra letter spacing on the heading. */
  readonly headingTracking: string;
}

const SANS =
  'ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
const SERIF = 'ui-serif, Georgia, Cambria, "Times New Roman", Times, serif';

/**
 * Every theme's style, keyed by the backend's theme key.
 */
export const THEME_STYLES: Record<string, ThemeStyle> = {
  light: {
    background: "#fafafa",
    surface: "#ffffff",
    text: "#18181b",
    muted: "#71717a",
    border: "#e4e4e7",
    accent: "#18181b",
    accentText: "#ffffff",
    headingFont: SANS,
    bodyFont: SANS,
    headingWeight: 700,
    headingItalic: false,
    headingTracking: "-0.02em",
  },
  dark: {
    background: "#18181b",
    surface: "#27272a",
    text: "#fafafa",
    muted: "#a1a1aa",
    border: "#3f3f46",
    accent: "#fafafa",
    accentText: "#18181b",
    headingFont: SANS,
    bodyFont: SANS,
    headingWeight: 700,
    headingItalic: false,
    headingTracking: "-0.02em",
  },
  editorial: {
    background: "#fdfaf3",
    surface: "#fffdf8",
    text: "#1c1917",
    muted: "#78716c",
    border: "#e7e0d0",
    accent: "#7c2d12",
    accentText: "#fffdf8",
    headingFont: SERIF,
    bodyFont: SANS,
    headingWeight: 600,
    headingItalic: false,
    headingTracking: "-0.01em",
  },
  midnight: {
    background: "#0b1020",
    surface: "#151c33",
    text: "#eef2ff",
    muted: "#94a3c4",
    border: "#28304d",
    accent: "#818cf8",
    accentText: "#0b1020",
    headingFont: SANS,
    bodyFont: SANS,
    headingWeight: 700,
    headingItalic: false,
    headingTracking: "-0.03em",
  },
  sunset: {
    background: "#fff4ec",
    surface: "#ffffff",
    text: "#431407",
    muted: "#9a5b3d",
    border: "#f6d9c4",
    accent: "#ea580c",
    accentText: "#ffffff",
    headingFont: SANS,
    bodyFont: SANS,
    headingWeight: 800,
    headingItalic: false,
    headingTracking: "-0.02em",
  },
};

/**
 * The style for a theme key.
 *
 * A Space always holds a key the backend accepted, but an unknown one falls
 * back to Light rather than rendering nothing.
 */
export function themeStyle(key: string): ThemeStyle {
  return THEME_STYLES[key] ?? THEME_STYLES.light;
}

/**
 * The inline styles for a preview or a public page.
 *
 * Inline styles rather than Tailwind classes, because the theme is chosen at
 * runtime and the colour pairs are not known at build time.
 */
export function themeCss(key: string): {
  page: React.CSSProperties;
  card: React.CSSProperties;
  heading: React.CSSProperties;
  body: React.CSSProperties;
  muted: React.CSSProperties;
  input: React.CSSProperties;
  button: React.CSSProperties;
  /** The accent colour, for the small highlights such as the stars. */
  accent: string;
} {
  const style = themeStyle(key);

  return {
    page: {
      backgroundColor: style.background,
      color: style.text,
      fontFamily: style.bodyFont,
    },
    card: {
      backgroundColor: style.surface,
      borderColor: style.border,
    },
    heading: {
      fontFamily: style.headingFont,
      fontWeight: style.headingWeight,
      fontStyle: style.headingItalic ? "italic" : "normal",
      letterSpacing: style.headingTracking,
      color: style.text,
    },
    body: {
      color: style.text,
      fontFamily: style.bodyFont,
    },
    muted: {
      color: style.muted,
      fontFamily: style.bodyFont,
    },
    input: {
      backgroundColor: style.background,
      color: style.text,
      borderColor: style.border,
      fontFamily: style.bodyFont,
    },
    button: {
      backgroundColor: style.accent,
      color: style.accentText,
      fontFamily: style.bodyFont,
    },
    accent: style.accent,
  };
}
