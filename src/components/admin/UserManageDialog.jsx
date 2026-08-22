import React, { useState, useEffect } from 'react';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import {
  AlertDialog, AlertDialogTrigger, AlertDialogContent, AlertDialogHeader,
  AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogCancel, AlertDialogAction,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import SheetSelect from '@/components/SheetSelect';
import { KeyRound, Trash2, Flame, ShieldOff, Mail, GitMerge, Bell, Send, UserCog, Loader2, Check } from 'lucide-react';

function ActionRow({ icon, title, desc, children }) {
  return (
    <div className="rounded-lg border bg-card p-4 flex items-center justify-between gap-4">
      <div className="flex items-start gap-3 min-w-0">
        <div className="mt-0.5 text-primary">{icon}</div>
        <div className="min-w-0">
          <p className="text-sm font-medium">{title}</p>
          <p className="text-xs text-muted-foreground">{desc}</p>
        </div>
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  );
}

export default function UserManageDialog({
  open, onOpenChange, user: u, currentUser, busy,
  onSetRole, onResetPassword, onRestoreStreak, onVerifyUser, onReset2fa, onDeleteUser, onMerge,
  onSendTestPush, onSendTestEmail,
  onSaveName,
}) {
  const [name, setName] = useState('');
  useEffect(() => {
    setName(u?.name || u?.full_name || '');
  }, [u?.id]);
  if (!u) return null;
  const isSelf = u.id === currentUser?.id;
  const disabled = busy === u.id;
  const savedName = (u.name || u.full_name || '').trim();

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

        <div className="space-y-2 pt-2 max-h-[60vh] overflow-y-auto">
          <div className="rounded-lg border bg-card p-4 space-y-3">
            <div>
              <p className="text-sm font-medium">Display name</p>
              <p className="text-xs text-muted-foreground">Shown in emails and across the app.</p>
            </div>
            <div className="flex gap-2">
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Add a name"
                className="flex-1"
                maxLength={80}
              />
              <Button
                size="sm"
                onClick={() => onSaveName(u, name)}
                disabled={disabled || name.trim() === savedName}
              >
                {disabled ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Check className="h-4 w-4 mr-1" />}
                Save
              </Button>
            </div>
          </div>

          <ActionRow
            icon={<KeyRound className="h-5 w-5" />}
            title="Reset password"
            desc="Send a password-reset link to this user's email."
          >
            <Button variant="outline" size="sm" onClick={() => onResetPassword(u)} disabled={disabled}>
              <KeyRound className="h-4 w-4 mr-1" /> Send
            </Button>
          </ActionRow>

          <ActionRow
            icon={<Flame className="h-5 w-5" />}
            title="Restore streak"
            desc="Mark overdue assignments complete to rebuild their streak."
          >
            <Button variant="outline" size="sm" onClick={() => onRestoreStreak(u)} disabled={disabled}>
              <Flame className="h-4 w-4 mr-1" /> Restore
            </Button>
          </ActionRow>

          {!u.is_verified && (
            <ActionRow
              icon={<Mail className="h-5 w-5" />}
              title="Resend verification"
              desc="This user hasn't verified their email. Send the link again."
            >
              <Button variant="outline" size="sm" onClick={() => onVerifyUser(u)} disabled={disabled}>
                <Mail className="h-4 w-4 mr-1" /> Resend
              </Button>
            </ActionRow>
          )}

          <ActionRow
            icon={<Bell className="h-5 w-5" />}
            title="Test push notification"
            desc="Send a test push to this user's device (requires the mobile app)."
          >
            <Button variant="outline" size="sm" onClick={() => onSendTestPush(u)} disabled={disabled}>
              <Bell className="h-4 w-4 mr-1" /> Send
            </Button>
          </ActionRow>

          <ActionRow
            icon={<Send className="h-5 w-5" />}
            title="Test email"
            desc="Send a test email to confirm delivery is working."
          >
            <Button variant="outline" size="sm" onClick={() => onSendTestEmail(u)} disabled={disabled}>
              <Send className="h-4 w-4 mr-1" /> Send
            </Button>
          </ActionRow>

          <ActionRow
            icon={<GitMerge className="h-5 w-5" />}
            title="Merge accounts"
            desc="Move this user's assignments and classes into another account."
          >
            <Button variant="outline" size="sm" onClick={() => onMerge(u)} disabled={disabled || isSelf}>
              <GitMerge className="h-4 w-4 mr-1" /> Merge
            </Button>
          </ActionRow>

          <ActionRow
            icon={<ShieldOff className="h-5 w-5" />}
            title="Reset 2FA"
            desc="Clear their second-factor setup so they can sign in without a code."
          >
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="outline" size="sm" disabled={disabled}>
                  <ShieldOff className="h-4 w-4 mr-1" /> Reset
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
                  <AlertDialogCancel disabled={disabled}>Cancel</AlertDialogCancel>
                  <AlertDialogAction onClick={() => onReset2fa(u)} disabled={disabled}>Reset</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </ActionRow>

          <p className="font-semibold text-lg text-destructive pt-2">Danger Zone</p>

          <div className="rounded-lg border border-destructive/40 bg-card p-4 flex items-center justify-between gap-4">
            <div className="flex items-start gap-3 min-w-0">
              <div className="mt-0.5 text-destructive"><UserCog className="h-5 w-5" /></div>
              <div className="min-w-0">
                <p className="text-sm font-medium">Role</p>
                <p className="text-xs text-muted-foreground">Promote or demote this user.</p>
              </div>
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

          <div className="rounded-lg border border-destructive/40 bg-card p-4 flex items-center justify-between gap-4">
            <div className="flex items-start gap-3 min-w-0">
              <div className="mt-0.5 text-destructive"><Trash2 className="h-5 w-5" /></div>
              <div className="min-w-0">
                <p className="text-sm font-medium">Delete user</p>
                <p className="text-xs text-muted-foreground">
                  Permanently removes this account. This cannot be undone.
                </p>
              </div>
            </div>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="destructive" size="sm" disabled={disabled || isSelf}>
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
                  <AlertDialogCancel disabled={disabled}>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                    onClick={() => onDeleteUser(u)}
                    disabled={disabled}
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