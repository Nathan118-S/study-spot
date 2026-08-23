import React, { useState, useRef } from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '@/lib/AuthContext';
import { Calendar, LayoutDashboard, BookOpen, Settings as SettingsIcon, Moon, Sun, LogOut, GraduationCap, BarChart3, Flame, ArrowLeft, ShieldCheck, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import NotificationCenter from '@/components/NotificationCenter';
import { isDemoUser } from '@/lib/demoData';
import { useTheme } from '@/lib/theme';

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/classes', label: 'Classes', icon: BookOpen, end: false },
  { to: '/calendar', label: 'Calendar View', icon: Calendar, end: false },
  { to: '/analytics', label: 'Streaks', icon: Flame, end: false },
  { to: '/settings', label: 'Settings', icon: SettingsIcon, end: false },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const { dark, setDarkMode } = useTheme();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [desktopOpen, setDesktopOpen] = useState(true);
  const location = useLocation();
  const navigate = useNavigate();
  const mainRef = useRef(null);
  const primaryRoots = ['/', '/classes', '/calendar', '/analytics', '/settings', '/admin'];
  const showBack = !primaryRoots.includes(location.pathname);
  const bottomNav = navItems.filter((i) => i.to !== '/settings' && i.to !== '/calendar');

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
          'fixed inset-y-0 left-0 z-40 w-64 bg-sidebar border-r border-sidebar-border flex flex-col transition-transform',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
          desktopOpen ? 'md:translate-x-0' : 'md:-translate-x-full'
        )}
      >
        <div className="h-16 flex items-center gap-2 px-6 border-b border-sidebar-border">
          <GraduationCap className="h-7 w-7 text-primary" />
          <span className="font-heading font-bold text-lg flex-1">Study Spot</span>
          <Button
            variant="ghost"
            size="icon"
            className="hidden md:flex h-8 w-8"
            onClick={() => setDesktopOpen(false)}
            aria-label="Hide sidebar"
            title="Hide sidebar"
          >
            <PanelLeftClose className="h-5 w-5" />
          </Button>
        </div>
        <nav className="flex-1 p-4 space-y-1">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                    : 'text-sidebar-foreground hover:bg-sidebar-accent/60'
                )
              }
            >
              <item.icon className="h-5 w-5" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="p-4 border-t border-sidebar-border space-y-3">
          {isDemoUser(user) && (
            <div className="rounded-md border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm font-medium text-emerald-700 dark:text-emerald-300">
              Demo account
            </div>
          )}
          {isAdmin && (
            <NavLink
              to="/admin"
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-destructive/10 text-destructive'
                    : 'text-destructive hover:bg-destructive/10'
                )
              }
            >
              <ShieldCheck className="h-5 w-5" />
              Admin
            </NavLink>
          )}
          <NotificationCenter fullWidth />
          <Button variant="ghost" className="w-full justify-start" onClick={() => setDarkMode(!dark)}>
            {dark ? <Sun className="h-5 w-5 mr-2" /> : <Moon className="h-5 w-5 mr-2" />}
            {dark ? 'Light mode' : 'Dark mode'}
          </Button>
          <div className="flex items-center gap-2 px-2">
            <div className="flex-1 min-w-0">
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

      {!desktopOpen && (
        <Button
          variant="outline"
          size="icon"
          className="hidden md:flex fixed top-3 left-3 z-40 h-9 w-9 bg-background shadow-sm"
          onClick={() => setDesktopOpen(true)}
          aria-label="Show sidebar"
          title="Show sidebar"
        >
          <PanelLeftOpen className="h-5 w-5" />
        </Button>
      )}

      <div className={cn('flex-1 flex flex-col min-w-0', desktopOpen ? 'md:ml-64' : 'md:ml-0')}>
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