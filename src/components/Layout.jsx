import React, { useState, useRef } from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '@/lib/AuthContext';
import {
  Calendar, LayoutGrid, BookOpen, Settings as SettingsIcon, LogOut, GraduationCap, Flame,
  ChevronLeft, ShieldCheck, PanelLeftClose, TrendingUp, CircleUserRound, X,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import NotificationCenter from '@/components/NotificationCenter';
import Onboarding from '@/components/Onboarding';
import { isDemoUser } from '@/lib/demoData';
import { useTheme } from '@/lib/theme';
import { useIsMobile } from '@/hooks/use-mobile';

// Same tabs, in the same order, as the iOS app.
const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutGrid, end: true },
  { to: '/classes', label: 'Classes', icon: BookOpen, end: false },
  { to: '/calendar', label: 'Calendar', icon: Calendar, end: false },
  { to: '/analytics', label: 'Streaks', icon: Flame, end: false },
  { to: '/insights', label: 'Insights', icon: TrendingUp, end: false },
];

const primaryRoots = ['/', '/classes', '/calendar', '/analytics', '/insights', '/settings', '/admin'];

function SidebarLink({ to, end, icon: Icon, label, collapsed, danger, onClick }) {
  return (
    <NavLink
      to={to}
      end={end}
      onClick={onClick}
      title={collapsed ? label : undefined}
      className={({ isActive }) =>
        cn(
          'flex h-11 items-center gap-3 rounded-[10px] px-3 text-[17px] transition-colors',
          collapsed && 'md:justify-center md:px-0',
          isActive
            ? danger
              ? 'bg-destructive text-destructive-foreground'
              : 'bg-primary text-primary-foreground'
            : 'text-[hsl(var(--navy))] hover:bg-sidebar-accent'
        )
      }
    >
      {({ isActive }) => (
        <>
          <Icon className={cn('h-[22px] w-[22px] shrink-0', !isActive && (danger ? 'text-destructive' : 'text-[hsl(var(--navy))]'))} />
          <span className={cn('truncate', collapsed && 'md:hidden')}>{label}</span>
        </>
      )}
    </NavLink>
  );
}

