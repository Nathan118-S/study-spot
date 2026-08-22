import React from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Smartphone, Mail, Fingerprint } from 'lucide-react';

const OPTIONS = [
  { id: 'totp', label: 'Authenticator app', desc: 'Google Authenticator, Authy, 2FAS', icon: Smartphone },
  { id: 'email', label: 'Email', desc: 'A code sent to your email each sign-in', icon: Mail },
  { id: 'passkey', label: 'Passkey', desc: 'Face, fingerprint, or device PIN', icon: Fingerprint },
];

export default function MethodChooser({ open, onOpenChange, onPick, enabledMethods = [] }) {
  const options = OPTIONS.filter((o) => !enabledMethods.includes(o.id));
  const adding = enabledMethods.length > 0;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{adding ? 'Add another method' : 'Choose a second factor'}</DialogTitle>
          <DialogDescription>
            {adding
              ? 'Add a backup way to verify at sign-in.'
              : 'Pick how you want to verify at sign-in. You can add more later.'}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2 py-2">
          {options.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-4">
              All methods are already enabled.
            </p>
          ) : (
            options.map((o) => {
              const Icon = o.icon;
              return (
                <button
                  key={o.id}
                  onClick={() => onPick(o.id)}
                  className="w-full flex items-center gap-3 rounded-lg border p-3 text-left hover:bg-accent transition-colors"
                >
                  <Icon className="h-5 w-5 text-primary shrink-0" />
                  <div className="min-w-0">
                    <p className="font-medium">{o.label}</p>
                    <p className="text-sm text-muted-foreground">{o.desc}</p>
                  </div>
                </button>
              );
            })
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}