import { useState, useEffect } from 'react';

const THEME_KEY = 'cf-theme';
const SYNC_KEY = 'cf-theme-sync';
const ANIM_KEY = 'cf-animations';
const ACCENT_KEY = 'cf-accent';

export const ACCENTS = {
  orange: { label: 'Orange', value: '33 87% 58%' },
  blue: { label: 'Blue', value: '221 83% 53%' },
  purple: { label: 'Purple', value: '262 83% 58%' },
  green: { label: 'Green', value: '142 71% 45%' },
  rose: { label: 'Rose', value: '347 77% 55%' },
};

export function getSystemDark() {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function readSync() {
  if (typeof window === 'undefined') return false;
  return localStorage.getItem(SYNC_KEY) === 'true';
}

function readDark() {
  if (typeof window === 'undefined') return false;
  if (readSync()) return getSystemDark();
  const stored = localStorage.getItem(THEME_KEY);
  if (stored) return stored === 'dark';
  return getSystemDark();
}

function applyDark(dark) {
  document.documentElement.classList.toggle('dark', dark);
}

function readAnimations() {
  if (typeof window === 'undefined') return true;
  const v = localStorage.getItem(ANIM_KEY);
  return v === null ? true : v === 'true';
}

function applyAnimations(on) {
  document.documentElement.classList.toggle('no-animations', !on);
}

function readAccent() {
  if (typeof window === 'undefined') return 'orange';
  return localStorage.getItem(ACCENT_KEY) || 'orange';
}

function applyAccent(name) {
  const v = (ACCENTS[name] || ACCENTS.orange).value;
  document.documentElement.style.setProperty('--primary', v);
  document.documentElement.style.setProperty('--ring', v);
  document.documentElement.style.setProperty('--sidebar-primary', v);
}

function persist(sync, dark) {
  localStorage.setItem(SYNC_KEY, sync ? 'true' : 'false');
  if (!sync) localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light');
  window.dispatchEvent(new CustomEvent('cf-theme-change'));
}

export function useTheme() {
  const [sync, setSync] = useState(readSync);
  const [dark, setDark] = useState(readDark);
  const [animations, setAnimationsState] = useState(readAnimations);
  const [accent, setAccentState] = useState(readAccent);

  useEffect(() => {
    applyDark(dark);
  }, [dark]);

  useEffect(() => {
    applyAnimations(animations);
  }, [animations]);

  useEffect(() => {
    applyAccent(accent);
  }, [accent]);

  // Stay in sync with changes made from the other component.
  useEffect(() => {
    const onChange = () => {
      setSync(readSync());
      setDark(readDark());
      setAnimationsState(readAnimations());
      setAccentState(readAccent());
    };
    window.addEventListener('cf-theme-change', onChange);
    window.addEventListener('storage', onChange);
    return () => {
      window.removeEventListener('cf-theme-change', onChange);
      window.removeEventListener('storage', onChange);
    };
  }, []);

  // While syncing, follow the system preference live.
  useEffect(() => {
    if (!sync) return;
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    setDark(mql.matches);
    const handler = (e) => setDark(e.matches);
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, [sync]);

  // Manually pick a mode; this turns off device sync.
  const setDarkMode = (checked) => {
    setSync(false);
    setDark(checked);
    persist(false, checked);
  };

  const setSyncWithDevice = (checked) => {
    setSync(checked);
    if (checked) {
      const sys = getSystemDark();
      setDark(sys);
      persist(true, sys);
    } else {
      persist(false, dark);
    }
  };

  const setAnimations = (on) => {
    setAnimationsState(on);
    localStorage.setItem(ANIM_KEY, on ? 'true' : 'false');
  };

  const setAccent = (name) => {
    setAccentState(name);
    localStorage.setItem(ACCENT_KEY, name);
    window.dispatchEvent(new CustomEvent('cf-theme-change'));
  };

  return { dark, sync, animations, accent, setDarkMode, setSyncWithDevice, setAnimations, setAccent };
}