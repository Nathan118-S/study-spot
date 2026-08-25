import { useState, useEffect } from 'react';

const THEME_KEY = 'cf-theme';
const SYNC_KEY = 'cf-theme-sync';
const ANIM_KEY = 'cf-animations';

export const THEMES = [
  { id: 'light', label: 'Light' },
  { id: 'dark', label: 'Dark' },
  { id: 'deep-blue', label: 'Deep Blue' },
  { id: 'midnight', label: 'Midnight' },
];

const THEME_CLASSES = {
  'deep-blue': 'theme-deep-blue',
  midnight: 'theme-midnight',
};

export function getSystemDark() {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function readSync() {
  if (typeof window === 'undefined') return false;
  return localStorage.getItem(SYNC_KEY) === 'true';
}

function readTheme() {
  if (typeof window === 'undefined') return 'light';
  if (readSync()) return getSystemDark() ? 'dark' : 'light';
  const stored = localStorage.getItem(THEME_KEY);
  if (stored && THEMES.some((t) => t.id === stored)) return stored;
  return getSystemDark() ? 'dark' : 'light';
}

function applyTheme(theme) {
  const el = document.documentElement;
  el.classList.remove('theme-deep-blue', 'theme-midnight');
  el.classList.toggle('dark', theme !== 'light');
  const cls = THEME_CLASSES[theme];
  if (cls) el.classList.add(cls);
}

function readAnimations() {
  if (typeof window === 'undefined') return true;
  const v = localStorage.getItem(ANIM_KEY);
  return v === null ? true : v === 'true';
}

function applyAnimations(on) {
  document.documentElement.classList.toggle('no-animations', !on);
}

function notify() {
  window.dispatchEvent(new CustomEvent('cf-theme-change'));
}

export function useTheme() {
  const [sync, setSync] = useState(readSync);
  const [theme, setThemeState] = useState(readTheme);
  const [animations, setAnimationsState] = useState(readAnimations);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  useEffect(() => {
    applyAnimations(animations);
  }, [animations]);

  // Stay in sync with changes made from other components.
  useEffect(() => {
    const onChange = () => {
      setSync(readSync());
      setThemeState(readTheme());
      setAnimationsState(readAnimations());
    };
    window.addEventListener('cf-theme-change', onChange);
    window.addEventListener('storage', onChange);
    return () => {
      window.removeEventListener('cf-theme-change', onChange);
      window.removeEventListener('storage', onChange);
    };
  }, []);

  // While syncing, follow the system preference live (light/dark only).
  useEffect(() => {
    if (!sync) return;
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const follow = () => setThemeState(mql.matches ? 'dark' : 'light');
    follow();
    const handler = (e) => setThemeState(e.matches ? 'dark' : 'light');
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, [sync]);

  const setTheme = (next) => {
    setSync(false);
    localStorage.setItem(SYNC_KEY, 'false');
    setThemeState(next);
    localStorage.setItem(THEME_KEY, next);
    notify();
  };

  const setSyncWithDevice = (checked) => {
    setSync(checked);
    localStorage.setItem(SYNC_KEY, checked ? 'true' : 'false');
    if (checked) {
      const next = getSystemDark() ? 'dark' : 'light';
      setThemeState(next);
      localStorage.setItem(THEME_KEY, next);
    }
    notify();
  };

  const setAnimations = (on) => {
    setAnimationsState(on);
    localStorage.setItem(ANIM_KEY, on ? 'true' : 'false');
    notify();
  };

  // Backward-compatible dark boolean + setter for any legacy callers.
  const dark = theme !== 'light';
  const setDarkMode = (checked) => setTheme(checked ? 'dark' : 'light');

  return { theme, setTheme, dark, sync, animations, setDarkMode, setSyncWithDevice, setAnimations };
}