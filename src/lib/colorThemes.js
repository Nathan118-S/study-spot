// Colour themes (Settings > Appearance > Theme).
//
// "studyspot" is the default and changes nothing: its colours stay in
// src/index.css exactly as tuned. Every other theme is derived the same way
// the default palette is built (60/20/20):
//   - 60% base: a near-white canvas tinted toward the theme (dark: a near-black one)
//   - 20% ink: a deep "navy" for structure (titles, icons, chips)
//   - 20% accent: the action colour (buttons, selection, progress)
// The same values are mirrored in ios/StudySpot/State/ColorTheme.swift and
// android/.../ui/theme/ColorThemes.kt; the choice syncs through user.data.theme.

export const DEFAULT_COLOR_THEME = 'studyspot';
const STORAGE_KEY = 'cf-color-theme';

// accent / accentDark: [h, s, l]; ink: [h, s] (light ink is l=20%);
// base / baseDark: [h, s] for the canvas tint.
export const COLOR_THEMES = [
  { id: 'studyspot', name: 'Study Spot', accent: [33, 87, 58], accentDark: [33, 87, 58], ink: [222, 39], base: [40, 60], baseDark: [30, 17] },
  { id: 'ocean', name: 'Ocean', accent: [211, 78, 52], accentDark: [211, 82, 58], ink: [208, 55], base: [205, 60], baseDark: [212, 18] },
  { id: 'forest', name: 'Forest', accent: [150, 50, 38], accentDark: [148, 48, 48], ink: [160, 38], base: [80, 45], baseDark: [120, 12] },
  { id: 'grape', name: 'Grape', accent: [266, 58, 58], accentDark: [266, 70, 68], ink: [256, 40], base: [275, 50], baseDark: [270, 14] },
  { id: 'rose', name: 'Rose', accent: [342, 76, 60], accentDark: [342, 80, 66], ink: [332, 36], base: [350, 60], baseDark: [345, 15] },
  { id: 'graphite', name: 'Graphite', accent: [220, 8, 30], accentDark: [220, 10, 82], onAccentDark: '220 10% 9%', ink: [220, 12], base: [220, 10], baseDark: [220, 6] },
];

const byId = Object.fromEntries(COLOR_THEMES.map((t) => [t.id, t]));

export function isColorTheme(id) {
  return typeof id === 'string' && Object.prototype.hasOwnProperty.call(byId, id);
}

const hsl = (h, s, l) => `${h} ${Math.round(Math.min(100, s) * 10) / 10}% ${l}%`;

/** CSS variables for one theme in light or dark mode (same roles as index.css). */
export function themeVars(theme, dark) {
  const [ih, is] = theme.ink;
  const k = is / 39; // the default ink's saturation is 39%
  if (!dark) {
    const [ah, as, al] = theme.accent;
    const accent = hsl(ah, as, al);
    const navy = hsl(ih, is, 20);
    const bg = hsl(theme.base[0], theme.base[1], 99);
    const onAccent = theme.onAccent || '0 0% 100%';
    return {
      '--navy': navy, '--background': bg, '--foreground': hsl(ih, 30 * k, 12),
      '--card-foreground': hsl(ih, 30 * k, 12), '--popover-foreground': hsl(ih, 30 * k, 12),
      '--primary': accent, '--primary-foreground': onAccent,
      '--secondary': hsl(ih, 30 * k, 94), '--secondary-foreground': navy,
      '--muted': hsl(ih, 25 * k, 95), '--muted-foreground': hsl(ih, 14 * k, 46),
      '--accent': hsl(ih, 30 * k, 94), '--accent-foreground': navy,
      '--border': hsl(ih, 20 * k, 90), '--input': hsl(ih, 18 * k, 87), '--ring': accent,
      '--chart-1': accent, '--chart-2': hsl(ih, is, 30),
      '--sidebar-background': bg, '--sidebar-foreground': navy,
      '--sidebar-primary': accent, '--sidebar-primary-foreground': onAccent,
      '--sidebar-accent': hsl(ih, 30 * k, 93), '--sidebar-accent-foreground': navy,
      '--sidebar-border': hsl(ih, 20 * k, 89), '--sidebar-ring': accent,
    };
  }
  const [ah, as, al] = theme.accentDark;
  const accent = hsl(ah, as, al);
  const navy = hsl(ih + 1, Math.min(60, 44 * k), 76);
  const [bh, bs] = theme.baseDark;
  const bg = hsl(bh, bs, 7);
  const card = hsl(bh, bs, 11);
  const onAccent = theme.onAccentDark || '0 0% 100%';
  return {
    // Every variable the light block sets must be set here too: that block's
    // selector is more specific than index.css's .dark.
    '--navy': navy, '--background': bg, '--card': card, '--popover': card,
    '--foreground': '0 0% 100%', '--card-foreground': '0 0% 100%', '--popover-foreground': '0 0% 100%',
    '--primary': accent, '--primary-foreground': onAccent,
    '--secondary': hsl(ih + 1, 18 * k, 18), '--secondary-foreground': navy,
    '--muted': hsl(ih + 1, 12 * k, 16), '--muted-foreground': hsl(ih + 1, 14 * k, 62),
    '--accent': hsl(ih + 1, 18 * k, 18), '--accent-foreground': navy,
    '--border': hsl(ih + 1, 10 * k, 20), '--input': hsl(ih + 1, 10 * k, 22), '--ring': accent,
    '--chart-1': accent, '--chart-2': hsl(ih + 1, Math.min(60, 44 * k), 70),
    '--sidebar-background': bg, '--sidebar-foreground': navy,
    '--sidebar-primary': accent, '--sidebar-primary-foreground': onAccent,
    '--sidebar-accent': hsl(ih + 1, 18 * k, 18), '--sidebar-accent-foreground': navy,
    '--sidebar-border': hsl(ih + 1, 10 * k, 18), '--sidebar-ring': accent,
  };
}

