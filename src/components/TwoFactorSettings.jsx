import React, { useState } from 'react';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ShieldCheck, KeyRound } from 'lucide-react';
import MethodChooser from './twofa/MethodChooser';
import TotpSetup from './twofa/TotpSetup';
import EmailSetup from './twofa/EmailSetup';
import PasskeySetup from './twofa/PasskeySetup';
import DisableDialog from './twofa/DisableDialog';

const METHOD_LABEL = { totp: 'Authenticator app', email: 'Email', passkey: 'Passkey' };

export default function TwoFactorSettings() {
  const { user, checkUserAuth } = useAuth();
  const enabled = !!user?.twofa_enabled;
  const method = user?.twofa_method || (enabled ? 'totp' : '');
  const [dialog, setDialog] = useState(null);

  const onDone = async () => {
    await checkUserAuth();
    setDialog(null);
  };

  return (
    <div className="rounded-lg border bg-card p-4 flex items-center justify-between gap-4">
      <div className="flex items-start gap-3 min-w-0">
        <div className="mt-0.5"><ShieldCheck className="h-5 w-5 text-primary" /></div>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="font-medium">Two-factor authentication</p>
            {enabled ? (
              <Badge className="bg-emerald-500 hover:bg-emerald-500">Enabled · {METHOD_LABEL[method] || 'On'}</Badge>
            ) : (
              <Badge variant="secondary">Disabled</Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">
            Add a second step at sign-in using an authenticator app, email code, or a passkey.
          </p>
        </div>
      </div>
      <div className="shrink-0">
        {enabled ? (
          <Button variant="outline" size="sm" onClick={() => setDialog('disable')}>Disable</Button>
        ) : (
          <Button size="sm" onClick={() => setDialog('chooser')}>
            <KeyRound className="h-4 w-4 mr-1" /> Enable
          </Button>
        )}
      </div>

      <MethodChooser open={dialog === 'chooser'} onOpenChange={(o) => !o && setDialog(null)} onPick={(m) => setDialog(m)} />
      <TotpSetup open={dialog === 'totp'} onOpenChange={(o) => !o && setDialog(null)} onDone={onDone} />
      <EmailSetup open={dialog === 'email'} onOpenChange={(o) => !o && setDialog(null)} onDone={onDone} />
      <PasskeySetup open={dialog === 'passkey'} onOpenChange={(o) => !o && setDialog(null)} onDone={onDone} />
      <DisableDialog open={dialog === 'disable'} onOpenChange={(o) => !o && setDialog(null)} method={method} onDone={onDone} />
    </div>
  );
}