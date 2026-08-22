import React from 'react';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '@/components/ui/dialog';
import {
  AlertDialog, AlertDialogTrigger, AlertDialogContent, AlertDialogHeader,
  AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogCancel, AlertDialogAction,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import SheetSelect from '@/components/SheetSelect';
import { KeyRound, Trash2, Flame, ShieldOff, Mail, GitMerge, Bell, Send } from 'lucide-react';

export default function UserManageDialog({
  open, onOpenChange, user: u, currentUser, busy,
  onSetRole, onResetPassword, onRestoreStreak, onVerifyUser, onReset2fa, onDeleteUser, onMerge,
  onSendTestPush, onSendTestEmail,
}) {
  if (!u) return null;
  const isSelf = u.id === currentUser?.id;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <span className="truncate">{u.full_name || u.email}</span>
            <Badge variant={u.role === 'admin' ? 'default' : 'secondary'} className="capitalize">{u.role}</Badge>
          </DialogTitle>
          <DialogDescription className="truncate">{u.email}</DialogDescription>
        </DialogHeader>
        <p className="text-xs text-muted-foreground -mt-1">
          Joined {u.created_date ? new Date(u.created_date).toLocaleDateString() : '—'}
        </p>

        <div className="space-y-4 pt-2">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium">Role</p>
              <p className="text-xs text-muted-foreground">Promote or demote this user.</p>
            </div>
            <SheetSelect
              value={u.role}
              onValueChange={(r) => onSetRole(u, r)}
              triggerClassName="w-[130px]"
              options={[
                { value: 'user', label: 'User' },
                { value: 'admin', label: 'Admin' },
              ]}
            />
          </div>

          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" onClick={() => onResetPassword(u)} disabled={busy === u.id} className="h-11 md:h-9">
              <KeyRound className="h-4 w-4 mr-1" /> Reset password
            </Button>
            <Button size="sm" variant="outline" onClick={() => onRestoreStreak(u)} disabled={busy === u.id} className="h-11 md:h-9">
              <Flame className="h-4 w-4 mr-1" /> Restore streak
            </Button>
            {!u.is_verified && (
              <Button size="sm" variant="outline" onClick={() => onVerifyUser(u)} disabled={busy === u.id} className="h-11 md:h-9">
                <Mail className="h-4 w-4 mr-1" /> Resend verification
              </Button>
            )}
            <Button size="sm" variant="outline" onClick={() => onSendTestPush(u)} disabled={busy === u.id} className="h-11 md:h-9">
              <Bell className="h-4 w-4 mr-1" /> Test push
            </Button>
            <Button size="sm" variant="outline" onClick={() => onSendTestEmail(u)} disabled={busy === u.id} className="h-11 md:h-9">
              <Send className="h-4 w-4 mr-1" /> Test email
            </Button>
            <Button size="sm" variant="outline" onClick={() => onMerge(u)} disabled={busy === u.id || isSelf} className="h-11 md:h-9">
              <GitMerge className="h-4 w-4 mr-1" /> Merge
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button size="sm" variant="outline" disabled={busy === u.id} className="h-11 md:h-9">
                  <ShieldOff className="h-4 w-4 mr-1" /> Reset 2FA
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Reset 2FA for {u.email}?</AlertDialogTitle>
                  <AlertDialogDescription>
                    This clears their second-factor setup so they can sign in without a code. They can re-enable 2FA from Settings.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel disabled={busy === u.id}>Cancel</AlertDialogCancel>
                  <AlertDialogAction onClick={() => onReset2fa(u)} disabled={busy === u.id}>Reset</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button size="sm" variant="destructive" disabled={busy === u.id || isSelf} className="h-11 md:h-9">
                  <Trash2 className="h-4 w-4 mr-1" /> Delete
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Delete {u.email}?</AlertDialogTitle>
                  <AlertDialogDescription>
                    This permanently removes the user account. This action cannot be undone.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel disabled={busy === u.id}>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                    onClick={() => onDeleteUser(u)}
                    disabled={busy === u.id}
                  >
                    Delete
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}