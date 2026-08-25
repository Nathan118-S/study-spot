import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Switch } from '@/components/ui/switch';
import { Trophy } from 'lucide-react';

const container = { hidden: {}, show: { transition: { staggerChildren: 0.1 } } };
const item = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};

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
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-3">
      <motion.p variants={item} className="text-sm text-muted-foreground">
        Compete with other students by showing the top study streaks on the Streaks page.
      </motion.p>
      <motion.div variants={item} whileHover={{ scale: 1.01 }} className="rounded-lg border p-4 flex items-center justify-between gap-4 bg-card">
        <div className="min-w-0">
          <p className="text-sm font-medium flex items-center gap-2">
            <motion.span
              animate={{ rotate: enabled ? [0, -12, 12, 0] : 0 }}
              transition={{ duration: 0.6, repeat: enabled ? Infinity : 0, repeatDelay: 1.5 }}
            >
              <Trophy className="h-4 w-4 text-yellow-500" />
            </motion.span>
            Study-streak leaderboard
          </p>
          <p className="text-xs text-muted-foreground">Off by default. Your streak is never shared unless you turn this on.</p>
        </div>
        <Switch checked={enabled} onCheckedChange={toggle} disabled={saving} />
      </motion.div>
    </motion.div>
  );
}