import React, { useCallback, useEffect, useState } from 'react';
import { CalendarPlus, Link2, Loader2, MoreHorizontal, Pencil, RefreshCw, Trash2 } from 'lucide-react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { isDemoUser } from '@/lib/demoData';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import CalendarLinkGuide from '@/components/CalendarLinkGuide';
import BlackboardLinkGuide, { LMS_SYSTEMS } from '@/components/BlackboardLinkGuide';
import CalendarFileImport from '@/components/CalendarFileImport';
import { AskTeacherButton } from '@/components/AskTeacher';
import { FileUp } from 'lucide-react';

const LMS_KINDS = ['canvas', 'schoology', 'brightspace'];

export function syncedLine(feed) {
  if (feed.last_error) return null;
  if (!feed.last_synced_at) return 'Not synced yet';
  const mins = Math.max(0, Math.round((Date.now() - new Date(feed.last_synced_at).getTime()) / 60000));
  const ago = mins < 1 ? 'just now' : mins < 60 ? `${mins} min ago` : mins < 1440 ? `${Math.round(mins / 60)} h ago` : `${Math.round(mins / 1440)} d ago`;
  return `Synced ${ago}${feed.last_imported ? ` · ${feed.last_imported} new` : ''}`;
}

// "Can't connect your Google account?" section: the guide to add a calendar
// link, and the list of links already added.
export default function CalendarLinks({ compact = false, kind = 'google' }) {
  const blackboard = kind === 'blackboard';
  // 'lms': Canvas, Schoology and Brightspace links together.
  const lms = kind === 'lms';
  const [system, setSystem] = useState(kind === 'blackboard' ? 'blackboard' : 'canvas');
  const [fileOpen, setFileOpen] = useState(false);
  const { user } = useAuth();
  const demo = isDemoUser(user);
  const [feeds, setFeeds] = useState([]);
  const [guideOpen, setGuideOpen] = useState(false);
  const [busyId, setBusyId] = useState(null);
  const [renaming, setRenaming] = useState(null);
  const [newName, setNewName] = useState('');
  const [removing, setRemoving] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (demo) return;
    try {
      const res = await api.functions.invoke('icalListFeeds', {});
      setFeeds((res.data?.feeds || []).filter((f) => (lms ? LMS_KINDS.includes(f.kind) : (f.kind || 'google') === kind)));
    } catch {
      /* the section still lets people add a link */
    }
  }, [demo]);

  useEffect(() => {
    load();
    window.addEventListener('studyspot:ical-changed', load);
    return () => window.removeEventListener('studyspot:ical-changed', load);
  }, [load]);

  const run = async (id, fn) => {
    setBusyId(id);
    setError('');
    try {
      await fn();
      await load();
    } catch (e) {
      setError(e.message || 'Something went wrong');
    } finally {
      setBusyId(null);
    }
  };

  const syncAll = () => run('all', () => api.functions.invoke('icalSyncFeeds', {}));
  const setMode = (feed, all) => run(feed.id, () => api.functions.invoke('icalUpdateFeed', { id: feed.id, mode: all ? 'all' : 'auto' }));
  const rename = () => {
    const feed = renaming;
    setRenaming(null);
    run(feed.id, () => api.functions.invoke('icalUpdateFeed', { id: feed.id, label: newName }));
  };
  const remove = (removeAssignments) => {
    const feed = removing;
    setRemoving(null);
    run(feed.id, () => api.functions.invoke('icalRemoveFeed', { id: feed.id, removeAssignments }));
  };

  return (
    <div className="rounded-lg border bg-card p-4 space-y-3">
      <div className="flex items-start gap-3">
        <Link2 className="h-5 w-5 text-[hsl(var(--navy))] shrink-0 mt-0.5" />
        <div className="min-w-0 flex-1">
          <p className="font-medium">{lms ? 'Canvas, Schoology or Brightspace' : blackboard ? 'Can’t connect Blackboard?' : 'Can’t connect your Google account?'}</p>
          <p className="text-sm text-muted-foreground">
            {lms ? 'If your school uses Canvas, Schoology or Brightspace, paste your calendar link from it and Study Spot will import your due dates. It works without your school approving anything.' : blackboard
              ? 'Paste your Blackboard calendar link instead and Study Spot will import your due dates from it.'
              : 'Paste your calendar’s public link instead and Study Spot will import your assignments from it.'}
          </p>
        </div>
      </div>
      <div className="flex flex-wrap gap-2 sm:ml-8">
        {lms ? (
          LMS_KINDS.map((k) => (
            <Button key={k} size="sm" className="rounded-full" onClick={() => { setSystem(k); setGuideOpen(true); }} disabled={demo}>
              <CalendarPlus className="h-4 w-4 mr-1.5" /> {LMS_SYSTEMS[k].name}
            </Button>
          ))
        ) : (
          <Button size="sm" className="rounded-full" onClick={() => setGuideOpen(true)} disabled={demo}>
            <CalendarPlus className="h-4 w-4 mr-1.5" /> {blackboard ? 'Add a Blackboard calendar link' : 'Add a calendar link'}
          </Button>
        )}
        {!blackboard && !lms && (
          <Button size="sm" variant="outline" className="rounded-full" onClick={() => setFileOpen(true)} disabled={demo}>
            <FileUp className="h-4 w-4 mr-1.5" /> Import a calendar file
          </Button>
        )}
        {feeds.length > 0 && (
          <Button size="sm" variant="outline" className="rounded-full" onClick={syncAll} disabled={busyId === 'all'}>
            {busyId === 'all' ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <RefreshCw className="h-4 w-4 mr-1.5" />}
            Sync now
          </Button>
        )}
      </div>

      {!compact && feeds.length > 0 && (
        <ul className="divide-y rounded-xl border sm:ml-8">
          {feeds.map((f) => (
            <li key={f.id} className="flex items-center gap-3 px-3 py-2.5">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium truncate">{f.label}</p>
                {f.last_error ? (
                  <p className="text-xs text-destructive">Couldn’t sync: {f.last_error}</p>
                ) : (
                  <p className="text-xs text-muted-foreground">
                    {lms && `${LMS_SYSTEMS[f.kind]?.name} · `}{syncedLine(f)}
                    {f.mode === 'all' ? ' · every event' : ''}
                  </p>
                )}
              </div>
              {busyId === f.id && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" aria-label={`Options for ${f.label}`}>
                    <MoreHorizontal className="h-5 w-5" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-60">
                  <DropdownMenuItem onSelect={syncAll}><RefreshCw className="h-4 w-4 mr-2" /> Sync now</DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => { setNewName(f.label); setRenaming(f); }}>
                    <Pencil className="h-4 w-4 mr-2" /> Rename
                  </DropdownMenuItem>
                  <DropdownMenuItem onSelect={(e) => { e.preventDefault(); setMode(f, f.mode !== 'all'); }} className="justify-between">
                    Import every event
                    <Switch checked={f.mode === 'all'} className="pointer-events-none" />
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={() => setRemoving(f)} className="text-destructive focus:text-destructive">
                    <Trash2 className="h-4 w-4 mr-2" /> Remove
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </li>
          ))}
        </ul>
      )}
      {error && <p className="text-sm text-destructive sm:ml-8">{error}</p>}

      {!blackboard && !lms && <div className="sm:ml-8"><AskTeacherButton variant="ghost" className="px-2 text-[hsl(var(--navy))]" /></div>}
      {blackboard || lms
        ? <BlackboardLinkGuide key={system} open={guideOpen} onOpenChange={setGuideOpen} system={system} />
        : <CalendarLinkGuide open={guideOpen} onOpenChange={setGuideOpen} />}
      {!blackboard && !lms && <CalendarFileImport open={fileOpen} onOpenChange={setFileOpen} />}

      <Dialog open={!!renaming} onOpenChange={(o) => !o && setRenaming(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Rename calendar</DialogTitle>
            <DialogDescription>Its class in Study Spot is renamed too.</DialogDescription>
          </DialogHeader>
          <Input value={newName} onChange={(e) => setNewName(e.target.value)} autoFocus className="h-11" />
          <DialogFooter>
            <Button variant="outline" onClick={() => setRenaming(null)}>Cancel</Button>
            <Button onClick={rename} disabled={!newName.trim()}>Save</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!removing} onOpenChange={(o) => !o && setRemoving(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Remove {removing?.label}?</DialogTitle>
            <DialogDescription>Study Spot stops checking this calendar.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Button variant="outline" onClick={() => remove(false)}>Remove, keep imported assignments</Button>
            <Button variant="destructive" onClick={() => remove(true)}>Remove and delete its assignments</Button>
            <Button variant="ghost" onClick={() => setRemoving(null)}>Cancel</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
