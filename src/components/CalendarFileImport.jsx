import React, { useRef, useState } from 'react';
import { ExternalLink, FileUp, Loader2 } from 'lucide-react';
import { api } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';

const MAX_BYTES = 10 * 1024 * 1024;

function toBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
    reader.onerror = () => reject(new Error('That file couldn’t be read'));
    reader.readAsDataURL(file);
  });
}

// One-time import of a calendar file, for when calendar links are turned off
// too: Google Calendar's export (.zip of .ics files) or any .ics file.
export default function CalendarFileImport({ open, onOpenChange }) {
  const inputRef = useRef(null);
  const [everything, setEverything] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const choose = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError('');
    setResult(null);
    if (file.size > MAX_BYTES) {
      setError('That file is too large (10 MB max).');
      return;
    }
    setBusy(true);
    try {
      const data = await toBase64(file);
      const res = await api.functions.invoke('icalImportFile', { filename: file.name, data, mode: everything ? 'all' : 'auto' });
      setResult(res.data);
      window.dispatchEvent(new Event('studyspot:data-changed'));
    } catch (err) {
      setError(err.message || 'Import failed');
    } finally {
      setBusy(false);
    }
  };

  const names = (result?.calendars || []).filter((c) => c.imported > 0).map((c) => c.name);

  return (
    <Dialog open={open} onOpenChange={(o) => { onOpenChange(o); if (!o) { setResult(null); setError(''); } }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Import a calendar file</DialogTitle>
          <DialogDescription>
            For when calendar links are turned off too. It’s a one-time import: new assignments won’t appear
            automatically, so import again later to pick them up.
          </DialogDescription>
        </DialogHeader>
        <ol className="space-y-3 text-[15px] leading-relaxed">
          <li className="flex gap-3">
            <span className="h-7 w-7 shrink-0 rounded-full bg-primary text-primary-foreground text-sm font-bold flex items-center justify-center">1</span>
            <div className="space-y-2">
              <p>On a computer, open calendar.google.com → ⚙ <strong>Settings</strong> → <strong>Import &amp; export</strong> → <strong>Export</strong>. Google downloads a .zip file.</p>
              <Button asChild variant="outline" size="sm" className="rounded-full">
                <a href="https://calendar.google.com/calendar/r/settings/export" target="_blank" rel="noreferrer">
                  <ExternalLink className="h-4 w-4 mr-1.5" /> Open Google Calendar settings
                </a>
              </Button>
            </div>
          </li>
          <li className="flex gap-3">
            <span className="h-7 w-7 shrink-0 rounded-full bg-primary text-primary-foreground text-sm font-bold flex items-center justify-center">2</span>
            <p>Choose that .zip file here (or any .ics calendar file).</p>
          </li>
        </ol>
        <label className="flex items-center justify-between gap-3 rounded-xl border p-3 text-sm">
          <span>
            <span className="block font-medium">Import every event</span>
            <span className="text-muted-foreground text-xs">Off: only events that look like assignments.</span>
          </span>
          <Switch checked={everything} onCheckedChange={setEverything} />
        </label>
        <input ref={inputRef} type="file" accept=".ics,.zip,text/calendar,application/zip" className="hidden" onChange={choose} />
        <Button className="rounded-full" onClick={() => inputRef.current?.click()} disabled={busy}>
          {busy ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <FileUp className="h-4 w-4 mr-1.5" />}
          {busy ? 'Importing…' : 'Choose file…'}
        </Button>
        {error && <p className="text-sm text-destructive">{error}</p>}
        {result && (
          <p className="text-sm font-medium">
            {result.imported > 0
              ? `Imported ${result.imported} assignment${result.imported === 1 ? '' : 's'} from ${names.join(', ')}.`
              : 'No new assignments found.'}
          </p>
        )}
      </DialogContent>
    </Dialog>
  );
}
