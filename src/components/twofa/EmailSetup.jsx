import React, { useState, useEffect } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
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
import { Loader2 } from 'lucide-react';

export default function EmailSetup({ open, onOpenChange, onDone }) {
  const { user } = useAuth();
  const { toast } = useToast();
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);

  const send = async () => {
    setSending(true);
    setError('');
    try {
      await api.functions.invoke('email2faSend', {});
      setSent(true);
    } catch (e) {
      setError(e.response?.data?.error || e.message || 'Failed to send code');
    } finally {
      setSending(false);
    }
  };

  useEffect(() => {
    if (open) {
      setCode('');
      setError('');
      setSent(false);
      send();
    }
     
  }, [open]);

  const confirm = async () => {
    setError('');
    setBusy(true);
    try {
      await api.functions.invoke('email2faVerify', { code, enable: true });
      sessionStorage.setItem('cf-2fa-verified', '1');
      toast({ title: 'Email verification enabled' });
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
          <DialogTitle>Email verification</DialogTitle>
          <DialogDescription>
            We sent a 6-digit code to {user?.email}. Enter it below to enable.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <Input
            inputMode="numeric"
            pattern="[0-9]*"
            maxLength={6}
            placeholder="123456"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            className="text-center text-xl tracking-[0.4em] h-12 font-mono"
            autoFocus
          />
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button variant="ghost" size="sm" onClick={send} disabled={sending} className="w-full">
            {sending ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
            {sent ? 'Resend code' : 'Send code'}
          </Button>
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