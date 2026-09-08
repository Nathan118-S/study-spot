import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { useTheme, ACCENTS } from '@/lib/theme';
import { cn } from '@/lib/utils';
import { Check } from 'lucide-react';

export function AccentPicker() {
  const { accent, setAccent } = useTheme();
  return (
    <div className="flex flex-wrap gap-2">
      {Object.entries(ACCENTS).map(([key, a]) => (
        <motion.button
          key={key}
          type="button"
          whileHover={{ scale: 1.1 }}
          whileTap={{ scale: 0.95 }}
          onClick={() => setAccent(key)}
          className={cn(
            'h-8 w-8 rounded-full flex items-center justify-center transition-shadow',
            accent === key ? 'ring-2 ring-offset-2 ring-offset-card ring-foreground/40' : ''
          )}
          style={{ background: `hsl(${a.value})` }}
          title={a.label}
          aria-label={a.label}
        >
          {accent === key && <Check className="h-4 w-4 text-white drop-shadow" />}
        </motion.button>
      ))}
    </div>
  );
}

const WELCOME_OPTIONS = [
  { value: 'large', label: 'Large popup', desc: 'Full-screen welcome each login.' },
  { value: 'small', label: 'Small popup', desc: 'A compact greeting card.' },
  { value: 'off', label: 'Off', desc: 'Skip the welcome message.' },
];

export function WelcomeStyleSelect() {
  const { user } = useAuth();
  const [value, setValue] = useState(
    user?.welcome_style ?? user?.data?.welcome_style ?? 'large'
  );
  const [saving, setSaving] = useState(false);

  const choose = async (v) => {
    setValue(v);
    setSaving(true);
    try {
      await api.auth.updateMe({ welcome_style: v });
    } catch {
      setValue((prev) => prev);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="grid grid-cols-3 gap-2">
      {WELCOME_OPTIONS.map((o) => (
        <button
          key={o.value}
          type="button"
          onClick={() => choose(o.value)}
          disabled={saving}
          className={cn(
            'rounded-lg border p-3 text-left transition-colors',
            value === o.value ? 'border-primary bg-primary/10' : 'border-border hover:bg-accent'
          )}
        >
          <p className="text-sm font-medium">{o.label}</p>
          <p className="text-xs text-muted-foreground">{o.desc}</p>
        </button>
      ))}
    </div>
  );
}