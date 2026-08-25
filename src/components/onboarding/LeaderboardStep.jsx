import React, { useState } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Switch } from '@/components/ui/switch';
import { Trophy } from 'lucide-react';

export default function LeaderboardStep() {
  const { user } = useAuth();
  const [enabled, setEnabled] = useState(
    (user?.leaderboard_enabled ?? user?.data?.leaderboard_enabled) === true
  );
  const [saving, setSaving] = useState(false);

  const toggle = async (v) => {
    setEnabled(v);
    setSaving(true);
    try {
      await base44.auth.updateMe({ leaderboard_enabled: v });
    } catch {
      setEnabled(!v);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">Compete with other students by showing the top study streaks on the Streaks page.</p>
      <div className="rounded-lg border p-4 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-medium flex items-center gap-2">
            <Trophy className="h-4 w-4 text-yellow-500" /> Study-streak leaderboard
          </p>
          <p className="text-xs text-muted-foreground">Off by default. Your streak is never shared unless you turn this on.</p>
        </div>
        <Switch checked={enabled} onCheckedChange={toggle} disabled={saving} />
      </div>
    </div>
  );
}