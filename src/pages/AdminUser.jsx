import React, { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { useToast } from '@/components/ui/use-toast';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/components/ui/dialog';
import SheetSelect from '@/components/SheetSelect';
import MergeDialog from '@/components/admin/MergeDialog';
import AdminUserSettings from '@/components/admin/AdminUserSettings';
import AdminUserRecords from '@/components/admin/AdminUserRecords';
import { ElevationStatus } from '@/components/admin/AdminElevation';
import { Section, Row, ConfirmButton, fmtDate, fmtDay } from '@/components/admin/AdminUserParts';
import { METHOD_LABEL } from '@/lib/twofa';
import { cn } from '@/lib/utils';
import {
  ArrowLeft, Loader2, Lock, Check, Copy, KeyRound, LogOut, ShieldOff, Smartphone, Fingerprint, Trash2,
  Calendar, BookOpen, GraduationCap, Unlink, Bell, Flame, RotateCcw, GitMerge, Ban, RefreshCw,
} from 'lucide-react';

const ROLE_OPTIONS = [
  { value: 'user', label: 'User' },
  { value: 'admin', label: 'Admin' },
  { value: 'demo', label: 'Demo' },
  { value: 'disabled', label: 'Disabled' },
];

const CONNECTORS = [
  { id: 'google_calendar', label: 'Google Calendar', icon: Calendar },
  { id: 'google_classroom', label: 'Google Classroom', icon: GraduationCap },
];

const DEVICE_KIND = { alert: 'Push notifications', 'live-activity-start': 'Live Activities' };

function TempPasswordDialog({ value, onClose }) {
  const [copied, setCopied] = useState(false);
  const { toast } = useToast();
  useEffect(() => setCopied(false), [value]);
  return (
    <Dialog open={!!value} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Temporary password</DialogTitle>
          <DialogDescription>
            Give this to @{value?.username}. They've been signed out everywhere and can change it in
            Settings → Account after logging in. It won't be shown again.
          </DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-2">
          <code className="flex-1 rounded-lg border bg-muted/50 px-3 py-2.5 font-mono text-base tracking-wide select-all break-all">
            {value?.password}
          </code>
          <Button
            variant="outline"
            size="icon"
            aria-label="Copy password"
            title="Copy password"
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(value?.password || '');
                setCopied(true);
              } catch {
                toast({ title: 'Copy failed', description: 'Select the password and copy it manually.', variant: 'destructive' });
              }
            }}
          >
            {copied ? <Check className="h-4 w-4 text-success" /> : <Copy className="h-4 w-4" />}
          </Button>
        </div>
        <DialogFooter>
          <Button onClick={onClose}>Done</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function SetPasswordDialog({ open, onOpenChange, onSubmit }) {
  const [pw, setPw] = useState('');
  const [signOut, setSignOut] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) {
      setPw('');
      setSignOut(true);
      setError('');
    }
  }, [open]);
  const submit = async (e) => {
    e.preventDefault();
    if (pw.length < 8) return setError('Passwords must be at least 8 characters');
    setBusy(true);
    setError('');
    try {
      await onSubmit(pw, signOut);
      onOpenChange(false);
    } catch (err) {
      if (!err?.elevationCancelled) setError(err.message || 'Could not set the password');
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Set password</DialogTitle>
            <DialogDescription>Choose a new password for this account (at least 8 characters).</DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="admin-set-pw" className="text-xs text-muted-foreground">New password</Label>
            <Input id="admin-set-pw" type="text" autoComplete="off" value={pw} onChange={(e) => setPw(e.target.value)} autoFocus />
          </div>
          <div className="flex items-center justify-between gap-3">
            <Label htmlFor="admin-set-pw-signout">Sign them out everywhere</Label>
            <Switch id="admin-set-pw-signout" checked={signOut} onCheckedChange={setSignOut} />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
            <Button type="submit" disabled={busy || !pw}>
              {busy && <Loader2 className="h-4 w-4 mr-1 animate-spin" />}
              Set password
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function DisableDialog({ open, onOpenChange, username, onSubmit }) {
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (open) setReason('');
  }, [open]);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Disable @{username}?</DialogTitle>
          <DialogDescription>
            They won't be able to log in. The reason below is shown on their next login attempt.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5">
          <Label htmlFor="disable-reason">Reason (optional)</Label>
          <Textarea
            id="disable-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Violation of community guidelines"
            maxLength={500}
            rows={3}
          />
        </div>
        <DialogFooter className="gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button
            variant="destructive"
            onClick={() => {
              onOpenChange(false);
              onSubmit(reason);
            }}
          >
            <Ban className="h-4 w-4 mr-1" /> Disable account
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ProfileSection({ u, onSave, busy }) {
  const [form, setForm] = useState({ username: '', name: '', full_name: '' });
  const [error, setError] = useState('');
  useEffect(() => {
    setForm({ username: u.username || '', name: u.name || '', full_name: u.full_name || '' });
    setError('');
  }, [u.username, u.name, u.full_name]);

  const changes = {};
  if (form.username.trim().toLowerCase() !== (u.username || '').toLowerCase()) changes.username = form.username.trim();
  if (form.name.trim() !== (u.name || '')) changes.name = form.name.trim();
  if (form.full_name.trim() !== (u.full_name || '')) changes.full_name = form.full_name.trim();
  const dirty = Object.keys(changes).length > 0;

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await onSave(changes);
    } catch (err) {
      if (!err?.elevationCancelled) setError(err.message || 'Could not save');
    }
  };

  const field = (key, label, extra = {}) => (
    <div className="px-4 py-2.5 flex items-center gap-3">
      <Label htmlFor={`profile-${key}`} className="w-24 shrink-0 text-[15px] font-normal">{label}</Label>
      <Input
        id={`profile-${key}`}
        value={form[key]}
        onChange={(e) => {
          setError('');
          setForm((f) => ({ ...f, [key]: key === 'username' ? e.target.value.toLowerCase() : e.target.value }));
        }}
        className="flex-1 min-w-0 h-9"
        maxLength={key === 'username' ? 254 : 80}
        {...extra}
      />
    </div>
  );

  return (
    <form onSubmit={submit}>
      <Section
        title="Profile"
        footer={error ? <span className="text-destructive">{error}</span> : `Joined ${fmtDay(u.created_date)} · Last updated ${fmtDate(u.updated_date)}`}
        action={
          <Button type="submit" size="sm" className="h-7" disabled={!dirty || busy}>
            {busy ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : <Check className="h-3.5 w-3.5 mr-1" />}
            Save
          </Button>
        }
      >
        {field('username', 'Username', { autoCapitalize: 'none', autoCorrect: 'off', spellCheck: false, autoComplete: 'off' })}
        {field('name', 'Name', { placeholder: 'Display name' })}
        {field('full_name', 'Full name', { placeholder: 'Full name' })}
      </Section>
    </form>
  );
}