export default function Layout() {
  const { user, logout } = useAuth();
  useTheme();
  const isMobile = useIsMobile();
  const [accountOpen, setAccountOpen] = useState(false);
  const [desktopOpen, setDesktopOpen] = useState(true);
  const location = useLocation();
  const navigate = useNavigate();
  const mainRef = useRef(null);
  const showBack = !primaryRoots.includes(location.pathname);
  const collapsed = !desktopOpen;

  const scrollToTop = () => {
    const el = mainRef.current;
    if (el && el.scrollHeight > el.clientHeight) el.scrollTo({ top: 0, behavior: 'smooth' });
    else window.scrollTo({ top: 0, behavior: 'smooth' });
  };
  const isAdmin = user?.role === 'admin';
  const onboardingDone = (user?.onboarding_completed ?? user?.data?.onboarding_completed) === true;
  const showOnboarding = !!user && !isDemoUser(user) && !onboardingDone;
  const closeAccount = () => setAccountOpen(false);
  const displayName = user?.name || user?.full_name || user?.username || 'User';

  return (
    <div className="min-h-screen text-foreground flex">
      {showOnboarding && <Onboarding />}

      {/* Sidebar: iPadOS style on desktop; the account sheet on phones. */}
      <aside
        className={cn(
          'fixed z-50 flex flex-col bg-sidebar transition-all duration-300 md:bg-sidebar/55 md:backdrop-blur-2xl md:backdrop-saturate-150',
          // Phones: a sheet from the bottom with account actions.
          'inset-x-0 bottom-0 max-h-[85dvh] rounded-t-[14px] shadow-2xl',
          accountOpen ? 'translate-y-0' : 'translate-y-full',
          // Desktop: a full-height sidebar.
          'md:inset-y-0 md:left-0 md:right-auto md:max-h-none md:translate-y-0 md:rounded-none md:shadow-none md:border-r md:border-sidebar-border',
          desktopOpen ? 'md:w-72' : 'md:w-[72px]'
        )}
        style={isMobile ? { paddingBottom: 'calc(env(safe-area-inset-bottom) + 12px)' } : undefined}
      >
        <div className="mx-auto mt-2 h-[5px] w-9 rounded-full bg-muted-foreground/30 md:hidden" />
        <div className={cn('flex items-center gap-2 px-5 pt-4 pb-2 md:pt-6', collapsed && 'md:justify-center md:px-0')}>
          <button
            type="button"
            onClick={() => collapsed && setDesktopOpen(true)}
            className={cn('flex items-center gap-2 min-w-0', !collapsed && 'cursor-default')}
            aria-label={collapsed ? 'Expand sidebar' : undefined}
          >
            <GraduationCap className="h-7 w-7 shrink-0 text-primary" />
            <span className={cn('font-heading text-[22px] font-bold truncate', collapsed && 'md:hidden')}>Study Spot</span>
          </button>
          <Button
            variant="ghost"
            size="icon"
            className={cn('ml-auto hidden md:flex', collapsed && 'md:hidden')}
            onClick={() => setDesktopOpen(false)}
            aria-label="Collapse sidebar"
            title="Collapse sidebar"
          >
            <PanelLeftClose />
          </Button>
          <Button variant="ghost" size="icon" className="ml-auto md:hidden" onClick={closeAccount} aria-label="Close">
            <X />
          </Button>
        </div>

        <nav className={cn('hidden md:block flex-1 space-y-1 px-3 py-2', collapsed && 'md:px-2')}>
          {navItems.map((item) => (
            <SidebarLink key={item.to} {...item} collapsed={collapsed} />
          ))}
        </nav>

        <div className={cn('space-y-1 px-3 py-3 overflow-y-auto', collapsed && 'md:px-2')}>
          {isDemoUser(user) && (
            <div className={cn('rounded-[10px] bg-success/15 px-3 py-2 text-sm font-medium text-success', collapsed && 'md:hidden')}>
              Demo account
            </div>
          )}
          {isAdmin && (
            <SidebarLink to="/admin" icon={ShieldCheck} label="Admin" collapsed={collapsed} danger onClick={closeAccount} />
          )}
          <div className="hidden md:block">
            <NotificationCenter fullWidth={!collapsed} />
          </div>
          <SidebarLink to="/settings" icon={SettingsIcon} label="Settings" collapsed={collapsed} onClick={closeAccount} />
          <div className={cn('mt-2 flex items-center gap-3 rounded-[10px] bg-card px-3 py-2.5', collapsed && 'md:justify-center md:bg-transparent md:px-0')}>
            <CircleUserRound className={cn('h-8 w-8 shrink-0 text-muted-foreground', collapsed && 'md:hidden')} />
            <div className={cn('flex-1 min-w-0', collapsed && 'md:hidden')}>
              <p className="text-[15px] font-medium truncate">{displayName}</p>
              {user?.username && <p className="text-[13px] text-muted-foreground truncate">@{user.username}</p>}
            </div>
            <Button variant="ghost" size="icon" onClick={() => logout()} title="Sign out" aria-label="Sign out">
              <LogOut />
            </Button>
          </div>
        </div>
      </aside>

      {accountOpen && <div className="fixed inset-0 z-40 bg-black/40 md:hidden" onClick={closeAccount} />}

      <div className={cn('flex-1 flex flex-col min-w-0', desktopOpen ? 'md:ml-72' : 'md:ml-[72px]')}>
        {/* Phones: translucent navigation bar. */}
        <header
          className="md:hidden sticky top-0 z-30 ios-bar border-b border-border/60"
          style={{ paddingTop: 'env(safe-area-inset-top)' }}
        >
          <div className="h-11 flex items-center px-2">
            {showBack ? (
              <button
                type="button"
                onClick={() => navigate(-1)}
                className="flex items-center text-primary text-[17px] pr-2"
                aria-label="Go back"
              >
                <ChevronLeft className="h-7 w-7 -mr-0.5" strokeWidth={2.2} />
                Back
              </button>
            ) : (
              <span className="pl-2 font-heading text-[17px] font-semibold">Study Spot</span>
            )}
            <div className="ml-auto flex items-center">
              <NotificationCenter />
              <Button variant="ghost" size="icon" onClick={() => setAccountOpen(true)} aria-label="Account">
                <CircleUserRound className="!size-6" />
              </Button>
            </div>
          </div>
        </header>

        <main ref={mainRef} className="flex-1 w-full max-w-5xl mx-auto px-4 pt-3 pb-28 md:px-8 md:pt-8 md:pb-10 overscroll-none">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.2, ease: 'easeOut' }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>

        {/* Phones: translucent tab bar. */}
        <nav
          className="md:hidden fixed bottom-0 left-0 right-0 z-30 ios-bar border-t border-border/60"
          style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
        >
          <div className="flex h-[50px] pt-1">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={() => {
                  if (location.pathname === item.to) scrollToTop();
                }}
                className={({ isActive }) =>
                  cn(
                    'flex-1 flex flex-col items-center gap-0.5 text-[10px] font-medium',
                    isActive ? 'text-primary' : 'text-muted-foreground'
                  )
                }
              >
                <item.icon className="h-6 w-6" />
                {item.label}
              </NavLink>
            ))}
          </div>
        </nav>
      </div>
    </div>
  );
}
