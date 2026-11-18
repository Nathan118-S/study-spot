-- Study Spot database schema (PostgreSQL)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT UNIQUE NOT NULL,
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

  -- Free-form app-specific settings/state (stored in the "data" JSONB bag):
  -- onboarding_completed, welcome_style, leaderboard_enabled, reminders_enabled,
  -- reminder_lead_hours, dnd_enabled/start/end, sync_completion_to_classroom,
  -- grading_scale, last_sync, blackboard_*, etc.
  data JSONB NOT NULL DEFAULT '{}',

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Accounts use usernames; email was removed. Older databases had an email
-- column: it becomes the username, so existing accounts sign in with their
-- email address as their username (they can change it in Settings).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'email') THEN
    ALTER TABLE users RENAME COLUMN email TO username;
    -- Email verification no longer exists; every remaining account is usable.
    UPDATE users SET is_verified = true WHERE is_verified = false;
    -- Email two-factor no longer exists.
    UPDATE users SET twofa_methods = array_remove(twofa_methods, 'email') WHERE 'email' = ANY(twofa_methods);
    UPDATE users SET twofa_method = twofa_methods[1] WHERE twofa_method = 'email';
    UPDATE users SET twofa_enabled = false, twofa_method = NULL WHERE twofa_enabled AND cardinality(twofa_methods) = 0
      AND totp_secret IS NULL AND passkey_cred_id IS NULL;
    UPDATE users SET data = data - 'email_2fa_code' - 'email_2fa_expires' - 'email_reminders';
  END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower ON users (lower(username));
DROP TABLE IF EXISTS email_otps;
DROP TABLE IF EXISTS password_resets;

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


-- Class meeting times (for the iOS "class starts soon" Live Activity).
-- schedule: [{ "day": 0-6 (Sunday = 0), "start": "HH:MM", "end": "HH:MM" }]
ALTER TABLE classes ADD COLUMN IF NOT EXISTS subject TEXT;
ALTER TABLE classes ADD COLUMN IF NOT EXISTS room TEXT;
ALTER TABLE classes ADD COLUMN IF NOT EXISTS schedule JSONB NOT NULL DEFAULT '[]';

-- iOS push-to-start tokens (ActivityKit), one per device install.
CREATE TABLE IF NOT EXISTS device_push_tokens (
  token TEXT PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL DEFAULT 'live-activity-start',
  environment TEXT NOT NULL DEFAULT 'production' CHECK (environment IN ('production', 'development')),
  bundle_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON device_push_tokens(user_id);

-- One row per class meeting a "class starts soon" Live Activity was sent
-- for; update_token is the started activity's own push token, used to end it.
CREATE TABLE IF NOT EXISTS class_activity_sends (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
  starts_at TIMESTAMPTZ NOT NULL,
  update_token TEXT,
  environment TEXT,
  bundle_id TEXT,
  sent_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  PRIMARY KEY (user_id, class_id, starts_at)
);

-- Google Calendar links pasted by people who can't connect their Google
-- account (the calendar's "Secret address in iCal format"). The URL is a
-- secret, so it's stored encrypted like the integration secrets.
CREATE TABLE IF NOT EXISTS ical_feeds (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  label TEXT,
  url_hint TEXT,
  url_iv BYTEA NOT NULL,
  url_ciphertext BYTEA NOT NULL,
  url_auth_tag BYTEA NOT NULL,
  mode TEXT NOT NULL DEFAULT 'auto' CHECK (mode IN ('auto', 'all')),
  calendar_name TEXT,
  last_synced_at TIMESTAMPTZ,
  last_error TEXT,
  last_imported INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ical_feeds_user ON ical_feeds(user_id);

-- Calendar links from more school systems (Canvas, Schoology, Brightspace).
DO $$
BEGIN
  ALTER TABLE classes DROP CONSTRAINT IF EXISTS classes_source_check;
  ALTER TABLE classes ADD CONSTRAINT classes_source_check
    CHECK (source IN ('manual', 'google_classroom', 'blackboard', 'google_calendar', 'canvas', 'schoology', 'brightspace'));
  ALTER TABLE assignments DROP CONSTRAINT IF EXISTS assignments_source_check;
  ALTER TABLE assignments ADD CONSTRAINT assignments_source_check
    CHECK (source IN ('manual', 'google_calendar', 'google_classroom', 'blackboard', 'canvas', 'schoology', 'brightspace'));
END $$;
