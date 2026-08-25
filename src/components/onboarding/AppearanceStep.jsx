import React from 'react';
import { motion } from 'framer-motion';
import { useTheme } from '@/lib/theme';
import { Switch } from '@/components/ui/switch';
import { Moon, Sun, Smartphone, Sparkles } from 'lucide-react';

const container = { hidden: {}, show: { transition: { staggerChildren: 0.08 } } };
const item = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};

export default function AppearanceStep() {
  const { dark, sync, animations, setDarkMode, setSyncWithDevice, setAnimations } = useTheme();
  const rows = [
    {
      icon: dark ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />,
      title: 'Dark mode',
      desc: 'Switch between light and dark themes. Disabled while syncing with device.',
      checked: dark,
      onToggle: setDarkMode,
      disabled: sync,
    },
    {
      icon: <Smartphone className="h-4 w-4" />,
      title: 'Sync with device',
      desc: 'Match light or dark mode to your system setting.',
      checked: sync,
      onToggle: setSyncWithDevice,
      disabled: false,
    },
    {
      icon: <Sparkles className="h-4 w-4" />,
      title: 'Animations',
      desc: 'Show entrance and hover animations across the app.',
      checked: animations,
      onToggle: setAnimations,
      disabled: false,
    },
  ];
  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-3">
      <motion.p variants={item} className="text-sm text-muted-foreground">
        Choose how Study Spot looks. You can change this anytime in Settings.
      </motion.p>
      {rows.map((r) => (
        <motion.div
          key={r.title}
          variants={item}
          whileHover={{ scale: 1.01 }}
          className="rounded-lg border p-4 flex items-center justify-between gap-4 bg-card"
        >
          <div className="min-w-0">
            <p className="text-sm font-medium flex items-center gap-2">{r.icon} {r.title}</p>
            <p className="text-xs text-muted-foreground">{r.desc}</p>
          </div>
          <Switch checked={r.checked} onCheckedChange={r.onToggle} disabled={r.disabled} />
        </motion.div>
      ))}
    </motion.div>
  );
}