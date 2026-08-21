import React, { useState } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { useToast } from '@/components/ui/use-toast';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { ShieldCheck, Loader2, Copy, Check, KeyRound } from 'lucide-react';

export default function TwoFactorSettings() {
  const { user, checkUserAuth } = useAuth();
  const { toast } = useToast();
  const enabled = !!user?.twofa_enabled;

  const [setupOpen, setSetupOpen] = useState(false);
  const [disableOpen, setDisableOpen] = useState(false);
  const [secret, setSecret] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  const startSetup = async () => {
    setError('');
    setCode('');
    setBusy(true);
    try {
      const res = await base44.functions.invoke('setup2fa', {});
      setSecret(res.data.secret);
      setSetupOpen(true);
    } catch (err) {
      toast({ title: 'Failed to start setup', description: err.message, variant: 'destructive' });
    } finally {
      setBusy(false);
    }
  };

  const copySecret = async () => {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {}
  };

  const confirmEnable = async () => {
    setError('');
    setBusy(true);
    try {
      await base44.functions.invoke('enable2fa', { code, secret });
      await base44.auth.updateMe({ totp_secret: secret, twofa_enabled: true });
      sessionStorage.setItem('cf-2fa-verified', '1');
      await checkUserAuth();
      setSetupOpen(false);
      toast({ title: 'Two-factor authentication enabled' });
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Invalid code');
    } finally {
      setBusy(false);
    }
  };

  const confirmDisable = async () => {
    setError('');
    setBusy(true);
    try {
      await base44.functions.invoke('disable2fa', { code });
      await base44.auth.updateMe({ totp_secret: null, twofa_enabled: false });
      sessionStorage.removeItem('cf-2fa-verified');
      await checkUserAuth();
      setDisableOpen(false);
      toast({ title: 'Two-factor authentication disabled' });
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Invalid code');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rounded-lg border bg-card p-4 flex items-center justify-between gap-4">
      <div className="flex items-start gap-3 min-w-0">
        <div className="mt-0.5"><ShieldCheck className="h-5 w-5 text-primary" /></div>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="font-medium">Two-factor authentication</p>
            {enabled ? (
              <Badge className="bg-emerald-500 hover:bg-emerald-500">Enabled</Badge>
            ) : (
              <Badge variant="secondary">Disabled</Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">
            Require a 6-digit code from an authenticator app (Google Authenticator, Authy, 2FAS) on every sign-in.
          </p>
        </div>
      </div>
      <div className="shrink-0">
        {enabled ? (
          <Button variant="outline" size="sm" onClick={() => { setCode(''); setError(''); setDisableOpen(true); }}>
            Disable
          </Button>
        ) : (
          <Button size="sm" onClick={startSetup} disabled={busy}>
            {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <KeyRound className="h-4 w-4 mr-1" />}
            Enable
          </Button>
        )}
      </div>

      <Dialog open={setupOpen} onOpenChange={setSetupOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Set up two-factor authentication</DialogTitle>
            <DialogDescription>
              Add this key to your authenticator app, then enter the 6-digit code it generates.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <p className="text-xs text-muted-foreground">Account: <span className="font-medium text-foreground">Study Spot</span></p>
              <div className="flex items-center gap-2">
                <code className="flex-1 rounded-md border bg-muted px-3 py-2 font-mono text-sm tracking-wider break-all">
                  {secret}
                </code>
                <Button variant="outline" size="icon" onClick={copySecret} title="Copy key">
                  {copied ? <Check className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
                </Button>
              </div>
            </div>
            <div className="space-y-2">
              <p className="text-sm font-medium">Enter the 6-digit code</p>
              <Input
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={6}
                placeholder="123456"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                className="text-center text-xl tracking-[0.4em] h-12 font-mono"
              />
              {error && <p className="text-sm text-destructive">{error}</p>}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSetupOpen(false)} disabled={busy}>Cancel</Button>
            <Button onClick={confirmEnable} disabled={busy || code.length !== 6}>
              {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
              Enable
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={disableOpen} onOpenChange={setDisableOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Disable two-factor authentication</DialogTitle>
            <DialogDescription>
              Enter a current 6-digit code from your authenticator app to confirm.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 py-2">
            <Input
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              placeholder="123456"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              className="text-center text-xl tracking-[0.4em] h-12 font-mono"
            />
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDisableOpen(false)} disabled={busy}>Cancel</Button>
            <Button variant="destructive" onClick={confirmDisable} disabled={busy || code.length !== 6}>
              {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
              Disable
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}