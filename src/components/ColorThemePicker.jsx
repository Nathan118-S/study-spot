import React, { useEffect, useState } from 'react';
import { Check } from 'lucide-react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { useTheme } from '@/lib/theme';
import { isDemoUser } from '@/lib/demoData';
import { cn } from '@/lib/utils';
import {
  COLOR_THEMES,
  applyColorTheme,
  readStoredColorTheme,
  themeSwatch,
} from '@/lib/colorThemes';

/** A tiny screen in the theme's colours: base, a card with an ink title, an accent button. */
function ThemePreview({ theme, dark }) {
  const c = themeSwatch(theme, dark);
  return (
    <div
      aria-hidden="true"
      className="h-16 w-full rounded-md p-2 flex flex-col gap-1.5 ring-1 ring-black/5 dark:ring-white/10"
      style={{ background: c.base }}
    >
      <div className="h-1.5 w-8 rounded-full" style={{ background: c.ink }} />
      <div className="flex-1 rounded flex items-center justify-between px-1.5" style={{ background: c.card }}>
        <div className="h-1 w-6 rounded-full opacity-60" style={{ background: c.ink }} />
        <div className="h-3 w-6 rounded-full" style={{ background: c.accent }} />
      </div>
    </div>
  );
}

/** Settings > Appearance > Theme. Applies at once and syncs through user.data.theme. */
export default function ColorThemePicker() {
  const { user } = useAuth();
  const { dark } = useTheme();
  const [current, setCurrent] = useState(readStoredColorTheme);

  useEffect(() => {
    const onChange = (e) => setCurrent(e.detail || readStoredColorTheme());
    window.addEventListener('cf-color-theme-change', onChange);
    return () => window.removeEventListener('cf-color-theme-change', onChange);
  }, []);

  const choose = (id) => {
    setCurrent(applyColorTheme(id));
    if (user && !isDemoUser(user)) api.auth.updateMe({ theme: id }).catch(() => {});
  };

  return (
    <div role="radiogroup" aria-label="Theme" className="grid grid-cols-2 sm:grid-cols-3 gap-2">
      {COLOR_THEMES.map((t) => {
        const selected = current === t.id;
        const accent = themeSwatch(t, dark).accent;
        return (
          <button
            key={t.id}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => choose(t.id)}
            className={cn(
              'rounded-lg border p-2 text-left transition-colors',
              selected ? 'border-primary bg-primary/10' : 'border-border hover:bg-accent'
            )}
          >
            <ThemePreview theme={t} dark={dark} />
            <span className="mt-2 flex items-center gap-1.5 text-sm font-medium">
              <span className="h-3 w-3 rounded-full shrink-0" style={{ background: accent }} />
              <span className="truncate">{t.name}</span>
              {selected && <Check className="h-4 w-4 ml-auto text-primary shrink-0" />}
            </span>
          </button>
        );
      })}
    </div>
  );
}
