/**
 * Runs node-pg-migrate (up/down). Uses only Node; no tsx required.
 * Usage: node scripts/run-migrate.cjs up | down
 */
require('dotenv/config');
const { spawn } = require('node:child_process');
const path = require('node:path');

const url = process.env.DATABASE_URL || (() => {
  const host = process.env.PG_HOST || '127.0.0.1';
  const port = process.env.PG_PORT || '5432';
  const user = process.env.PG_USER || 'postgres';
  const password = String(process.env.PG_PASSWORD ?? '');
  const database = process.env.PG_DATABASE || 'round';
  return `postgresql://${encodeURIComponent(user)}:${encodeURIComponent(password)}@${host}:${port}/${database}`;
})();
process.env.DATABASE_URL = url;

const action = process.argv[2] || 'up';
const bin = path.join(__dirname, '..', 'node_modules', 'node-pg-migrate', 'bin', 'node-pg-migrate');
const child = spawn(process.execPath, [bin, action], {
  stdio: 'inherit',
  env: process.env,
  cwd: path.join(__dirname, '..'),
});

child.on('exit', (code) => {
  process.exit(code ?? 0);
});
