import React from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { GraduationCap, Calendar, BookOpen, Flame, ShieldCheck, BarChart3, CheckCircle2, ArrowRight, Timer, Bell, Layers, School } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

const features = [
  {
    icon: Calendar,
    title: 'Google Calendar Sync',
    description: 'Assignments auto-import from your Google Calendar events using smart keyword matching.',
  },
  {
    icon: BookOpen,
    title: 'Google Classroom',
    description: 'Pull coursework, due dates, and attachments straight from Classroom — and mark work done on both sides.',
  },
  {
    icon: School,
    title: 'Blackboard Learn',
    description: 'Connect your school\'s Blackboard instance to import courses and gradebook due-dated items.',
  },
  {
    icon: Bell,
    title: 'Smart Reminders',
    description: 'Set custom reminder times before each due date and a do-not-disturb window for quiet hours.',
  },
  {
    icon: Timer,
    title: 'Study Timer',
    description: 'A built-in Pomodoro timer tracks focus and break sessions against your assignments.',
  },
  {
    icon: Flame,
    title: 'Streak Tracking',
    description: 'Stay motivated with completion streaks per class, with alerts when you break a record.',
  },
  {
    icon: CheckCircle2,
    title: 'Grading & Progress',
    description: 'Track scores, subtasks, and per-assignment progress with a custom grading scale.',
  },
  {
    icon: Layers,
    title: 'Templates & Analytics',
    description: 'Reuse assignment templates and visualize your workload and grades over time.',
  },
  {
    icon: ShieldCheck,
    title: 'Secure & Private',
    description: 'Per-user data isolation plus optional two-factor auth (TOTP, email, or passkeys).',
  },
];

const container = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08 } },
};
const item = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } },
};

export default function Landing() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Nav */}
      <header className="sticky top-0 z-30 backdrop-blur bg-background/80 border-b border-border">
        <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <GraduationCap className="h-7 w-7 text-primary" />
            <span className="font-heading font-bold text-lg">Study Spot</span>
          </div>
          <div className="flex items-center gap-2">
            <Button asChild variant="ghost" size="sm">
              <Link to="/login">Sign in</Link>
            </Button>
            <Button asChild size="sm">
              <Link to="/register">Get started</Link>
            </Button>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 -z-10 bg-gradient-to-b from-primary/10 via-background to-background" />
        <div className="max-w-6xl mx-auto px-4 pt-20 pb-24 text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: 'easeOut' }}
          >
            <span className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-4 py-1.5 text-sm text-muted-foreground">
              <Flame className="h-4 w-4 text-primary" />
              Built for students who juggle everything
            </span>
            <h1 className="mt-6 font-heading font-bold text-4xl md:text-6xl tracking-tight">
              All your assignments,
              <br />
              <span className="text-primary">one organized spot.</span>
            </h1>
            <p className="mt-6 max-w-2xl mx-auto text-lg text-muted-foreground">
              Study Spot pulls every assignment from Google Calendar, Google Classroom, and Blackboard into a single
              dashboard — with smart reminders, a study timer, streaks, grading, and analytics.
            </p>
            <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-3">
              <Button asChild size="lg" className="w-full sm:w-auto">
                <Link to="/register">
                  Create your account
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </Button>
              <Button asChild variant="outline" size="lg" className="w-full sm:w-auto">
                <Link to="/login">I already have an account</Link>
              </Button>
            </div>
          </motion.div>
        </div>
      </section>

      {/* Features */}
      <section className="max-w-6xl mx-auto px-4 py-16">
        <motion.div
          variants={container}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, amount: 0.2 }}
          className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3"
        >
          {features.map((f) => (
            <motion.div key={f.title} variants={item}>
              <Card className="h-full hover:shadow-md transition-shadow">
                <CardContent className="p-6">
                  <div className="h-11 w-11 rounded-lg bg-primary/10 flex items-center justify-center mb-4">
                    <f.icon className="h-5 w-5 text-primary" />
                  </div>
                  <h3 className="font-heading font-semibold text-lg">{f.title}</h3>
                  <p className="mt-2 text-sm text-muted-foreground">{f.description}</p>
                </CardContent>
              </Card>
            </motion.div>
          ))}
        </motion.div>
      </section>

      {/* CTA */}
      <section className="max-w-6xl mx-auto px-4 pb-20">
        <div className="rounded-2xl border border-border bg-card p-10 md:p-16 text-center">
          <h2 className="font-heading font-bold text-3xl md:text-4xl">Ready to stop missing deadlines?</h2>
          <p className="mt-4 text-muted-foreground max-w-xl mx-auto">
            Set up your account in under a minute and connect Google to import your assignments automatically.
          </p>
          <Button asChild size="lg" className="mt-8">
            <Link to="/register">
              Get started free
              <ArrowRight className="h-4 w-4" />
            </Link>
          </Button>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-border">
        <div className="max-w-6xl mx-auto px-4 py-8 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <GraduationCap className="h-5 w-5 text-primary" />
            <span className="font-heading font-semibold">Study Spot</span>
          </div>
          <div className="flex items-center gap-4">
            <Link to="/privacy" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
              Privacy Policy
            </Link>
            <p className="text-sm text-muted-foreground">© {new Date().getFullYear()} Study Spot</p>
          </div>
        </div>
      </footer>
    </div>
  );
}