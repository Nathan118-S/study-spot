import React from 'react';
import { Info } from 'lucide-react';

// Shown before connecting Google or Blackboard: many schools restrict which
// third-party apps can access school accounts.
export default function SchoolAccessNote({ service = 'school' }) {
  return (
    <div className="flex items-start gap-2 rounded-lg bg-secondary/70 px-3 py-2.5 text-xs text-secondary-foreground">
      <Info className="h-4 w-4 shrink-0 mt-px" />
      <p>
        Some schools block connecting third-party apps like Study Spot to {service} accounts. If the connection is
        blocked or fails, ask your school&apos;s IT team, or add your assignments manually.
      </p>
    </div>
  );
}
