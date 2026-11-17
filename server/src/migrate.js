import 'dotenv/config';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { pool } from './db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const sql = readFileSync(path.join(__dirname, '..', 'schema.sql'), 'utf8');
  // The database container may still be starting; retry for up to a minute.
  for (let attempt = 1; ; attempt++) {
    try {
      await pool.query('SELECT 1');
      break;
    } catch (err) {
      if (attempt >= 30) throw err;
      console.log(`Waiting for the database (${err.code || err.message})…`);
      await sleep(2000);
    }
  }
  await pool.query(sql);
  console.log('Database schema applied.');
  await pool.end();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
