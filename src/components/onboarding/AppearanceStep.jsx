import React from 'react';
import { useTheme } from '@/lib/theme';
import { Switch } from '@/components/ui/switch';
import { Moon, Sun, Smartphone, Sparkles } from 'lucide-react';

export default function AppearanceStep() {
  const { dark, sync, animations, setDarkMode, setSyncWithDevice, setAnimations } = useTheme();
  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">Choose how Study Spot looks. You can change this anytime in Settings.</p>
      <div className="rounded-lg border p-4 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-medium flex items-center gap-2">
            {dark ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />} Dark mode
          </p>
          <p className="text-xs text-muted-foreground">Switch between light and dark themes. Disabled while syncing with device.</p>
        </div>
        <Switch checked={dark} onCheckedChange={setDarkMode} disabled={sync} />
      </div>
      <div className="rounded-lg border p-4 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-medium flex items-center gap-2">
            <Smartphone className="h-4 w-4" /> Sync with device
          </p>
          <p className="text-xs text-muted-foreground">Match light or dark mode to your system setting.</p>
        </div>
        <Switch checked={sync} onCheckedChange={setSyncWithDevice} />
      </div>
      <div className="rounded-lg border p-4 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-medium flex items-center gap-2">
            <Sparkles className="h-4 w-4" /> Animations
          </p>
          <p className="text-xs text-muted-foreground">Show entrance and hover animations across the app.</p>
        </div>
        <Switch checked={animations} onCheckedChange={setAnimations} />
      </div>
    </div>
  );
}