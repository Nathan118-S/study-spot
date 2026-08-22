import React, { useState } from 'react';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ShieldCheck, KeyRound, Smartphone, Mail, Fingerprint, Plus, Trash2 } from 'lucide-react';
import { getMethods, METHOD_LABEL } from '@/lib/twofa';
import MethodChooser from './twofa/MethodChooser';
import TotpSetup from './twofa/TotpSetup';
import EmailSetup from './twofa/EmailSetup';
import PasskeySetup from './twofa/PasskeySetup';
import RemoveMethodDialog from './twofa/RemoveMethodDialog';

const METHOD_ICON = { totp: Smartphone, email: Mail, passkey: Fingerprint };
const ALL_METHODS = ['totp', 'email', 'passkey'];

export default function TwoFactorSettings() {
  const { user, checkUserAuth } = useAuth();
  const methods = getMethods(user);
  const enabled = methods.length > 0;
  const [dialog, setDialog] = useState(null);

  const onDone = async () => {
    await checkUserAuth();
    setDialog(null);
  };

  const availableToAdd = ALL_METHODS.filter((m) => !methods.includes(m));
  const removing = dialog && typeof dialog === 'object' ? dialog.remove : null;

  return (
    <div className="rounded-lg border bg-card p-4 space-y-4">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3 min-w-0">
          <div className="mt-0.5"><ShieldCheck className="h-5 w-5 text-primary" /></div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <p className="font-medium">Two-factor authentication</p>
              {enabled ? (
                <Badge className="bg-emerald-500 hover:bg-emerald-500">
                  Enabled{methods.length > 1 ? ` · ${methods.length} methods` : ''}
                </Badge>
              ) : (
                <Badge variant="secondary">Disabled</Badge>
              )}
            </div>
            <p className="text-sm text-muted-foreground">
              Add a second step at sign-in using an authenticator app, email code, or a passkey.
            </p>
          </div>
        </div>
        {!enabled && (
          <Button size="sm" className="shrink-0" onClick={() => setDialog('chooser')}>
            <KeyRound className="h-4 w-4 mr-1" /> Enable
          </Button>
        )}
      </div>

      {enabled && (
        <>
          <div className="space-y-2">
            {methods.map((m) => {
              const Icon = METHOD_ICON[m];
              return (
                <div key={m} className="flex items-center justify-between gap-3 rounded-lg border p-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <Icon className="h-4 w-4 text-primary shrink-0" />
                    <span className="text-sm font-medium">{METHOD_LABEL[m]}</span>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => setDialog({ remove: m })}>
                    <Trash2 className="h-4 w-4 mr-1" /> Remove
                  </Button>
                </div>
              );
            })}
          </div>
          {availableToAdd.length > 0 && (
            <Button variant="outline" size="sm" onClick={() => setDialog('chooser')}>
              <Plus className="h-4 w-4 mr-1" /> Add another method
            </Button>
          )}
        </>
      )}

      <MethodChooser
        open={dialog === 'chooser'}
        onOpenChange={(o) => !o && setDialog(null)}
        onPick={(m) => setDialog(m)}
        enabledMethods={methods}
      />
      <TotpSetup open={dialog === 'totp'} onOpenChange={(o) => !o && setDialog(null)} onDone={onDone} />
      <EmailSetup open={dialog === 'email'} onOpenChange={(o) => !o && setDialog(null)} onDone={onDone} />
      <PasskeySetup open={dialog === 'passkey'} onOpenChange={(o) => !o && setDialog(null)} onDone={onDone} />
      <RemoveMethodDialog
        open={!!removing}
        onOpenChange={(o) => !o && setDialog(null)}
        method={removing}
        onDone={onDone}
      />
    </div>
  );
}