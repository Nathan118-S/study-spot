import React, { useState } from 'react';
import { api } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Download, Loader2 } from 'lucide-react';
import { downloadExcelXml } from '@/lib/exportExcel';

export default function DataExport() {
  const [busy, setBusy] = useState(false);
  const [count, setCount] = useState(null);

  const exportData = async () => {
    setBusy(true);
    try {
      const [assignments, classes] = await Promise.all([
        api.entities.Assignment.list('-due_date', 2000),
        api.entities.Class.list(),
      ]);
      const classMap = {};
      classes.forEach((c) => {
        classMap[c.id] = c.name;
      });

      const assignmentRows = assignments.map((a) => ({
        title: a.title,
        class: classMap[a.class_id] || a.class_name || '',
        type: a.type,
        priority: a.priority,
        due_date: a.due_date ? new Date(a.due_date).toLocaleString() : '',
        completed: a.completed ? 'Yes' : 'No',
        progress: a.progress ?? 0,
        points: a.points ?? 0,
        score: a.score ?? '',
        source: a.source,
        notes: a.notes || '',
      }));

      const classRows = classes.map((c) => ({
        name: c.name,
        teacher: c.teacher_name || '',
        color: c.color || '',
        source: c.source,
      }));

      downloadExcelXml(
        [
          {
            name: 'Assignments',
            columns: [
              { key: 'title', label: 'Title' },
              { key: 'class', label: 'Class' },
              { key: 'type', label: 'Type' },
              { key: 'priority', label: 'Priority' },
              { key: 'due_date', label: 'Due date' },
              { key: 'completed', label: 'Completed' },
              { key: 'progress', label: 'Progress' },
              { key: 'points', label: 'Points' },
              { key: 'score', label: 'Score' },
              { key: 'source', label: 'Source' },
              { key: 'notes', label: 'Notes' },
            ],
            rows: assignmentRows,
          },
          {
            name: 'Classes',
            columns: [
              { key: 'name', label: 'Name' },
              { key: 'teacher', label: 'Teacher' },
              { key: 'color', label: 'Color' },
              { key: 'source', label: 'Source' },
            ],
            rows: classRows,
          },
        ],
        'study-spot-export.xls'
      );

      setCount({ assignments: assignmentRows.length, classes: classRows.length });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rounded-lg border bg-card p-4 flex items-center justify-between gap-4">
      <div className="min-w-0">
        <p className="text-sm font-medium">Export your data</p>
        <p className="text-xs text-muted-foreground">
          Download all your assignments and classes as an Excel spreadsheet.
        </p>
        {count && (
          <p className="text-xs text-muted-foreground mt-1">
            Last export: {count.assignments} assignments, {count.classes} classes.
          </p>
        )}
      </div>
      <Button variant="outline" size="sm" onClick={exportData} disabled={busy} className="shrink-0">
        {busy ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Download className="h-4 w-4 mr-1" />}
        Export
      </Button>
    </div>
  );
}