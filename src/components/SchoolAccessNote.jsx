import React, { useState } from 'react';
import { Info, Mail } from 'lucide-react';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
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
import { SCHOOL_PLACEHOLDER, schoolItEmailUrl } from '@/lib/schoolItEmail';

const SERVICE_LABEL = {
  google: 'school Google',
  blackboard: 'Blackboard',
  both: 'school Google or Blackboard',
};

// Shown before connecting Google or Blackboard: many schools restrict which
// third-party apps can access school accounts. Offers a drafted email asking
// the school's IT team to approve Study Spot.
export default function SchoolAccessNote({ service = 'both' }) {
  const { user } = useAuth();
  const [confirming, setConfirming] = useState(false);

  const openEmail = () => {
    window.location.href = schoolItEmailUrl(user, service);
  };

  return (
    <div className="rounded-lg bg-secondary/70 px-3 py-2.5 text-xs text-secondary-foreground space-y-2">
      <div className="flex items-start gap-2">
        <Info className="h-4 w-4 shrink-0 mt-px" />
        <p>
          Some schools block connecting third-party apps like Study Spot to {SERVICE_LABEL[service]} accounts. If the
          connection is blocked or fails, ask your school&apos;s IT team, or add your assignments manually.
        </p>
      </div>
      <Button type="button" variant="outline" size="sm" className="ml-6 bg-card" onClick={() => setConfirming(true)}>
        <Mail className="h-4 w-4 mr-1.5" /> Email my school&apos;s IT team
      </Button>

      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Before you send it</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-2">
                <p>
                  We&apos;ll open a drafted email in your mail app asking your school to approve Study Spot, signed with
                  your name.
                </p>
                <p className="font-medium text-foreground">
                  Replace every “{SCHOOL_PLACEHOLDER}” with your school&apos;s name, and add your IT team&apos;s email
                  address as the recipient.
                </p>
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={openEmail}>Open email</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
