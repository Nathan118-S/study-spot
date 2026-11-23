import React, { useCallback, useEffect, useState } from 'react';
import { Check, Copy, Eye, Loader2, Plus, Share2, Trash2, Users } from 'lucide-react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { isDemoUser } from '@/lib/demoData';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';

function viewedLine(link) {
  if (!link.last_viewed_at) return 'Not opened yet';
  const mins = Math.max(0, Math.round((Date.now() - new Date(link.last_viewed_at).getTime()) / 60000));
  const ago = mins < 1 ? 'just now' : mins < 60 ? `${mins} min ago` : mins < 1440 ? `${Math.round(mins / 60)} h ago` : `${Math.round(mins / 1440)} d ago`;
  return `Last opened ${ago}`;
}

// Settings → Parent view: private, read-only links to a weekly summary for a
// parent or guardian. The full link is shown only once, right after it's made.
export default function GuardianLinks() {
  const { user } = useAuth();
  const demo = isDemoUser(user);
  const [links, setLinks] = useState([]);
  const [max, setMax] = useState(5);
  const [loading, setLoading] = useState(true);
  const [label, setLabel] = useState('');
  const [showGrades, setShowGrades] = useState(false);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState(null); // { id, label, url }
  const [copied, setCopied] = useState(false);
  const [busyId, setBusyId] = useState(null);
  const [revoking, setRevoking] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (demo) return setLoading(false);
    try {
      const res = await api.functions.invoke('guardianListLinks', {});
      setLinks(res.data?.links || []);
      if (res.data?.max) setMax(res.data.max);
    } catch (e) {
      setError(e.message || 'Couldn’t load your parent links');
    } finally {
      setLoading(false);
    }
  }, [demo]);

  useEffect(() => { load(); }, [load]);

  const create = async (e) => {
    e.preventDefault();
    if (!label.trim()) return;
    setCreating(true);
    setError('');
    try {
      const res = await api.functions.invoke('guardianCreateLink', { label: label.trim(), show_grades: showGrades });
      setCreated({ id: res.data.link.id, label: res.data.link.label, url: res.data.url });
      setCopied(false);
      setLabel('');
      setShowGrades(false);
      await load();
    } catch (err) {
      setError(err.message || 'Couldn’t create the link');
    } finally {
      setCreating(false);
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(created.url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* the link is still selectable */
    }
  };

  const share = async () => {
    try {
      await navigator.share({ title: 'Study Spot', text: 'Here’s my weekly Study Spot summary:', url: created.url });
    } catch {
      /* cancelled */
    }
  };

  const toggleGrades = async (link, on) => {
    setBusyId(link.id);
    setError('');
    setLinks((ls) => ls.map((l) => (l.id === link.id ? { ...l, show_grades: on } : l)));
    try {
      await api.functions.invoke('guardianUpdateLink', { id: link.id, show_grades: on });
    } catch (err) {
      setError(err.message || 'Couldn’t update the link');
      await load();
    } finally {
      setBusyId(null);
    }
  };

  const revoke = async () => {
    const link = revoking;
    setRevoking(null);
    setBusyId(link.id);
    setError('');
    try {
      await api.functions.invoke('guardianRevokeLink', { id: link.id });
      if (created?.id === link.id) setCreated(null);
      await load();
    } catch (err) {
      setError(err.message || 'Couldn’t turn the link off');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-3">
      <div className="rounded-lg border bg-card p-4 flex items-start gap-3">
        <Users className="h-5 w-5 text-[hsl(var(--navy))] shrink-0 mt-0.5" />
        <div className="min-w-0 text-sm text-muted-foreground space-y-1">
          <p>
            Give a parent or guardian a private, read-only link to your week: what’s due and overdue, what you finished
            in the last 7 days, your streak and your study time. Grades are only included if you turn them on.
          </p>
          <p>They don’t need an account, and you can turn a link off at any time.</p>
        </div>
      </div>

      {created && (
        <div className="rounded-lg border border-primary/50 bg-primary/5 p-4 space-y-2">
          <p className="text-sm font-medium">Link for {created.label} is ready</p>
          <p className="text-xs text-muted-foreground">Copy it now: for privacy, Study Spot can’t show it again.</p>
          <Input readOnly value={created.url} onFocus={(e) => e.target.select()} className="h-10 font-mono text-xs" />
          <div className="flex flex-wrap gap-2">
            <Button size="sm" className="rounded-full" onClick={copy}>
              {copied ? <Check className="h-4 w-4 mr-1.5" /> : <Copy className="h-4 w-4 mr-1.5" />}
              {copied ? 'Copied' : 'Copy link'}
            </Button>
            {typeof navigator !== 'undefined' && navigator.share && (
              <Button size="sm" variant="outline" className="rounded-full" onClick={share}>
                <Share2 className="h-4 w-4 mr-1.5" /> Share
              </Button>
            )}
          </div>
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-4"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
      ) : links.length > 0 && (
        <ul className="divide-y rounded-xl border bg-card">
          {links.map((l) => (
            <li key={l.id} className="px-3 py-2.5 space-y-2">
              <div className="flex items-center gap-3">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium truncate">{l.label}</p>
                  <p className="text-xs text-muted-foreground flex items-center gap-1">
                    <Eye className="h-3 w-3" /> {viewedLine(l)}
                  </p>
                </div>
                {busyId === l.id && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
                <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive" onClick={() => setRevoking(l)}>
                  <Trash2 className="h-4 w-4 mr-1" /> Turn off
                </Button>
              </div>
              <label className="flex items-center justify-between gap-3 text-sm">
                <span className="text-muted-foreground">Show grades</span>
                <Switch checked={l.show_grades} onCheckedChange={(on) => toggleGrades(l, on)} disabled={busyId === l.id} />
              </label>
            </li>
          ))}
        </ul>
      )}

      {!loading && links.length < max && (
        <form onSubmit={create} className="rounded-lg border bg-card p-4 space-y-3">
          <p className="text-sm font-medium">New parent link</p>
          <Input
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="Who is it for? e.g. Mom"
            maxLength={40}
            className="h-10"
            disabled={demo}
          />
          <label className="flex items-center justify-between gap-3 text-sm">
            <span>
              Show grades
              <span className="block text-xs text-muted-foreground">Your grade in each class, from scored assignments.</span>
            </span>
            <Switch checked={showGrades} onCheckedChange={setShowGrades} disabled={demo} />
          </label>
          <Button type="submit" size="sm" className="rounded-full" disabled={demo || creating || !label.trim()}>
            {creating ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <Plus className="h-4 w-4 mr-1.5" />}
            Create link
          </Button>
          {demo && <p className="text-xs text-muted-foreground">Parent links aren’t available in the demo.</p>}
        </form>
      )}
      {!loading && links.length >= max && (
        <p className="text-xs text-muted-foreground">You have {max} parent links, the most you can have. Turn one off to add another.</p>
      )}
      {error && <p className="text-sm text-destructive">{error}</p>}

      <AlertDialog open={!!revoking} onOpenChange={(o) => !o && setRevoking(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Turn off {revoking?.label}’s link?</AlertDialogTitle>
            <AlertDialogDescription>
              The link stops working right away. You can make a new one later.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={revoke} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Turn off
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
