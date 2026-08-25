import React, { useState } from 'react';
import { useAuth } from '@/lib/AuthContext';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { motion, AnimatePresence } from 'framer-motion';
import { Loader2, GraduationCap, Palette, Trophy, ShieldCheck, Plug, ClipboardCheck, Check, ArrowLeft, ArrowRight, Sparkles } from 'lucide-react';
import { cn } from '@/lib/utils';
import AppearanceStep from './onboarding/AppearanceStep';
import LeaderboardStep from './onboarding/LeaderboardStep';
import ServicesStep from './onboarding/ServicesStep';
import TwoFactorSettings from '@/components/TwoFactorSettings';
import GradingScaleEditor from '@/components/GradingScaleEditor';

const STEPS = [
  { key: 'welcome', title: 'Welcome', icon: GraduationCap },
  { key: 'appearance', title: 'Appearance', icon: Palette },
  { key: 'leaderboard', title: 'Leaderboard', icon: Trophy },
  { key: 'security', title: 'Security', icon: ShieldCheck },
  { key: 'services', title: 'Services', icon: Plug },
  { key: 'grading', title: 'Grading', icon: ClipboardCheck },
];

const container = {
  hidden: {},
  show: { transition: { staggerChildren: 0.07, delayChildren: 0.05 } },
};
const item = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};

export default function Onboarding() {
  const { user, checkUserAuth } = useAuth();
  const [step, setStep] = useState(0);
  const [finishing, setFinishing] = useState(false);

  const finish = async () => {
    setFinishing(true);
    try {
      await base44.auth.updateMe({ onboarding_completed: true });
      await checkUserAuth();
    } catch {
      setFinishing(false);
    }
  };

  const next = () => setStep((s) => Math.min(s + 1, STEPS.length - 1));
  const back = () => setStep((s) => Math.max(s - 1, 0));
  const isLast = step === STEPS.length - 1;
  const current = STEPS[step];

  const renderStep = () => {
    switch (current.key) {
      case 'welcome':
        return (
          <motion.div
            key="welcome"
            variants={container}
            initial="hidden"
            animate="show"
            className="text-center py-6"
          >
            <motion.div variants={item} className="mx-auto h-20 w-20 rounded-full bg-primary/15 flex items-center justify-center mb-5">
              <motion.div
                animate={{ y: [0, -8, 0], rotate: [0, -6, 6, 0] }}
                transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
              >
                <GraduationCap className="h-10 w-10 text-primary" />
              </motion.div>
            </motion.div>
            <motion.h2 variants={item} className="font-heading text-2xl font-bold">
              Welcome to <span className="text-primary">Study Spot</span>
            </motion.h2>
            <motion.p variants={item} className="text-sm text-muted-foreground mt-3 max-w-sm mx-auto">
              Let's set up your account in a few quick steps. You can change any of these later in Settings.
            </motion.p>
            <motion.div variants={item} className="flex items-center justify-center gap-1.5 mt-5 text-primary">
              {[0, 1, 2].map((i) => (
                <motion.span
                  key={i}
                  className="h-1.5 w-1.5 rounded-full bg-primary"
                  animate={{ opacity: [0.3, 1, 0.3], scale: [0.8, 1.2, 0.8] }}
                  transition={{ duration: 1.2, repeat: Infinity, delay: i * 0.2 }}
                />
              ))}
            </motion.div>
          </motion.div>
        );
      case 'appearance':
        return <AppearanceStep />;
      case 'leaderboard':
        return <LeaderboardStep />;
      case 'security':
        return <TwoFactorSettings />;
      case 'services':
        return <ServicesStep />;
      case 'grading':
        return <GradingScaleEditor initialScale={user?.data?.grading_scale} />;
      default:
        return null;
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto bg-background"
    >
      <motion.div
        initial={{ opacity: 0, y: 24, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="relative w-full max-w-lg my-auto flex flex-col rounded-2xl border border-amber-300 dark:border-amber-500/30 bg-gradient-to-br from-amber-50 to-amber-100 dark:from-amber-900/40 dark:to-amber-950/50 shadow-2xl"
      >
        <div className="p-5 border-b border-border">
          <div className="flex items-center justify-between gap-3">
            <motion.div
              key={current.key}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.3 }}
              className="flex items-center gap-2 min-w-0"
            >
              <motion.div
                animate={{ rotate: [0, -8, 8, 0] }}
                transition={{ duration: 2, repeat: Infinity, ease: 'easeInOut' }}
              >
                <current.icon className="h-5 w-5 text-primary shrink-0" />
              </motion.div>
              <h2 className="font-heading font-bold truncate">{current.title}</h2>
            </motion.div>
            <Button variant="ghost" size="sm" onClick={finish} disabled={finishing} className="text-muted-foreground">
              {finishing ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
              Skip setup
            </Button>
          </div>
          <div className="flex gap-1.5 mt-3">
            {STEPS.map((s, i) => (
              <motion.div
                key={s.key}
                className={cn('h-1.5 flex-1 rounded-full', i <= step ? 'bg-primary' : 'bg-muted')}
                animate={i === step ? { scaleX: [1, 1.15, 1] } : {}}
                transition={{ duration: 0.6 }}
                style={{ transformOrigin: 'left' }}
              />
            ))}
          </div>
        </div>

        <div className="p-5 overflow-y-auto flex-1 min-h-[280px]">
          <AnimatePresence mode="wait">
            <motion.div
              key={current.key}
              initial={{ opacity: 0, x: 30 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -30 }}
              transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
            >
              {renderStep()}
            </motion.div>
          </AnimatePresence>
        </div>

        <div className="p-5 border-t border-border flex items-center justify-between gap-3">
          <Button variant="ghost" onClick={back} disabled={step === 0 || finishing}>
            <ArrowLeft className="h-4 w-4 mr-1" /> Back
          </Button>
          {isLast ? (
            <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
              <Button onClick={finish} disabled={finishing} className="bg-gradient-to-r from-primary to-amber-500">
                {finishing ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Check className="h-4 w-4 mr-1" />}
                Finish
              </Button>
            </motion.div>
          ) : (
            <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
              <Button onClick={next} disabled={finishing} className="bg-gradient-to-r from-primary to-amber-500">
                {step === 0 ? (
                  <>
                    Get started <Sparkles className="h-4 w-4 ml-1" />
                  </>
                ) : (
                  <>
                    Next <ArrowRight className="h-4 w-4 ml-1" />
                  </>
                )}
              </Button>
            </motion.div>
          )}
        </div>
      </motion.div>
    </motion.div>
  );
}