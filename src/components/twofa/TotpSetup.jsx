import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { useToast } from '@/components/ui/use-toast';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Loader2, Copy, Check } from 'lucide-react';

export default function TotpSetup({ open, onOpenChange, onDone }) {
  const { toast } = useToast();
  const [secret, setSecret] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  const start = async () => {
    setError('');
    setCode('');
    setBusy(true);
    try {
      const res = await base44.functions.invoke('setup2fa', {});
      setSecret(res.data.secret);
    } catch (e) {
      toast({ title: 'Failed to start setup', description: e.message, variant: 'destructive' });
      onOpenChange(false);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (open) start();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {}
  };

  const confirm = async () => {
    setError('');
    setBusy(true);
    try {
      await base44.functions.invoke('enable2fa', { code, secret });
      sessionStorage.setItem('cf-2fa-verified', '1');
      toast({ title: 'Authenticator app enabled' });
      await onDone();
    } catch (e) {
      setError(e.response?.data?.error || e.message || 'Invalid code');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Authenticator app</DialogTitle>
          <DialogDescription>
            Add this key to your app, then enter the 6-digit code it generates.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <p className="text-xs text-muted-foreground">Account: <span className="font-medium text-foreground">Study Spot</span></p>
            <div className="flex items-center gap-2">
              <code className="flex-1 rounded-md border bg-muted px-3 py-2 font-mono text-sm tracking-wider break-all">
                {secret}
              </code>
              <Button variant="outline" size="icon" onClick={copy} title="Copy key">
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
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
          <Button onClick={confirm} disabled={busy || code.length !== 6}>
            {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
            Enable
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}