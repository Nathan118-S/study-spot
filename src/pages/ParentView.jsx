import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { AlertTriangle, CalendarClock, CheckCircle2, Clock, Flame, GraduationCap, Link2Off, Loader2 } from 'lucide-react';
import logo from '@/assets/icons/apple-touch-icon.png';

const API_BASE = import.meta.env.VITE_API_URL || '/api';

// Keep the private page out of search engines and other sites' referrer logs.
function usePrivatePageMeta(title) {
  useEffect(() => {
    const added = [
      ['robots', 'noindex, nofollow'],
      ['referrer', 'no-referrer'],
    ].map(([name, content]) => {
      const m = document.createElement('meta');
      m.name = name;
      m.content = content;
      document.head.appendChild(m);
      return m;
    });
    const prevTitle = document.title;
    document.title = title;
    return () => {
      added.forEach((m) => m.remove());
      document.title = prevTitle;
    };
  }, [title]);
}

const dayFmt = (d, tz) => new Date(d).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', timeZone: tz });
const timeFmt = (d, tz) => new Date(d).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit', timeZone: tz });

function studyTime(minutes) {
  if (!minutes) return '0 min';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h ? `${h} h${m ? ` ${m} min` : ''}` : `${m} min`;
}

// The read-only weekly summary a student shares with a parent or guardian
// (Settings → Parent view). Works without signing in.
export default function ParentView() {
  const { token } = useParams();
  const [state, setState] = useState({ status: 'loading' });
  const name = state.data?.student?.first_name;
  usePrivatePageMeta(name ? `${name}’s week · Study Spot` : 'Study Spot');

  useEffect(() => {
    let cancelled = false;
    fetch(`${API_BASE}/public/guardian/${encodeURIComponent(token || '')}`, { referrerPolicy: 'no-referrer', cache: 'no-store' })
      .then(async (r) => {
        const body = await r.json().catch(() => null);
        if (cancelled) return;
        if (r.ok && body) setState({ status: 'ok', data: body });
        else if (r.status === 404) setState({ status: 'off' });
        else setState({ status: 'error', message: body?.error });
      })
      .catch(() => !cancelled && setState({ status: 'error' }));
    return () => { cancelled = true; };
  }, [token]);

  return (
    <div className="min-h-screen text-foreground">
      <header className="sticky top-0 z-30 backdrop-blur bg-background/80 border-b border-border">
        <div className="max-w-3xl mx-auto px-4 h-16 flex items-center gap-3">
          <img src={logo} alt="" className="h-8 w-8 rounded-[22%]" />
          <span className="font-heading font-bold text-lg text-[hsl(var(--navy))]">Study Spot</span>
          <span className="ml-auto text-xs font-medium text-muted-foreground rounded-full bg-secondary px-2.5 py-1">Read-only</span>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-8 md:py-10">
        {state.status === 'loading' && (
          <div className="flex justify-center py-24"><Loader2 className="h-8 w-8 animate-spin text-muted-foreground" /></div>
        )}
        {state.status === 'off' && (
          <Notice
            icon={<Link2Off className="h-7 w-7" />}
            title="This link was turned off"
            text="The student who shared it has turned it off, or it isn’t a valid link. Ask them for a new one if you’d like to keep following along."
          />
        )}
        {state.status === 'error' && (
          <Notice
            icon={<AlertTriangle className="h-7 w-7" />}
            title="Couldn’t load this summary"
            text={state.message || 'Check your connection and try again in a moment.'}
          />
        )}
        {state.status === 'ok' && <Summary data={state.data} />}
      </main>
    </div>
  );
}

function Notice({ icon, title, text }) {
  return (
    <div className="max-w-md mx-auto text-center py-16 space-y-3">
      <div className="mx-auto h-14 w-14 rounded-full bg-secondary text-[hsl(var(--navy))] flex items-center justify-center">{icon}</div>
      <h1 className="font-heading text-2xl font-bold text-[hsl(var(--navy))]">{title}</h1>
      <p className="text-muted-foreground leading-relaxed">{text}</p>
    </div>
  );
}

