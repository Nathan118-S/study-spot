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

export default function DisableDialog({ open, onOpenChange, method, onDone }) {
  const { toast } = useToast();
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setCode('');
      setError('');
    }
  }, [open]);

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
      await base44.auth.updateMe({
        twofa_enabled: false,
        twofa_method: null,
        totp_secret: null,
        passkey_cred_id: null,
        passkey_pub_key: null,
        passkey_alg: null,
        passkey_counter: 0,
      });
      sessionStorage.removeItem('cf-2fa-verified');
      toast({ title: 'Two-factor authentication disabled' });
      await onDone();
    } catch (e) {
      setError(e.response?.data?.error || e.message || 'Verification failed');
    } finally {
      setBusy(false);
    }
  };

  if (method === 'passkey') {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Disable two-factor authentication</DialogTitle>
            <DialogDescription>Verify with your passkey to confirm.</DialogDescription>
          </DialogHeader>
          <div className="py-2">
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
            <Button variant="destructive" onClick={confirm} disabled={busy}>
              {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
              Disable
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
          <DialogTitle>Disable two-factor authentication</DialogTitle>
          <DialogDescription>
            {method === 'email'
              ? 'Enter the code sent to your email to confirm.'
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
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
          <Button variant="destructive" onClick={confirm} disabled={busy || code.length !== 6}>
            {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
            Disable
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}