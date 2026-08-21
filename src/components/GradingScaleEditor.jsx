import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2, Plus, Trash2, RotateCcw } from 'lucide-react';
import { DEFAULT_GRADING_SCALE } from '@/lib/grading';

export default function GradingScaleEditor({ initialScale }) {
  const [rows, setRows] = useState(
    initialScale?.length ? initialScale : DEFAULT_GRADING_SCALE
  );
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setRows(initialScale?.length ? initialScale : DEFAULT_GRADING_SCALE);
  }, [initialScale]);

  const update = (i, field, value) =>
    setRows((prev) =>
      prev.map((r, idx) =>
        idx === i ? { ...r, [field]: field === 'min' ? Number(value) : value } : r
      )
    );

  const addRow = () => setRows((prev) => [...prev, { letter: '', min: 0 }]);
  const removeRow = (i) => setRows((prev) => prev.filter((_, idx) => idx !== i));
  const reset = () => setRows(DEFAULT_GRADING_SCALE);

  const save = async () => {
    setSaving(true);
    try {
      const sorted = [...rows]
        .filter((r) => r.letter && r.letter.trim())
        .sort((a, b) => b.min - a.min);
      await base44.auth.updateMe({ grading_scale: sorted });
      setRows(sorted);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-lg border bg-card p-4 space-y-3">
      <div className="space-y-2">
        {rows.map((r, i) => (
          <div key={i} className="flex items-center gap-2">
            <Input
              value={r.letter}
              onChange={(e) => update(i, 'letter', e.target.value)}
              placeholder="Letter"
              className="w-24"
              maxLength={3}
            />
            <span className="text-sm text-muted-foreground">≥</span>
            <Input
              type="number"
              min={0}
              max={100}
              value={r.min}
              onChange={(e) => update(i, 'min', e.target.value)}
              className="w-24"
            />
            <span className="text-sm text-muted-foreground">%</span>
            <Button variant="ghost" size="icon" onClick={() => removeRow(i)} title="Remove">
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
      </div>
      <div className="flex items-center gap-2 pt-1">
        <Button size="sm" onClick={save} disabled={saving}>
          {saving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
          Save scale
        </Button>
        <Button size="sm" variant="outline" onClick={addRow}>
          <Plus className="h-4 w-4 mr-1" /> Add row
        </Button>
        <Button size="sm" variant="ghost" onClick={reset}>
          <RotateCcw className="h-4 w-4 mr-1" /> Reset to default
        </Button>
      </div>
    </div>
  );
}