function Summary({ data }) {
  const tz = data.time_zone;
  const name = data.student?.first_name;
  const who = name ? `${name}’s` : 'Your student’s';
  return (
    <div className="space-y-8">
      <div>
        <p className="text-sm font-semibold tracking-wide text-primary uppercase">Weekly summary</p>
        <h1 className="font-heading text-3xl md:text-4xl font-bold text-[hsl(var(--navy))] mt-1">{who} week</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Shared with {data.label} · Updated {dayFmt(data.generated_at, tz)}, {timeFmt(data.generated_at, tz)}
        </p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Stat icon={<CalendarClock className="h-4 w-4 text-[hsl(var(--navy))]" />} label="Due in 7 days" value={data.upcoming_count} />
        <Stat icon={<AlertTriangle className="h-4 w-4 text-red-500" />} label="Overdue" value={data.overdue_count} warn={data.overdue_count > 0} />
        <Stat icon={<CheckCircle2 className="h-4 w-4 text-emerald-600" />} label="Done in 7 days" value={data.completed_count} />
        <Stat icon={<Clock className="h-4 w-4 text-primary" />} label="Study time this week" value={studyTime(data.study?.minutes_this_week || 0)} />
      </div>

      <Section title="Coming up" subtitle="Due in the next 7 days" empty="Nothing due in the next 7 days.">
        {data.upcoming.map((a, i) => <Row key={i} item={a} right={`${dayFmt(a.due_date, tz)}`} />)}
        {more(data.upcoming.length, data.upcoming_count)}
      </Section>

      <Section title="Overdue" subtitle="Past the due date and not marked done" empty="Nothing overdue. Nice!">
        {data.overdue.map((a, i) => <Row key={i} item={a} right={`Was due ${dayFmt(a.due_date, tz)}`} warn />)}
        {more(data.overdue.length, data.overdue_count)}
      </Section>

      <Section title="Finished" subtitle="Marked done in the last 7 days" empty="Nothing marked done in the last 7 days.">
        {data.completed.map((a, i) => <Row key={i} item={a} right={<CheckCircle2 className="h-4 w-4 text-emerald-600" />} />)}
        {more(data.completed.length, data.completed_count)}
      </Section>

      <Section
        title="Streaks"
        subtitle="Assignments done in a row in each class. One overdue assignment resets it."
        empty="No active streaks right now."
      >
        {data.streak?.classes?.map((s, i) => (
          <Row key={i} item={{ title: s.class_name, class_color: s.class_color }} right={
            <span className="flex items-center gap-1 font-semibold text-[hsl(var(--navy))]">
              <Flame className="h-4 w-4 text-orange-500" /> {s.streak}
            </span>
          } />
        ))}
      </Section>

      {data.show_grades && (
        <Section title="Grades" subtitle="From scored assignments in each class" empty="No graded assignments yet.">
          {(data.grades || []).map((g, i) => (
            <Row key={i} item={{ title: g.class_name, class_color: g.class_color, sub: `${g.graded_count} graded` }} right={
              <span className="flex items-center gap-2">
                <span className="text-sm text-muted-foreground">{g.percent}%</span>
                {g.letter && <span className="min-w-8 text-center rounded-md bg-secondary px-2 py-0.5 font-bold text-[hsl(var(--navy))]">{g.letter}</span>}
              </span>
            } />
          ))}
        </Section>
      )}

      <p className="text-xs text-muted-foreground border-t pt-6 flex items-start gap-2">
        <GraduationCap className="h-4 w-4 shrink-0" />
        {name || 'The student'} shared this private, read-only page from Study Spot. Only people with the link can see it,
        and {name || 'they'} can turn it off at any time.
      </p>
    </div>
  );
}

function Stat({ icon, label, value, warn }) {
  return (
    <div className={`rounded-2xl bg-card ring-1 p-4 ${warn ? 'ring-red-300 dark:ring-red-900' : 'ring-black/5 dark:ring-white/10'}`}>
      <p className="text-xs text-muted-foreground flex items-center gap-1.5">{icon} {label}</p>
      <p className="mt-1 text-2xl font-bold text-[hsl(var(--navy))] dark:text-foreground">{value}</p>
    </div>
  );
}

function Section({ title, subtitle, empty, children }) {
  const items = React.Children.toArray(children).filter(Boolean);
  return (
    <section>
      <h2 className="font-heading text-xl font-bold text-[hsl(var(--navy))] dark:text-foreground">{title}</h2>
      {subtitle && <p className="text-sm text-muted-foreground mb-3">{subtitle}</p>}
      {items.length ? (
        <ul className="rounded-2xl bg-card ring-1 ring-black/5 dark:ring-white/10 divide-y">{items}</ul>
      ) : (
        <p className="rounded-2xl bg-card ring-1 ring-black/5 dark:ring-white/10 px-4 py-3 text-sm text-muted-foreground">{empty}</p>
      )}
    </section>
  );
}

function Row({ item, right, warn }) {
  const sub = item.sub || item.class_name;
  return (
    <li className="flex items-center gap-3 px-4 py-3">
      <span className="h-2.5 w-2.5 rounded-full shrink-0" style={{ background: item.class_color || '#94a3b8' }} />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium truncate">{item.title}</p>
        {sub && <p className="text-xs text-muted-foreground truncate">{sub}</p>}
      </div>
      <div className={`text-sm shrink-0 ${warn ? 'text-red-600 dark:text-red-400 font-medium' : 'text-muted-foreground'}`}>{right}</div>
    </li>
  );
}

function more(shown, total) {
  if (!total || total <= shown) return null;
  return <li className="px-4 py-2.5 text-xs text-muted-foreground">and {total - shown} more</li>;
}
