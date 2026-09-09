import React, { useState, useEffect, useMemo } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Navigate, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/use-toast';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Loader2, ArrowLeft, Lock, KeyRound, Trash2, Save, Send } from 'lucide-react';

export default function AdminSecrets() {
  const { user, isLoadingAuth } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();

  const [secrets, setSecrets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [drafts, setDrafts] = useState({});
  const [saving, setSaving] = useState(false);
  const [clearing, setClearing] = useState(null);
  const [confirmClear, setConfirmClear] = useState(null);
  const [testEmailTo, setTestEmailTo] = useState('');
  const [sendingTest, setSendingTest] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.functions.invoke('adminListSecrets', {});
      setSecrets(res.data.secrets);
    } catch (e) {
      toast({ title: 'Failed to load secrets', description: e.message, variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user?.role === 'admin') load();
  }, [user]);

  const groups = useMemo(() => {
    const byGroup = new Map();
    for (const s of secrets) {
      if (!byGroup.has(s.group)) byGroup.set(s.group, []);
      byGroup.get(s.group).push(s);
    }
    return Array.from(byGroup.entries());
  }, [secrets]);

  if (isLoadingAuth) {
    return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  }
  if (!user || user.role !== 'admin') return <Navigate to="/" replace />;

  const pendingCount = Object.values(drafts).filter((v) => v && v.trim()).length;

  const save = async () => {
    const values = Object.fromEntries(
      Object.entries(drafts).filter(([, v]) => v && v.trim())
    );
    if (!Object.keys(values).length) return;
    setSaving(true);
    try {
      const res = await api.functions.invoke('adminSetSecrets', { values });
      const failed = Object.entries(res.data.results).filter(([, r]) => !r.ok);
      setSecrets(res.data.secrets);
      setDrafts({});
      if (failed.length) {
        toast({
          title: 'Some secrets were not saved',
          description: failed.map(([k, r]) => `${k}: ${r.error}`).join('; '),
          variant: 'destructive',
        });
      } else {
        toast({ title: 'Secrets saved', description: 'They are encrypted and cannot be viewed again — clear one to replace it.' });
      }
    } catch (e) {
      toast({ title: 'Save failed', description: e.message, variant: 'destructive' });
    } finally {
      setSaving(false);
    }
  };

  const sendTestEmail = async () => {
    if (!testEmailTo.trim()) return;
    setSendingTest(true);
    try {
      await api.functions.invoke('adminSendTestEmail', { to: testEmailTo.trim() });
      toast({ title: 'Test email sent', description: `Check ${testEmailTo.trim()} for delivery.` });
    } catch (e) {
      toast({ title: 'Test email failed', description: e.message, variant: 'destructive' });
    } finally {
      setSendingTest(false);
    }
  };

  const doClear = async (key) => {
    setClearing(key);
    try {
      const res = await api.functions.invoke('adminClearSecret', { key });
      setSecrets(res.data.secrets);
      toast({ title: 'Cleared', description: `${key} can now be re-entered.` });
    } catch (e) {
      toast({ title: 'Failed to clear', description: e.message, variant: 'destructive' });
    } finally {
      setClearing(null);
      setConfirmClear(null);
    }
  };

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate('/admin')} title="Back to Admin">
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold flex items-center gap-2">
            <KeyRound className="h-6 w-6 text-primary" /> Integration Secrets
          </h1>
          <p className="text-muted-foreground text-sm">
            Paste each credential once. They're encrypted at rest and can never be viewed again through this
            panel — only cleared and re-entered.
          </p>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : (
        <div className="space-y-6">
          {groups.map(([group, items]) => (
            <div key={group} className="rounded-lg border bg-card p-4 space-y-4">
              <p className="font-medium">{group}</p>
              <div className="space-y-3">
                {items.map((s) => (
                  <div key={s.key} className="space-y-1.5">
                    <div className="flex items-center justify-between gap-2">
                      <label htmlFor={s.key} className="text-sm text-muted-foreground">{s.label}</label>
                      {s.isSet && (
                        <Badge variant="secondary" className="gap-1">
                          <Lock className="h-3 w-3" /> Configured
                        </Badge>
                      )}
                    </div>
                    {s.isSet ? (
                      <div className="flex items-center gap-2">
                        <Input disabled value="••••••••••••••••" className="font-mono" />
                        <Button
                          type="button"
                          variant="outline"
                          size="icon"
                          title={`Clear ${s.label} so it can be re-entered`}
                          onClick={() => setConfirmClear(s)}
                          disabled={clearing === s.key}
                        >
                          {clearing === s.key ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                        </Button>
                      </div>
                    ) : (
                      <Input
                        id={s.key}
                        type="text"
                        autoComplete="off"
                        placeholder={`Paste ${s.label.toLowerCase()}`}
                        value={drafts[s.key] || ''}
                        onChange={(e) => setDrafts((d) => ({ ...d, [s.key]: e.target.value }))}
                        className="font-mono"
                      />
                    )}
                  </div>
                ))}
              </div>
              {group === 'Email (SMTP)' && items.find((s) => s.key === 'SMTP_HOST')?.isSet && (
                <div className="pt-1 space-y-1.5 border-t">
                  <p className="text-sm text-muted-foreground pt-3">
                    Send a test email to confirm delivery works — this is the same path new sign-ups use for their
                    verification code.
                  </p>
                  <div className="flex items-center gap-2">
                    <Input
                      type="email"
                      placeholder="you@example.com"
                      value={testEmailTo}
                      onChange={(e) => setTestEmailTo(e.target.value)}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      onClick={sendTestEmail}
                      disabled={sendingTest || !testEmailTo.trim()}
                      className="shrink-0"
                    >
                      {sendingTest ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                      <span className="ml-1.5 hidden sm:inline">Send test</span>
                    </Button>
                  </div>
                </div>
              )}
            </div>
          ))}

          <Button onClick={save} disabled={saving || pendingCount === 0} className="w-full h-11">
            {saving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Save className="h-4 w-4 mr-2" />}
            Save {pendingCount > 0 ? `${pendingCount} secret${pendingCount === 1 ? '' : 's'}` : ''}
          </Button>
        </div>
      )}

      <AlertDialog open={!!confirmClear} onOpenChange={(open) => !open && setConfirmClear(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Clear {confirmClear?.label}?</AlertDialogTitle>
            <AlertDialogDescription>
              This deletes the stored value for <span className="font-medium text-foreground">{confirmClear?.key}</span>{' '}
              so a new one can be pasted in. Anything relying on it (sign-in, sync, email) will stop working until
              it's replaced.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => doClear(confirmClear.key)}>Clear</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
