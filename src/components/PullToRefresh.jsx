import React, { useRef, useState } from 'react';
import { Loader2, RefreshCw } from 'lucide-react';
import { cn } from '@/lib/utils';

const THRESHOLD = 70;

export default function PullToRefresh({ onRefresh, children }) {
  const [pull, setPull] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const startY = useRef(null);

  const onTouchStart = (e) => {
    if (window.scrollY <= 0 && !refreshing) {
      startY.current = e.touches[0].clientY;
    }
  };

  const onTouchMove = (e) => {
    if (startY.current == null || refreshing) return;
    const delta = e.touches[0].clientY - startY.current;
    if (delta > 0) {
      setPull(Math.min(delta * 0.5, THRESHOLD * 1.5));
    }
  };

  const onTouchEnd = async () => {
    if (pull >= THRESHOLD && !refreshing) {
      setRefreshing(true);
      setPull(THRESHOLD);
      try {
        await onRefresh?.();
      } finally {
        setRefreshing(false);
        setPull(0);
      }
    } else {
      setPull(0);
    }
    startY.current = null;
  };

  return (
    <div
      className="relative"
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
    >
      <div
        className="absolute left-1/2 -translate-x-1/2 flex items-center justify-center transition-transform duration-200"
        style={{ top: pull - 36, height: 36, width: 36 }}
      >
        {refreshing ? (
          <Loader2 className="h-6 w-6 animate-spin text-primary" />
        ) : (
          <RefreshCw
            className={cn('h-6 w-6 text-muted-foreground', pull >= THRESHOLD && 'text-primary')}
            style={{ transform: `rotate(${pull * 3}deg)` }}
          />
        )}
      </div>
      <div style={{ transform: `translateY(${pull}px)` }} className="will-change-transform">
        {children}
      </div>
    </div>
  );
}