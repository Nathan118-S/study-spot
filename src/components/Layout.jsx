import React, { useState, useRef } from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '@/lib/AuthContext';
import { Calendar, LayoutDashboard, BookOpen, Settings as SettingsIcon, LogOut, GraduationCap, BarChart3, Flame, ArrowLeft, ShieldCheck, PanelLeftClose, Trello } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import NotificationCenter from '@/components/NotificationCenter';
import { isDemoUser } from '@/lib/demoData';
import { useTheme } from '@/lib/theme';
import { useIsMobile } from '@/hooks/use-mobile';

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/board', label: 'Board', icon: Trello, end: false },
  { to: '/classes', label: 'Classes', icon: BookOpen, end: false },
  { to: '/calendar', label: 'Calendar View', icon: Calendar, end: false },
  { to: '/analytics', label: 'Streaks', icon: Flame, end: false },
];

export default function Layout() {
  const { user, logout } = useAuth();
  useTheme();
  const isMobile = useIsMobile();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [desktopOpen, setDesktopOpen] = useState(true);
  const location = useLocation();
  const navigate = useNavigate();
  const mainRef = useRef(null);
  const primaryRoots = ['/', '/board', '/classes', '/calendar', '/analytics', '/settings', '/admin'];
  const showBack = !primaryRoots.includes(location.pathname);
  const bottomNav = navItems.filter((i) => i.to !== '/calendar');

  const scrollToMainTop = () => {
    const el = mainRef.current;
    if (el && el.scrollHeight > el.clientHeight) {
      el.scrollTo({ top: 0, behavior: 'smooth' });
    } else {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };
  const items = navItems;
  const isAdmin = user?.role === 'admin';

  return (
    <div className="min-h-screen bg-background text-foreground flex">
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 w-64 bg-sidebar border-r border-sidebar-border flex flex-col transition-all duration-200',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
          'md:translate-x-0',
          desktopOpen ? 'md:w-64' : 'md:w-16'
        )}
      >
        <div className={cn('h-16 flex items-center gap-2 px-6 border-b border-sidebar-border', desktopOpen ? 'md:flex' : 'md:hidden')}>
          <GraduationCap className="h-7 w-7 text-primary shrink-0" />
          <span className="font-heading font-bold text-lg flex-1">Study Spot</span>
          <Button
            variant="ghost"
            size="icon"
            className="hidden md:flex h-8 w-8"
            onClick={() => setDesktopOpen(false)}
            aria-label="Collapse sidebar"
            title="Collapse sidebar"
          >
            <PanelLeftClose className="h-5 w-5" />
          </Button>
        </div>
        <button
          type="button"
          onClick={() => setDesktopOpen(true)}
          className={cn('h-16 hidden w-full items-center justify-center border-b border-sidebar-border', desktopOpen ? 'md:hidden' : 'md:flex')}
          aria-label="Expand sidebar"
          title="Expand sidebar"
        >
          <GraduationCap className="h-7 w-7 text-primary" />
        </button>
        <nav className={cn('flex-1 p-4 space-y-1', !desktopOpen && 'md:px-2')}>
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setMobileOpen(false)}
              title={!desktopOpen ? item.label : undefined}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  !desktopOpen && 'md:justify-center md:px-0',
                  isActive
                    ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                    : 'text-sidebar-foreground hover:bg-sidebar-accent/60'
                )
              }
            >
              <item.icon className="h-5 w-5 shrink-0" />
              <span className={cn(!desktopOpen && 'md:hidden')}>{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className={cn('p-4 border-t border-sidebar-border space-y-3', !desktopOpen && 'md:p-2')}>
          {isDemoUser(user) && (
            <div className={cn('rounded-md border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm font-medium text-emerald-700 dark:text-emerald-300', !desktopOpen && 'md:hidden')}>
              Demo account
            </div>
          )}
          {isAdmin && (
            <NavLink
              to="/admin"
              onClick={() => setMobileOpen(false)}
              title={!desktopOpen ? 'Admin' : undefined}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  !desktopOpen && 'md:justify-center md:px-0',
                  isActive
                    ? 'bg-destructive/10 text-destructive'
                    : 'text-destructive hover:bg-destructive/10'
                )
              }
            >
              <ShieldCheck className="h-5 w-5 shrink-0" />
              <span className={cn(!desktopOpen && 'md:hidden')}>Admin</span>
            </NavLink>
          )}
          <NotificationCenter fullWidth={isMobile || desktopOpen} />
          <NavLink
            to="/settings"
            onClick={() => setMobileOpen(false)}
            title={!desktopOpen ? 'Settings' : undefined}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                !desktopOpen && 'md:justify-center md:px-0',
                isActive
                  ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                  : 'text-sidebar-foreground hover:bg-sidebar-accent/60'
              )
            }
          >
            <SettingsIcon className="h-5 w-5 shrink-0" />
            <span className={cn(!desktopOpen && 'md:hidden')}>Settings</span>
          </NavLink>
          <div className={cn('flex items-center gap-2 px-2', !desktopOpen && 'md:justify-center md:px-0')}>
            <div className={cn('flex-1 min-w-0', !desktopOpen && 'md:hidden')}>
              <p className="text-sm font-medium truncate">{user?.name || user?.full_name || user?.email || 'User'}</p>
              <p className="text-xs text-muted-foreground truncate">{user?.email}</p>
            </div>
            <Button variant="ghost" size="icon" onClick={() => logout()} title="Sign out">
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-30 bg-black/40 md:hidden" onClick={() => setMobileOpen(false)} />
      )}

      <div className={cn('flex-1 flex flex-col min-w-0', desktopOpen ? 'md:ml-64' : 'md:ml-16')}>
        <header
          className="md:hidden sticky top-0 z-20 bg-background border-b border-border"
          style={{ paddingTop: 'env(safe-area-inset-top)' }}
        >
          <div className="h-16 flex items-center px-2 gap-1">
            {showBack ? (
              <Button variant="ghost" size="icon" onClick={() => navigate(-1)} aria-label="Go back">
                <ArrowLeft className="h-5 w-5" />
              </Button>
            ) : (
              <Button variant="ghost" size="icon" onClick={() => setMobileOpen(true)} aria-label="Open menu">
                <span className="space-y-1 block">
                  <span className="block w-5 h-0.5 bg-current" />
                  <span className="block w-5 h-0.5 bg-current" />
                  <span className="block w-5 h-0.5 bg-current" />
                </span>
              </Button>
            )}
            <span className="font-heading font-bold text-lg ml-1">Study Spot</span>
            <div className="ml-auto">
              <NotificationCenter />
            </div>
          </div>
        </header>
        <main ref={mainRef} className="flex-1 p-4 pb-24 md:p-8 md:pb-8 max-w-7xl w-full mx-auto overscroll-none">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.2, ease: 'easeOut' }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>

        <nav
          className={cn(
            'md:hidden fixed bottom-0 left-0 right-0 z-40 bg-sidebar border-t border-sidebar-border',
            mobileOpen && 'hidden'
          )}
          style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
        >
          <div className="flex">
            {bottomNav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={() => {
                  if (location.pathname === item.to) scrollToMainTop();
                }}
                className={({ isActive }) =>
                  cn(
                    'flex-1 flex flex-col items-center gap-1 py-2 text-xs',
                    isActive ? 'text-primary' : 'text-muted-foreground'
                  )
                }
              >
                <item.icon className="h-5 w-5" />
                {item.label}
              </NavLink>
            ))}
          </div>
        </nav>
      </div>
    </div>
  );
}