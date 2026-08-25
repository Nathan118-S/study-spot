import React, { useState } from 'react';
import { useAuth } from '@/lib/AuthContext';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Loader2, GraduationCap, Palette, Trophy, ShieldCheck, Plug, ClipboardCheck, Check, ArrowLeft, ArrowRight } from 'lucide-react';
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
          <div className="text-center py-6">
            <div className="mx-auto h-14 w-14 rounded-full bg-primary/10 flex items-center justify-center mb-4">
              <GraduationCap className="h-7 w-7 text-primary" />
            </div>
            <h2 className="font-heading text-xl font-bold">Welcome to Study Spot</h2>
            <p className="text-sm text-muted-foreground mt-2 max-w-sm mx-auto">
              Let's set up your account in a few quick steps. You can change any of these later in Settings.
            </p>
          </div>
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
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-lg max-h-[90vh] flex flex-col rounded-xl border bg-card shadow-xl">
        <div className="p-5 border-b">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2 min-w-0">
              <current.icon className="h-5 w-5 text-primary shrink-0" />
              <h2 className="font-heading font-bold truncate">{current.title}</h2>
            </div>
            <Button variant="ghost" size="sm" onClick={finish} disabled={finishing} className="text-muted-foreground">
              {finishing ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
              Skip setup
            </Button>
          </div>
          <div className="flex gap-1.5 mt-3">
            {STEPS.map((s, i) => (
              <div
                key={s.key}
                className={cn('h-1.5 flex-1 rounded-full transition-colors', i <= step ? 'bg-primary' : 'bg-muted')}
              />
            ))}
          </div>
        </div>
        <div className="p-5 overflow-y-auto flex-1">{renderStep()}</div>
        <div className="p-5 border-t flex items-center justify-between gap-3">
          <Button variant="ghost" onClick={back} disabled={step === 0 || finishing}>
            <ArrowLeft className="h-4 w-4 mr-1" /> Back
          </Button>
          {isLast ? (
            <Button onClick={finish} disabled={finishing}>
              {finishing ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Check className="h-4 w-4 mr-1" />}
              Finish
            </Button>
          ) : (
            <Button onClick={next} disabled={finishing}>
              {step === 0 ? 'Get started' : 'Next'}
              <ArrowRight className="h-4 w-4 ml-1" />
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}