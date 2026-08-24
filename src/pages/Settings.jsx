import React, { useState, useEffect, useCallback } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Calendar, BookOpen, Loader2, Link2, Unlink, RefreshCw, Trash2, LogOut, Trophy, Moon, Sun, Smartphone, Palette, Plug, RefreshCcw, Database, AlarmClock, GraduationCap, Lock, User, AlertTriangle, ChevronRight } from 'lucide-react';
import { useTheme } from '@/lib/theme';
import GradingScaleEditor from '@/components/GradingScaleEditor';
import TwoFactorSettings from '@/components/TwoFactorSettings';
import BlackboardConnection from '@/components/BlackboardConnection';
import ReminderSettings from '@/components/ReminderSettings';
import DataExport from '@/components/DataExport';
import AccountName from '@/components/AccountName';
import { Switch } from '@/components/ui/switch';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { isDemoUser, demoConnections } from '@/lib/demoData';
import {
  AlertDialog,
  AlertDialogTrigger,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
  AlertDialogAction,
} from '@/components/ui/alert-dialog';

const CALENDAR_ID = '6a87a0a86ad979ee05f39b0c';
const CLASSROOM_ID = '6a87a2e5f3be615b69035dcd';

const CARDS = [
  { value: 'appearance', label: 'Appearance', desc: 'Light/dark mode and device sync', icon: Palette },
  { value: 'google', label: 'Google', desc: 'Calendar and Classroom connections', icon: Plug },
  { value: 'blackboard', label: 'Blackboard', desc: 'Connect your school’s Blackboard', icon: BookOpen },
  { value: 'sync', label: 'Sync', desc: 'Refresh synced data', icon: RefreshCcw },
  { value: 'leaderboard', label: 'Leaderboard', desc: 'Show study-streak rankings', icon: Trophy },
  { value: 'data', label: 'Data', desc: 'Export your assignments', icon: Database },
  { value: 'reminders', label: 'Reminders', desc: 'Email reminder timing', icon: AlarmClock },
  { value: 'grading', label: 'Grading', desc: 'Letter-grade thresholds', icon: GraduationCap },
  { value: 'security', label: 'Security', desc: 'Two-factor authentication', icon: Lock },
  { value: 'account', label: 'Account', desc: 'Your name and email', icon: User },
  { value: 'danger', label: 'Danger Zone', desc: 'Log out all devices, delete account', icon: AlertTriangle, danger: true },
];

