#!/bin/bash
# Run on VPS from app dir: /var/www/round.ozzy1986.com (must be a git clone)
# Usage: bash scripts/deploy-vps.sh
# Requires .env with JWT_SECRET (min 32 chars). Optional: BOT_SECRET for Telegram bot /auth/telegram.
# SSL: ensure certbot renew runs (e.g. cron: 0 0 * * * certbot renew --quiet --deploy-hook "systemctl reload nginx").
set -e
APP_DIR="${APP_DIR:-/var/www/round.ozzy1986.com}"
cd "$APP_DIR"
node -e "
require('dotenv').config();
if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32) {
  console.error('Error: JWT_SECRET must be set in .env and at least 32 characters.');
  process.exit(1);
}
" || exit 1
git fetch origin
git reset --hard origin/main
npm ci
npm run migrate:up
npm run build
if pm2 describe round-api >/dev/null 2>&1; then
  pm2 restart round-api --update-env -i max
else
  pm2 start dist/src/server.js --name round-api -i max
fi
if pm2 describe round-bot >/dev/null 2>&1; then
  pm2 restart round-bot --update-env
else
  pm2 start dist/src/bot/run.js --name round-bot
fi
pm2 save
