-- Study Spot database schema (PostgreSQL)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT,
  name TEXT,
  full_name TEXT,
  role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('admin', 'user', 'demo', 'disabled')),
  disabled_reason TEXT,
  is_verified BOOLEAN NOT NULL DEFAULT false,

  totp_secret TEXT,
  twofa_enabled BOOLEAN NOT NULL DEFAULT false,
  twofa_method TEXT,
  twofa_methods TEXT[] NOT NULL DEFAULT '{}',
  webauthn_challenge TEXT,
  passkey_cred_id TEXT,
  passkey_pub_key JSONB,
  passkey_alg INTEGER,
  passkey_counter INTEGER NOT NULL DEFAULT 0,

  -- Free-form app-specific settings/state (mirrors the Base44 "data" bag):
  -- onboarding_completed, welcome_style, leaderboard_enabled, reminders_enabled,
  -- reminder_lead_hours, dnd_enabled/start/end, sync_completion_to_classroom,
  -- grading_scale, last_sync, blackboard_*, email_2fa_code/expires, etc.
  data JSONB NOT NULL DEFAULT '{}',

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS email_otps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  purpose TEXT NOT NULL DEFAULT 'register',
  pending_password_hash TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS password_resets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS classes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  color TEXT NOT NULL DEFAULT '#6366f1',
  teacher_name TEXT,
  source TEXT NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'google_classroom', 'blackboard', 'google_calendar')),
  external_id TEXT,
  best_streak INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_classes_owner ON classes(created_by_id);

CREATE TABLE IF NOT EXISTS assignments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  class_id UUID,
  class_name TEXT,
  due_date TIMESTAMPTZ,
  priority TEXT NOT NULL DEFAULT 'medium' CHECK (priority IN ('low', 'medium', 'high')),
  type TEXT NOT NULL DEFAULT 'homework' CHECK (type IN ('homework', 'project', 'quiz', 'test', 'reading', 'other')),
  notes TEXT,
  description TEXT,
  source TEXT NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'google_calendar', 'google_classroom', 'blackboard')),
  external_id TEXT,
  completed BOOLEAN NOT NULL DEFAULT false,
  progress NUMERIC NOT NULL DEFAULT 0,
  subtasks JSONB NOT NULL DEFAULT '[]',
  points NUMERIC NOT NULL DEFAULT 0,
  score NUMERIC,
  reminder_sent BOOLEAN NOT NULL DEFAULT false,
  reminder_sent_hours NUMERIC[] NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_assignments_owner ON assignments(created_by_id);
CREATE INDEX IF NOT EXISTS idx_assignments_class ON assignments(class_id);

CREATE TABLE IF NOT EXISTS assignment_templates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  title TEXT,
  type TEXT NOT NULL DEFAULT 'homework' CHECK (type IN ('homework', 'project', 'quiz', 'test', 'reading', 'other')),
  priority TEXT NOT NULL DEFAULT 'medium' CHECK (priority IN ('low', 'medium', 'high')),
  points NUMERIC NOT NULL DEFAULT 0,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_templates_owner ON assignment_templates(created_by_id);

CREATE TABLE IF NOT EXISTS study_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  assignment_id UUID,
  assignment_title TEXT,
  class_id UUID,
  class_name TEXT,
  duration_minutes NUMERIC NOT NULL,
  session_type TEXT NOT NULL DEFAULT 'focus' CHECK (session_type IN ('focus', 'break')),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sessions_owner ON study_sessions(created_by_id);

CREATE TABLE IF NOT EXISTS notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  content TEXT,
  type TEXT NOT NULL DEFAULT 'info' CHECK (type IN ('streak', 'test', 'reminder', 'info')),
  read BOOLEAN NOT NULL DEFAULT false,
  action_label TEXT,
  action_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);

CREATE TABLE IF NOT EXISTS google_connections (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  connector TEXT NOT NULL CHECK (connector IN ('google_calendar', 'google_classroom')),
  access_token TEXT NOT NULL,
  refresh_token TEXT,
  expires_at TIMESTAMPTZ,
  scope TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, connector)
);

CREATE TABLE IF NOT EXISTS oauth_states (
  state TEXT PRIMARY KEY,
  purpose TEXT NOT NULL,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  return_to TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Encrypted, write-once integration secrets (SMTP, Google, Blackboard
-- credentials). Values are AES-256-GCM encrypted at rest and are never
-- readable through the API once set — only whether a key is set. An admin
-- can delete a row to allow re-entering it.
CREATE TABLE IF NOT EXISTS app_secrets (
  key TEXT PRIMARY KEY,
  iv BYTEA NOT NULL,
  ciphertext BYTEA NOT NULL,
  auth_tag BYTEA NOT NULL,
  set_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