export default function Settings() {
  const { user, logout } = useAuth();
  const [status, setStatus] = useState({ calendar: false, classroom: false, blackboard: false });
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [syncCompletion, setSyncCompletion] = useState(user?.data?.sync_completion_to_classroom !== false);
  const [savingSync, setSavingSync] = useState(false);
  const [leaderboardEnabled, setLeaderboardEnabled] = useState(
    (user?.leaderboard_enabled ?? user?.data?.leaderboard_enabled) === true
  );
  const [savingLeaderboard, setSavingLeaderboard] = useState(false);
  const [openSection, setOpenSection] = useState(null);
  const { dark, sync: syncTheme, setDarkMode, setSyncWithDevice } = useTheme();

  const toggleLeaderboard = async (checked) => {
    setLeaderboardEnabled(checked);
    if (isDemoUser(user)) return;
    setSavingLeaderboard(true);
    try {
      await base44.auth.updateMe({ leaderboard_enabled: checked });
    } catch {
      setLeaderboardEnabled(!checked);
    } finally {
      setSavingLeaderboard(false);
    }
  };

  const toggleSyncCompletion = async (checked) => {
    setSyncCompletion(checked);
    setSavingSync(true);
    try {
      await base44.auth.updateMe({ sync_completion_to_classroom: checked });
    } catch {
      setSyncCompletion(!checked);
    } finally {
      setSavingSync(false);
    }
  };

  const check = useCallback(async () => {
    setLoading(true);
    if (isDemoUser(user)) {
      setStatus(demoConnections);
      setLoading(false);
      return;
    }
    try {
      const res = await base44.functions.invoke('checkGoogleConnections', {});
      setStatus(res.data || { calendar: false, classroom: false });
    } catch {
      setStatus({ calendar: false, classroom: false, blackboard: false });
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    check();
  }, [check]);

  const connect = async (id, label) => {
    if (isDemoUser(user)) return;
    setBusy(label);
    try {
      const url = await base44.connectors.connectAppUser(id);
      const popup = window.open(url, '_blank');
      const timer = setInterval(() => {
        if (!popup || popup.closed) {
          clearInterval(timer);
          check();
        }
      }, 500);
    } finally {
      setBusy(null);
    }
  };

  const disconnect = async (id, label) => {
    if (isDemoUser(user)) return;
    setBusy(label);
    try {
      await base44.connectors.disconnectAppUser(id);
      await check();
    } finally {
      setBusy(null);
    }
  };

  const deleteAccount = async () => {
    setDeleting(true);
    try {
      await base44.entities.Assignment.deleteMany({});
      await base44.entities.Class.deleteMany({});
      await base44.auth.logout();
    } catch {
      setDeleting(false);
    }
  };

  const logoutAllDevices = async () => {
    setLoggingOut(true);
    try {
      await base44.auth.updateMe({ sessions_invalidated_at: new Date().toISOString() });
      logout();
    } catch {
      setLoggingOut(false);
    }
  };

  const lastSync = user?.data?.last_sync ? new Date(user.data.last_sync) : null;
  const activeCard = CARDS.find((c) => c.value === openSection);

  const renderSection = (value) => {
    switch (value) {
      case 'appearance':
        return (
          <div className="space-y-3">
            <div className="rounded-lg border bg-card p-4 flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="text-sm font-medium flex items-center gap-2">
                  {dark ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}
                  Dark mode
                </p>
                <p className="text-xs text-muted-foreground">
                  Switch between light and dark themes. Disabled while syncing with your device.
                </p>
              </div>
              <Switch checked={dark} onCheckedChange={setDarkMode} disabled={syncTheme} />
            </div>
            <div className="rounded-lg border bg-card p-4 flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="text-sm font-medium flex items-center gap-2">
                  <Smartphone className="h-4 w-4" />
                  Sync with device
                </p>
                <p className="text-xs text-muted-foreground">
                  Automatically match light or dark mode to your system setting.
                </p>
              </div>
              <Switch checked={syncTheme} onCheckedChange={setSyncWithDevice} />
            </div>
          </div>
        );
      case 'google':
        return (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">
              Connect your own Google account to import assignments. Each user connects separately — your data stays private to you.
            </p>
            <ConnectionRow
              icon={<Calendar className="h-5 w-5 text-blue-500" />}
              title="Google Calendar"
              desc="Import calendar events labeled as assignments."
              loading={loading}
              connected={status.calendar}
              busy={busy === 'calendar'}
              onConnect={() => connect(CALENDAR_ID, 'calendar')}
              onDisconnect={() => disconnect(CALENDAR_ID, 'calendar')}
            />
            <ConnectionRow
              icon={<BookOpen className="h-5 w-5 text-emerald-500" />}
              title="Google Classroom"
              desc="Import courses and coursework with due dates."
              loading={loading}
              connected={status.classroom}
              busy={busy === 'classroom'}
              onConnect={() => connect(CLASSROOM_ID, 'classroom')}
              onDisconnect={() => disconnect(CLASSROOM_ID, 'classroom')}
              extra={status.classroom && !loading ? (
                <div className="border-t pt-3 flex items-center justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-sm font-medium">Mark Classroom assignments done</p>
                    <p className="text-xs text-muted-foreground">
                      When you complete a Google Classroom assignment here, turn it in on Classroom so your teacher sees it as done.
                    </p>
                  </div>
                  <Switch checked={syncCompletion} onCheckedChange={toggleSyncCompletion} disabled={savingSync} />
                </div>
              ) : null}
            />
          </div>
        );
      case 'blackboard':
        return (
          <BlackboardConnection
            connected={status.blackboard}
            instanceUrl={user?.data?.blackboard_instance_url}
            onChanged={check}
          />
        );
      case 'sync':
        return (
          <div className="rounded-lg border bg-card p-4 flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">Auto-refresh</p>
              <p className="text-xs text-muted-foreground">
                {lastSync ? `Last synced ${lastSync.toLocaleString()}` : 'Not synced yet. The dashboard auto-syncs every 6 hours.'}
              </p>
            </div>
            <Button variant="outline" onClick={check} disabled={loading}>
              <RefreshCw className="h-4 w-4 mr-2" /> Refresh status
            </Button>
          </div>
        );
      case 'leaderboard':
        return (
          <div className="rounded-lg border bg-card p-4 flex items-center justify-between gap-4">
            <div className="min-w-0">
              <p className="text-sm font-medium flex items-center gap-2">
                <Trophy className="h-4 w-4 text-yellow-500" />
                Show study-streak leaderboard
              </p>
              <p className="text-xs text-muted-foreground">
                Display the top study streaks on the Analytics page so you can compete with other students. Off by default.
              </p>
            </div>
            <Switch checked={leaderboardEnabled} onCheckedChange={toggleLeaderboard} disabled={savingLeaderboard} />
          </div>
        );
      case 'data':
        return <DataExport />;
      case 'reminders':
        return (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">
              Control when you get email notifications about upcoming assignments.
            </p>
            <ReminderSettings />
          </div>
        );
      case 'grading':
        return (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">
              Set the minimum percentage for each letter grade. This is used to compute letter grades on graded assignments.
            </p>
            <GradingScaleEditor initialScale={user?.data?.grading_scale} />
          </div>
        );
      case 'security':
        return <TwoFactorSettings />;
      case 'account':
        return <AccountName />;
      case 'danger':
        return (
          <div className="space-y-3">
            <div className="rounded-lg border border-destructive/40 bg-card p-4 flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="text-sm font-medium">Log out of all devices</p>
                <p className="text-xs text-muted-foreground">
                  Signs out this device and any other devices currently logged into your account.
                </p>
              </div>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="outline" size="sm" disabled={loggingOut}>
                    {loggingOut ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <LogOut className="h-4 w-4 mr-1" />}
                    Log out all
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Log out of all devices?</AlertDialogTitle>
                    <AlertDialogDescription>
                      You'll be signed out here, and any other device signed into your account will be signed out the next time it opens the app.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel disabled={loggingOut}>Cancel</AlertDialogCancel>
                    <AlertDialogAction onClick={logoutAllDevices} disabled={loggingOut}>
                      {loggingOut ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
                      Log out all
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>
            <div className="rounded-lg border border-destructive/40 bg-card p-4 flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="text-sm font-medium">Delete account</p>
                <p className="text-xs text-muted-foreground">
                  Removes your assignments and classes, then signs you out. This can't be undone.
                </p>
              </div>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="destructive" size="sm" disabled={deleting}>
                    {deleting ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Trash2 className="h-4 w-4 mr-1" />}
                    Delete
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Delete account?</AlertDialogTitle>
                    <AlertDialogDescription>
                      This permanently deletes all your assignments and classes and signs you out. This action cannot be undone.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel disabled={deleting}>Cancel</AlertDialogCancel>
                    <AlertDialogAction
                      className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                      onClick={deleteAccount}
                      disabled={deleting}
                    >
                      {deleting ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
                      Delete
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="font-heading text-2xl md:text-3xl font-bold">Settings</h1>
        <p className="text-muted-foreground text-sm">Manage your Google connections and account.</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {CARDS.map((c) => (
          <button
            key={c.value}
            onClick={() => setOpenSection(c.value)}
            className={`w-full text-left rounded-lg border bg-card p-4 hover:bg-accent transition-colors flex items-center justify-between gap-3 ${
              c.danger ? 'border-destructive/40' : ''
            }`}
          >
            <div className="flex items-center gap-3 min-w-0">
              <div
                className={`h-10 w-10 rounded-full flex items-center justify-center shrink-0 ${
                  c.danger ? 'bg-destructive/10 text-destructive' : 'bg-primary/10 text-primary'
                }`}
              >
                <c.icon className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <p className={`font-medium ${c.danger ? 'text-destructive' : ''}`}>{c.label}</p>
                <p className="text-sm text-muted-foreground truncate">{c.desc}</p>
              </div>
            </div>
            <ChevronRight className="h-5 w-5 text-muted-foreground shrink-0" />
          </button>
        ))}
      </div>

      <Dialog open={!!openSection} onOpenChange={(o) => !o && setOpenSection(null)}>
        <DialogContent className="sm:max-w-lg max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className={activeCard?.danger ? 'text-destructive' : ''}>
              {activeCard?.label}
            </DialogTitle>
            <DialogDescription>{activeCard?.desc}</DialogDescription>
          </DialogHeader>
          {renderSection(openSection)}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function ConnectionRow({ icon, title, desc, loading, connected, busy, onConnect, onDisconnect, extra }) {
  return (
    <div className="rounded-lg border bg-card p-4 flex flex-col gap-4">
      <div className="flex items-start gap-3 min-w-0">
        <div className="mt-0.5">{icon}</div>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="font-medium">{title}</p>
            {loading ? (
              <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />
            ) : connected ? (
              <Badge className="bg-emerald-500 hover:bg-emerald-500">Connected</Badge>
            ) : (
              <Badge variant="secondary">Not connected</Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">{desc}</p>
        </div>
      </div>
      <div className="shrink-0">
        {connected ? (
          <Button variant="outline" size="sm" onClick={onDisconnect} disabled={busy}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Unlink className="h-4 w-4 mr-1" />}
            Disconnect
          </Button>
        ) : (
          <Button size="sm" onClick={onConnect} disabled={busy}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Link2 className="h-4 w-4 mr-1" />}
            Connect
          </Button>
        )}
      </div>
      {extra}
    </div>
  );
}