import React, { useState, useEffect } from 'react';
import { api } from '@/api/client';
import { useToast } from '@/components/ui/use-toast';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Loader2, Fingerprint } from 'lucide-react';
import { b64uEncode, b64uDecode } from '@/lib/webauthn';

export default function PasskeySetup({ open, onOpenChange, onDone }) {
  const { toast } = useToast();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const register = async () => {
    setError('');
    setBusy(true);
    try {
      const startRes = await api.functions.invoke('passkeyRegisterStart', {});
      const opts = startRes.data;
      const publicKey = {
        ...opts,
        challenge: b64uDecode(opts.challenge),
        user: { ...opts.user, id: b64uDecode(opts.user.id) },
        excludeCredentials: (opts.excludeCredentials || []).map((c) => ({ ...c, id: b64uDecode(c.id) })),
      };
      const cred = await navigator.credentials.create({ publicKey });
      await api.functions.invoke('passkeyRegisterFinish', {
        credentialId: b64uEncode(cred.rawId),
        attestationObject: b64uEncode(cred.response.attestationObject),
        clientDataJSON: b64uEncode(cred.response.clientDataJSON),
      });
      sessionStorage.setItem('cf-2fa-verified', '1');
      toast({ title: 'Passkey enabled' });
      await onDone();
    } catch (e) {
      setError(e.response?.data?.error || e.message || 'Passkey setup failed');
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (open) register();
     
  }, [open]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Set up a passkey</DialogTitle>
          <DialogDescription>
            Use your device's biometrics or PIN to create a passkey for sign-in.
          </DialogDescription>
        </DialogHeader>
        <div className="py-4 text-center space-y-3">
          {busy ? (
            <Loader2 className="h-8 w-8 animate-spin text-primary mx-auto" />
          ) : (
            <Fingerprint className="h-8 w-8 text-primary mx-auto" />
          )}
          {error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : (
            <p className="text-sm text-muted-foreground">
              {busy ? 'Follow your device prompts…' : 'Starting…'}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
          <Button onClick={register} disabled={busy}>Retry</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}