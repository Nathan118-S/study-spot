// Pulls the latest commit from the current git branch, reinstalls
// dependencies, rebuilds the frontend, then exits so the process supervisor
// (systemd/pm2/Docker restart policy — see README) brings the server back up
// on the new code. Runs server/scripts/deploy.sh as a child process and
// keeps an in-memory log/status the admin UI polls.
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { HttpError } from './httpError.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../../..');
const SCRIPT_PATH = path.join(__dirname, '..', '..', 'scripts', 'deploy.sh');

const MAX_LOG_CHARS = 20000;

const state = {
  status: 'idle', // idle | running | restarting | failed
  startedAt: null,
  finishedAt: null,
  log: '',
  error: null,
};

function appendLog(chunk) {
  state.log += chunk;
  if (state.log.length > MAX_LOG_CHARS) {
    state.log = state.log.slice(state.log.length - MAX_LOG_CHARS);
  }
}

export function getDeployStatus() {
  return { ...state };
}

export function startDeploy() {
  if (state.status === 'running') {
    throw new HttpError(409, 'A deploy is already in progress');
  }

  state.status = 'running';
  state.startedAt = new Date().toISOString();
  state.finishedAt = null;
  state.log = '';
  state.error = null;

  let child;
  try {
    child = spawn('bash', [SCRIPT_PATH], { cwd: REPO_ROOT, env: process.env });
  } catch (err) {
    state.status = 'failed';
    state.finishedAt = new Date().toISOString();
    state.error = err.message;
    appendLog(`Failed to start deploy: ${err.message}\n`);
    throw new HttpError(500, `Failed to start deploy: ${err.message}`);
  }

  child.stdout.on('data', (d) => appendLog(d.toString()));
  child.stderr.on('data', (d) => appendLog(d.toString()));

  child.on('error', (err) => {
    state.status = 'failed';
    state.finishedAt = new Date().toISOString();
    state.error = err.message;
    appendLog(`\nFailed to run deploy script: ${err.message}\n`);
  });

  child.on('close', (code) => {
    state.finishedAt = new Date().toISOString();
    if (code === 0) {
      state.status = 'restarting';
      appendLog('\nDeploy succeeded. Restarting...\n');
      // Give the response to this request (and one status poll) a moment to
      // go out before the process exits — the supervisor restarts it on the
      // freshly pulled code.
      setTimeout(() => process.exit(0), 1500);
    } else {
      state.status = 'failed';
      state.error = `Deploy script exited with code ${code}`;
      appendLog(`\n${state.error}\n`);
    }
  });

  return getDeployStatus();
}
