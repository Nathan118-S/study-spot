import React, { useState, useEffect } from 'react';
import { api } from '@/api/client';
import { Loader2, FileText, Link as LinkIcon, Youtube, ClipboardList, Paperclip, ExternalLink } from 'lucide-react';

const KIND_ICON = { drive: FileText, link: LinkIcon, video: Youtube, form: ClipboardList };

export default function ClassroomAttachments({ assignment }) {
  const [attachments, setAttachments] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const eligible = !!assignment && assignment.source === 'google_classroom' && !!assignment.external_id;

  useEffect(() => {
    if (!eligible) {
      setAttachments(null);
      setError('');
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError('');
    setAttachments(null);
    api.functions
      .invoke('getClassroomAttachments', { assignment_id: assignment.id })
      .then((res) => {
        if (!cancelled) setAttachments(res.data?.attachments || []);
      })
      .catch((e) => {
        if (!cancelled) setError(e?.response?.data?.error || 'Unable to load attachments');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [assignment?.id, eligible]);

  if (!eligible) return null;

  if (loading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading attachments…
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border p-3">
        <p className="text-sm font-medium flex items-center gap-2">
          <Paperclip className="h-4 w-4" /> Attached files
        </p>
        <p className="text-xs text-muted-foreground mt-1">{error}</p>
      </div>
    );
  }

  if (!attachments || attachments.length === 0) return null;

  return (
    <div className="rounded-lg border p-3 space-y-2">
      <p className="text-sm font-medium flex items-center gap-2">
        <Paperclip className="h-4 w-4" /> Attached files
      </p>
      <ul className="space-y-1.5">
        {attachments.map((a, i) => {
          const Icon = KIND_ICON[a.kind] || FileText;
          return (
            <li key={i}>
              <a
                href={a.url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 rounded-md border bg-muted/40 px-2.5 py-2 text-sm hover:bg-muted transition-colors"
              >
                <Icon className="h-4 w-4 shrink-0 text-muted-foreground" />
                <span className="flex-1 truncate">{a.title}</span>
                <ExternalLink className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
              </a>
            </li>
          );
        })}
      </ul>
    </div>
  );
}