/** The drifting live-background glows (index.css), re-hued around the accent. */
function blobRules(theme) {
  const [h, s] = theme.accent;
  const sat = (x) => Math.round(Math.min(x, s * 1.4));
  const blobs = [
    [h + 5, sat(100), 70, 0.9],
    [h - 9, sat(95), 66, 0.75],
    [h + 12, sat(100), 72, 0.7],
    [h - 18, sat(90), 70, 0.55],
  ];
  return blobs
    .map(([bh, bs, bl, a], i) =>
      `:root[data-theme="${theme.id}"] .live-bg__blob--${i + 1} { background: radial-gradient(closest-side, hsl(${(bh + 360) % 360} ${bs}% ${bl}% / ${a}), transparent); }`)
    .join('\n');
}

function block(selector, vars) {
  return `${selector} {\n${Object.entries(vars).map(([k, v]) => `  ${k}: ${v};`).join('\n')}\n}`;
}

/** The stylesheet for every non-default theme. */
export function colorThemesCss() {
  return COLOR_THEMES.filter((t) => t.id !== DEFAULT_COLOR_THEME)
    .map((t) => [
      block(`:root[data-theme="${t.id}"]`, themeVars(t, false)),
      block(`:root.dark[data-theme="${t.id}"]`, themeVars(t, true)),
      blobRules(t),
    ].join('\n'))
    .join('\n');
}

/** Swatch colours for previews: { base, card, ink, accent } as CSS colours. */
export function themeSwatch(theme, dark) {
  if (theme.id === DEFAULT_COLOR_THEME) {
    return dark
      ? { base: '#15120F', card: '#221D18', ink: '#A9B8DC', accent: '#F19D37', onAccent: '#FFFFFF' }
      : { base: '#FEFDFB', card: '#FFFFFF', ink: '#1F2B47', accent: '#F19D37', onAccent: '#FFFFFF' };
  }
  const v = themeVars(theme, dark);
  const c = (x) => `hsl(${x})`;
  return {
    base: c(v['--background']),
    card: dark ? c(v['--card']) : '#FFFFFF',
    ink: c(v['--navy']),
    accent: c(v['--primary']),
    onAccent: c(v['--primary-foreground']),
  };
}

function installStylesheet() {
  if (typeof document === 'undefined' || document.getElementById('cf-color-themes')) return;
  const style = document.createElement('style');
  style.id = 'cf-color-themes';
  style.textContent = colorThemesCss();
  document.head.appendChild(style);
}

export function readStoredColorTheme() {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return isColorTheme(v) ? v : DEFAULT_COLOR_THEME;
  } catch {
    return DEFAULT_COLOR_THEME;
  }
}

/** Applies a theme to the page and remembers it on this device. */
export function applyColorTheme(id) {
  const themeId = isColorTheme(id) ? id : DEFAULT_COLOR_THEME;
  if (typeof document === 'undefined') return themeId;
  installStylesheet();
  const root = document.documentElement;
  if (themeId === DEFAULT_COLOR_THEME) root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', themeId);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    const [h, s, l] = byId[themeId].accent;
    meta.setAttribute('content', themeId === DEFAULT_COLOR_THEME ? '#f19d37' : `hsl(${h} ${s}% ${l}%)`);
  }
  try {
    localStorage.setItem(STORAGE_KEY, themeId);
  } catch {
    // Storage unavailable; the theme still applies for this visit.
  }
  window.dispatchEvent(new CustomEvent('cf-color-theme-change', { detail: themeId }));
  return themeId;
}

/** Follows the signed-in user's saved theme (user.data.theme), when there is one. */
export function syncColorThemeFromUser(user) {
  const id = user?.data?.theme;
  if (isColorTheme(id) && id !== readStoredColorTheme()) applyColorTheme(id);
}

// Apply the remembered theme as soon as the app loads, before first paint.
if (typeof window !== 'undefined') {
  const stored = readStoredColorTheme();
  if (stored !== DEFAULT_COLOR_THEME) applyColorTheme(stored);
}
