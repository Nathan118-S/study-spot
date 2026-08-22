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
import { Loader2 } from 'lucide-react';
import { b64uEncode, b64uDecode } from '@/lib/webauthn';
import { METHOD_LABEL } from '@/lib/twofa';

export default function RemoveMethodDialog({ open, onOpenChange, method, onDone }) {
  const { toast } = useToast();
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);

  useEffect(() => {
    if (open) {
      setCode('');
      setError('');
      setSent(false);
    }
  }, [open, method]);

  const sendEmail = async () => {
    setBusy(true);
    setError('');
    try {
      await base44.functions.invoke('email2faSend', {});
      setSent(true);
    } catch (e) {
      setError(e.response?.data?.error || e.message || 'Failed to send code');
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (open && method === 'email') sendEmail();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, method]);

  const passkeyVerify = async () => {
    const startRes = await base44.functions.invoke('passkeyLoginStart', {});
    const opts = startRes.data;
    const assertion = await navigator.credentials.get({
      publicKey: {
        challenge: b64uDecode(opts.challenge),
        rpId: window.location.hostname,
        allowCredentials: (opts.allowCredentials || []).map((c) => ({ ...c, id: b64uDecode(c.id) })),
        userVerification: 'preferred',
        timeout: 60000,
      },
    });
    await base44.functions.invoke('passkeyLoginFinish', {
      credentialId: b64uEncode(assertion.rawId),
      authenticatorData: b64uEncode(assertion.response.authenticatorData),
      clientDataJSON: b64uEncode(assertion.response.clientDataJSON),
      signature: b64uEncode(assertion.response.signature),
    });
  };

  const confirm = async () => {
    setError('');
    setBusy(true);
    try {
      if (method === 'totp') {
        await base44.functions.invoke('verify2fa', { code });
      } else if (method === 'email') {
        await base44.functions.invoke('email2faVerify', { code });
      } else if (method === 'passkey') {
        await passkeyVerify();
      }
      await base44.functions.invoke('remove2faMethod', { method });
      toast({ title: `${METHOD_LABEL[method] || 'Method'} removed` });
      await onDone();
    } catch (e) {
      setError(e.response?.data?.error || e.message || 'Verification failed');
    } finally {
      setBusy(false);
    }
  };

  if (!method) return null;

  if (method === 'passkey') {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Remove {METHOD_LABEL[method]}?</DialogTitle>
            <DialogDescription>Verify with your passkey to confirm removal.</DialogDescription>
          </DialogHeader>
          <div className="py-2">
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
            <Button variant="destructive" onClick={confirm} disabled={busy}>
              {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
              Remove
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Remove {METHOD_LABEL[method]}?</DialogTitle>
          <DialogDescription>
            {method === 'email'
              ? 'Enter the code sent to your email to confirm removal.'
              : 'Enter a current 6-digit code from your authenticator app.'}
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
            autoFocus
          />
          {error && <p className="text-sm text-destructive">{error}</p>}
          {method === 'email' && (
            <Button variant="ghost" size="sm" onClick={sendEmail} disabled={busy} className="w-full">
              {sent ? 'Resend code' : 'Send code'}
            </Button>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
          <Button variant="destructive" onClick={confirm} disabled={busy || code.length !== 6}>
            {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
            Remove
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}