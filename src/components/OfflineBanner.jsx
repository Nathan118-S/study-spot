import { Component, useEffect, useState } from 'react';
import { WifiOff } from 'lucide-react';

function isOnline() {
  return typeof navigator === 'undefined' || navigator.onLine !== false;
}

/** Tracks the browser's online/offline state. */
export function useOnlineStatus() {
  const [online, setOnline] = useState(isOnline);
  useEffect(() => {
    const update = () => setOnline(isOnline());
    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
    };
  }, []);
  return online;
}

/**
 * A small "You're offline" pill pinned to the top of every page while the
 * browser has no connection. The website has no offline cache or write queue
 * (the iOS and Android apps do); requests simply fail until it's back.
 */
export default function OfflineBanner() {
  const online = useOnlineStatus();
  if (online) return null;
  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 top-0 z-[100] flex justify-center px-4"
      style={{ paddingTop: 'max(0.5rem, env(safe-area-inset-top))' }}
    >
      <div className="pointer-events-auto flex items-center gap-2 rounded-full border border-slate-200 bg-white/90 px-3 py-1.5 text-xs font-medium text-slate-700 shadow-sm backdrop-blur dark:border-slate-700 dark:bg-slate-900/90 dark:text-slate-200">
        <WifiOff className="h-3.5 w-3.5" aria-hidden="true" />
        <span>You're offline. Changes won't save until you reconnect.</span>
      </div>
    </div>
  );
}

/**
 * Keeps the app from going blank when something fails to load because the
 * browser is offline (e.g. a page's code chunk that wasn't downloaded yet):
 * shows a short message and reloads once the connection is back. Errors that
 * happen while online are passed on unchanged.
 */
export class OfflineErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
    this.handleOnline = () => window.location.reload();
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidUpdate(_prevProps, prevState) {
    if (this.state.error && !prevState.error && !isOnline()) {
      window.addEventListener('online', this.handleOnline, { once: true });
    }
  }

  componentWillUnmount() {
    window.removeEventListener('online', this.handleOnline);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    if (isOnline()) throw error;
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-2 px-6 text-center">
        <WifiOff className="h-8 w-8 text-slate-400" aria-hidden="true" />
        <p className="font-medium text-slate-700 dark:text-slate-200">You're offline</p>
        <p className="text-sm text-slate-500 dark:text-slate-400">This page will load when you reconnect.</p>
      </div>
    );
  }
}
