/**
 * Sets DATABASE_URL from PG_* / getDbConfig and runs node-pg-migrate.
 * Usage: tsx scripts/run-migrate.ts up | down
 */
import 'dotenv/config';
import { getDbConfig } from '../src/db/config.js';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const action = process.argv[2] || 'up';
process.env.DATABASE_URL = getDbConfig().connectionString;

const bin = path.join(__dirname, '..', 'node_modules', 'node-pg-migrate', 'bin', 'node-pg-migrate');
const child = spawn(process.execPath, [bin, action], {
  stdio: 'inherit',
  env: process.env,
  cwd: path.join(__dirname, '..'),
});

child.on('exit', (code) => {
  process.exit(code ?? 0);
});
