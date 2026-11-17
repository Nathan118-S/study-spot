import React, { useState } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useToast } from '@/components/ui/use-toast';
import { Loader2, Check, AtSign } from 'lucide-react';

export default function AccountName() {
  const { user, checkUserAuth } = useAuth();
  const { toast } = useToast();
  const current = user?.name || user?.full_name || '';
  const [name, setName] = useState(current);
  const [saving, setSaving] = useState(false);

  // Accounts created before usernames existed have their old email address
  // as the username, so let people pick a new one here.
  const currentUsername = user?.username || '';
  const [username, setUsername] = useState(currentUsername);
  const [savingUsername, setSavingUsername] = useState(false);
  const [usernameError, setUsernameError] = useState('');

  const save = async () => {
    setSaving(true);
    try {
      await api.auth.updateMe({ name: name.trim() });
      await checkUserAuth();
      toast({ title: 'Name saved' });
    } catch (e) {
      toast({ title: 'Failed to save', description: e.message, variant: 'destructive' });
    } finally {
      setSaving(false);
    }
  };

  const saveUsername = async (e) => {
    e.preventDefault();
    setUsernameError('');
    setSavingUsername(true);
    try {
      const updated = await api.auth.updateMe({ username: username.trim() });
      if (updated?.username) setUsername(updated.username);
      await checkUserAuth();
      toast({ title: 'Username saved', description: 'Use it the next time you log in.' });
    } catch (err) {
      setUsernameError(err.message || 'Could not change your username');
    } finally {
      setSavingUsername(false);
    }
  };

  const usernameUnchanged = username.trim().toLowerCase() === currentUsername.toLowerCase();

  return (
    <div className="space-y-3">
      <div className="rounded-lg border bg-card p-4 space-y-3">
        <div>
          <p className="text-sm font-medium">Name</p>
          <p className="text-xs text-muted-foreground">Shown across the app.</p>
        </div>
        <div className="flex gap-2">
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Your name"
            className="flex-1"
            maxLength={80}
          />
          <Button onClick={save} disabled={saving || name.trim() === current.trim()}>
            {saving ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Check className="h-4 w-4 mr-1" />}
            Save
          </Button>
        </div>
      </div>

      <form onSubmit={saveUsername} className="rounded-lg border bg-card p-4 space-y-3">
        <div>
          <p className="text-sm font-medium">Username</p>
          <p className="text-xs text-muted-foreground">
            You log in with this. 3–30 characters: letters, numbers, dots, dashes and underscores.
          </p>
        </div>
        <div className="flex gap-2">
          <div className="relative flex-1">
            <AtSign className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" aria-hidden="true" />
            <Input
              value={username}
              onChange={(e) => {
                setUsername(e.target.value.toLowerCase());
                setUsernameError('');
              }}
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              placeholder="your-username"
              className="pl-9"
              maxLength={254}
              aria-label="Username"
              aria-invalid={!!usernameError}
            />
          </div>
          <Button type="submit" disabled={savingUsername || !username.trim() || usernameUnchanged}>
            {savingUsername ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Check className="h-4 w-4 mr-1" />}
            Save
          </Button>
        </div>
        {usernameError && <p className="text-sm text-destructive">{usernameError}</p>}
      </form>
    </div>
  );
}