export default function AdminUser() {
  const { userId } = useParams();
  const { user: me, isLoadingAuth } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [locked, setLocked] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [busy, setBusy] = useState(null);
  const [tempPassword, setTempPassword] = useState(null);
  const [setPwOpen, setSetPwOpen] = useState(false);
  const [disableOpen, setDisableOpen] = useState(false);
  const [mergeUsers, setMergeUsers] = useState(null);

  const load = useCallback(async ({ quiet } = {}) => {
    if (!quiet) setLoading(true);
    setLoadError('');
    try {
      const res = await api.functions.invoke('adminGetUser', { userId });
      setData(res.data);
      setLocked(false);
    } catch (e) {
      if (e?.elevationCancelled) setLocked(true);
      else setLoadError(e.message || 'Failed to load this account');
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    if (me?.role === 'admin') load();
  }, [me?.role, load]);

  // Re-lock the page if the elevation is cleared or expires.
  useEffect(
    () =>
      api.elevation.subscribe((el, reason) => {
        // 'retry' means a prompt is about to show and the call will be retried.
        if (!el && reason !== 'retry') {
          setData(null);
          setLocked(true);
        }
      }),
    []
  );

  if (isLoadingAuth) {
    return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  }
  if (!me || me.role !== 'admin') return <Navigate to="/" replace />;

  // Runs an admin function for this user, shows a toast and refreshes.
  const act = async (key, name, payload, success, { reload = true } = {}) => {
    setBusy(key);
    try {
      const res = await api.functions.invoke(name, { userId, ...payload });
      if (success) toast(typeof success === 'function' ? success(res.data) : { title: success });
      if (reload) await load({ quiet: true });
      return res.data;
    } catch (e) {
      if (!e?.elevationCancelled) toast({ title: 'Failed', description: e.message, variant: 'destructive' });
      return null;
    } finally {
      setBusy(null);
    }
  };

  const header = (
    <div className="flex items-start gap-3">
      <Button variant="ghost" size="icon" onClick={() => navigate('/admin/people')} title="Back to People" className="shrink-0">
        <ArrowLeft className="h-5 w-5" />
      </Button>
      <div className="min-w-0 flex-1">
        <h1 className="font-heading text-2xl md:text-3xl font-bold truncate">
          {data ? data.user.name || data.user.full_name || data.user.username : 'Account'}
        </h1>
        {data && (
          <div className="flex flex-wrap items-center gap-2 mt-0.5">
            <span className="text-muted-foreground text-sm truncate">@{data.user.username}</span>
            <Badge
              variant={data.user.role === 'admin' ? 'default' : 'secondary'}
              className={cn(
                'capitalize',
                data.user.role === 'demo' && 'border-transparent bg-emerald-500 text-white hover:bg-emerald-500/90',
                data.user.role === 'disabled' && 'border-transparent bg-red-500 text-white hover:bg-red-500/90'
              )}
            >
              {data.user.role}
            </Badge>
          </div>
        )}
        <ElevationStatus className="mt-2" />
      </div>
      {data && (
        <Button variant="outline" size="icon" onClick={() => load({ quiet: true })} title="Refresh" className="shrink-0">
          <RefreshCw className="h-4 w-4" />
        </Button>
      )}
    </div>
  );

  if (loading && !data) {
    return (
      <div className="space-y-6 max-w-2xl">
        {header}
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="space-y-6 max-w-2xl">
        {header}
        <div className="rounded-xl bg-card p-6 text-center space-y-3">
          <Lock className="h-8 w-8 mx-auto text-muted-foreground" />
          <p className="font-medium">{locked ? 'Confirm with two-factor authentication to open this account.' : loadError}</p>
          <Button onClick={() => load()}>{locked ? 'Unlock' : 'Try again'}</Button>
        </div>
      </div>
    );
  }

  const { user: u, stats, connections, devices } = data;
  const isSelf = u.id === me.id;
  const methods = u.twofa_methods || [];
  const google = new Map((connections?.google || []).map((c) => [c.connector, c]));

  const saveSettings = async (settings) => {
    try {
      const res = await api.functions.invoke('adminUpdateUserSettings', { userId, settings });
      setData((d) => ({ ...d, user: { ...d.user, ...res.data.user } }));
      toast({ title: 'Setting saved' });
    } catch (e) {
      if (!e?.elevationCancelled) toast({ title: 'Failed to save', description: e.message, variant: 'destructive' });
    }
  };

  const setRole = (role) => {
    if (role === u.role) return;
    if (role === 'disabled') return setDisableOpen(true);
    act('role', 'adminUpdateUserRole', { role }, `@${u.username} is now ${role}`);
  };

  const statTiles = [
    ['Assignments', stats.assignments],
    ['Completed', stats.completed],
    ['Overdue', stats.overdue],
    ['Classes', stats.classes],
    ['Templates', stats.templates],
    ['Study sessions', stats.study_sessions],
    ['Focus minutes', stats.study_minutes],
    ['Notifications', `${stats.notifications}${stats.unread_notifications ? ` (${stats.unread_notifications} unread)` : ''}`],
  ];

  return (
    <div className="space-y-6 max-w-2xl">
      {header}

      <ProfileSection
        u={u}
        busy={busy === 'profile'}
        onSave={async (changes) => {
          setBusy('profile');
          try {
            const res = await api.functions.invoke('adminUpdateUser', { userId, ...changes });
            setData((d) => ({ ...d, user: { ...d.user, ...res.data.user } }));
            toast({ title: 'Profile saved' });
          } finally {
            setBusy(null);
          }
        }}
      />

      <Section
        title="Status"
        footer={u.role === 'disabled' ? (u.disabled_reason ? `Disabled: ${u.disabled_reason}` : 'This account cannot log in.') : isSelf ? "You can't change your own role." : undefined}
      >
        <Row label="Role" desc="Admins can manage everyone; disabled accounts can't log in.">
          <SheetSelect
            value={u.role}
            onValueChange={setRole}
            triggerClassName={cn('w-[130px]', isSelf && 'pointer-events-none opacity-50')}
            title="Role"
            options={ROLE_OPTIONS}
          />
        </Row>
      </Section>

      <Section title="Security">
        {methods.length === 0 ? (
          <Row icon={ShieldOff} label="Two-factor authentication" desc="Not turned on." />
        ) : (
          methods.map((m) => (
            <Row
              key={m}
              icon={m === 'passkey' ? Fingerprint : Smartphone}
              label={METHOD_LABEL[m] || m}
              desc="Two-factor method"
            >
              <ConfirmButton
                title={`Remove ${METHOD_LABEL[m] || m}?`}
                description={`@${u.username} will no longer be asked for this at sign-in.`}
                confirmLabel="Remove"
                disabled={!!busy}
                onConfirm={() => act(`2fa-${m}`, 'adminRemove2faMethod', { method: m }, `${METHOD_LABEL[m] || m} removed`)}
              >
                Remove
              </ConfirmButton>
            </Row>
          ))
        )}
        {methods.length > 0 && (
          <Row icon={ShieldOff} label="Reset all two-factor" desc="Clears every method so they can sign in with just a password.">
            <ConfirmButton
              title={`Reset 2FA for @${u.username}?`}
              description="This clears all of their second-factor methods. They can set them up again in Settings."
              confirmLabel="Reset"
              disabled={!!busy}
              onConfirm={() => act('2fa', 'adminReset2fa', {}, '2FA reset')}
            >
              Reset all
            </ConfirmButton>
          </Row>
        )}
        <Row
          icon={KeyRound}
          label="Password"
          desc={u.has_password ? 'A password is set.' : 'No password set (passkey only).'}
          stack
        >
          <div className="flex flex-wrap gap-2">
            <ConfirmButton
              title={`Reset password for @${u.username}?`}
              description="Their current password stops working and they're signed out on every device. You'll get a temporary password to give them."
              confirmLabel="Reset password"
              disabled={!!busy}
              onConfirm={async () => {
                const r = await act('pw', 'adminResetPassword', {}, null);
                if (r?.password) setTempPassword({ username: u.username, password: r.password });
              }}
            >
              Temporary password
            </ConfirmButton>
            <Button variant="outline" size="sm" onClick={() => setSetPwOpen(true)} disabled={!!busy}>
              Set password…
            </Button>
          </div>
        </Row>
        <Row icon={LogOut} label="Sign out everywhere" desc="Ends their sessions on every browser and iPhone.">
          <ConfirmButton
            title={`Sign @${u.username} out everywhere?`}
            description="They'll need to log in again on every device."
            confirmLabel="Sign out"
            disabled={!!busy || isSelf}
            onConfirm={() => act('signout', 'adminSignOutUser', {}, 'Signed out everywhere')}
          >
            Sign out
          </ConfirmButton>
        </Row>
      </Section>

      <AdminUserSettings user={u} onSave={saveSettings} disabled={!!busy} />

      <Section title="Connections">
        {CONNECTORS.map((c) => {
          const conn = google.get(c.id);
          return (
            <Row
              key={c.id}
              icon={c.icon}
              label={c.label}
              desc={conn ? `Connected ${fmtDay(conn.created_at)}` : 'Not connected'}
            >
              {conn && (
                <ConfirmButton
                  title={`Disconnect ${c.label}?`}
                  description="Their saved Google access for this connection is deleted. Imported assignments stay."
                  confirmLabel="Disconnect"
                  disabled={!!busy}
                  onConfirm={() => act(c.id, 'adminDisconnectUser', { connector: c.id }, `${c.label} disconnected`)}
                >
                  <Unlink className="h-4 w-4 mr-1" /> Disconnect
                </ConfirmButton>
              )}
            </Row>
          );
        })}
        <Row
          icon={BookOpen}
          label="Blackboard"
          desc={connections?.blackboard?.connected ? `Connected${connections.blackboard.domain ? ` to ${connections.blackboard.domain}` : ''}` : 'Not connected'}
        >
          {connections?.blackboard?.connected && (
            <ConfirmButton
              title="Disconnect Blackboard?"
              description="Their saved Blackboard access is deleted. Imported assignments stay."
              confirmLabel="Disconnect"
              disabled={!!busy}
              onConfirm={() => act('blackboard', 'adminDisconnectUser', { connector: 'blackboard' }, 'Blackboard disconnected')}
            >
              <Unlink className="h-4 w-4 mr-1" /> Disconnect
            </ConfirmButton>
          )}
        </Row>
      </Section>

      <Section title="Devices" footer="iPhones registered for push notifications and Live Activities.">
        {devices.length === 0 ? (
          <p className="px-4 py-4 text-sm text-muted-foreground">No devices registered.</p>
        ) : (
          devices.map((d) => (
            <Row
              key={`${d.id}-${d.kind}`}
              icon={Smartphone}
              label={DEVICE_KIND[d.kind] || d.kind}
              desc={
                <>
                  <span className="font-mono">{d.token}</span>
                  {' · '}
                  {d.environment || 'production'}
                  {d.bundle_id ? ` · ${d.bundle_id}` : ''}
                  <br />
                  Last seen {fmtDate(d.updated_at)}
                </>
              }
            >
              <ConfirmButton
                title="Remove this device?"
                description="It stops receiving notifications until the app registers it again."
                confirmLabel="Remove"
                variant="ghost"
                size="icon"
                disabled={!!busy}
                onConfirm={() => act(`dev-${d.id}`, 'adminRemoveDevice', { deviceId: d.id }, 'Device removed')}
              >
                <Trash2 className="h-4 w-4 text-destructive" />
                <span className="sr-only">Remove device</span>
              </ConfirmButton>
            </Row>
          ))
        )}
      </Section>

      <section>
        <p className="ios-section-header">Overview</p>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {statTiles.map(([label, value]) => (
            <div key={label} className="rounded-xl bg-card px-3 py-2.5">
              <p className="text-[13px] text-muted-foreground">{label}</p>
              <p className="text-lg font-semibold tabular-nums">{value}</p>
            </div>
          ))}
        </div>
      </section>

      <AdminUserRecords userId={userId} stats={stats} onChanged={() => load({ quiet: true })} />

      <Section title="Actions">
        <Row icon={Bell} label="Test notification" desc="Sends to their notification bell and iPhones.">
          <Button
            variant="outline"
            size="sm"
            disabled={!!busy}
            onClick={() =>
              act('test', 'adminSendTestNotification', {}, (r) => ({
                title: 'Test notification sent',
                description: r?.push_delivered
                  ? `Delivered to the bell and ${r.devices} iPhone${r.devices === 1 ? '' : 's'}.`
                  : 'Delivered to the bell. No iPhone received a push.',
              }))
            }
          >
            Send
          </Button>
        </Row>
        <Row icon={Flame} label="Restore streak" desc="Marks overdue assignments complete to rebuild their streak.">
          <ConfirmButton
            title="Restore streak?"
            description={`All ${stats.overdue} overdue assignment(s) will be marked complete.`}
            confirmLabel="Restore"
            destructive={false}
            disabled={!!busy}
            onConfirm={() =>
              act('streak', 'adminRestoreStreak', {}, (r) => ({ title: 'Streak restored', description: `${r?.restored ?? 0} overdue assignment(s) marked complete.` }))
            }
          >
            Restore
          </ConfirmButton>
        </Row>
        <Row icon={RotateCcw} label="Reset onboarding" desc="Shows the setup wizard again on their next visit.">
          <Button variant="outline" size="sm" disabled={!!busy || isSelf} onClick={() => act('onboarding', 'adminResetOnboarding', {}, 'Onboarding reset')}>
            Reset
          </Button>
        </Row>
        <Row icon={GitMerge} label="Merge into another account" desc="Moves their assignments and classes, then removes this account.">
          <Button
            variant="outline"
            size="sm"
            disabled={!!busy || isSelf}
            onClick={async () => {
              try {
                const res = await api.functions.invoke('adminListUsers', {});
                setMergeUsers(res.data.users);
              } catch (e) {
                toast({ title: 'Failed to load users', description: e.message, variant: 'destructive' });
              }
            }}
          >
            Merge…
          </Button>
        </Row>
        <Row icon={Trash2} label="Delete account" desc="Permanently removes the account and all of its data." danger>
          <ConfirmButton
            title={`Delete @${u.username}?`}
            description="This permanently removes the account and everything in it. This cannot be undone."
            confirmLabel="Delete"
            variant="destructive"
            disabled={!!busy || isSelf}
            onConfirm={async () => {
              const r = await act('delete', 'adminDeleteUser', {}, `Deleted @${u.username}`, { reload: false });
              if (r) navigate('/admin/people', { replace: true });
            }}
          >
            Delete
          </ConfirmButton>
        </Row>
      </Section>

      <TempPasswordDialog value={tempPassword} onClose={() => setTempPassword(null)} />
      <SetPasswordDialog
        open={setPwOpen}
        onOpenChange={setSetPwOpen}
        onSubmit={async (password, signOut) => {
          await api.functions.invoke('adminSetPassword', { userId, password, signOut });
          toast({ title: 'Password set', description: signOut ? 'They were signed out everywhere.' : undefined });
          await load({ quiet: true });
        }}
      />
      <DisableDialog
        open={disableOpen}
        onOpenChange={setDisableOpen}
        username={u.username}
        onSubmit={(reason) => act('role', 'adminUpdateUserRole', { role: 'disabled', reason }, `@${u.username} disabled`)}
      />
      <MergeDialog
        open={!!mergeUsers}
        onOpenChange={(o) => !o && setMergeUsers(null)}
        source={u}
        users={mergeUsers || []}
        onMerge={async (targetId) => {
          setMergeUsers(null);
          setBusy('merge');
          try {
            const res = await api.functions.invoke('adminMergeAccounts', { sourceId: u.id, targetId });
            toast({ title: 'Accounts merged', description: `Moved ${res.data.movedAssignments} assignment(s) and ${res.data.movedClasses} class(es).` });
            navigate(`/admin/people/${targetId}`, { replace: true });
          } catch (e) {
            if (!e?.elevationCancelled) toast({ title: 'Merge failed', description: e.message, variant: 'destructive' });
          } finally {
            setBusy(null);
          }
        }}
      />
    </div>
  );
}
