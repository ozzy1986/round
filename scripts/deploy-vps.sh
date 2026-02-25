#!/bin/bash
# Run on VPS from app dir: /var/www/round.ozzy1986.com (must be a git clone)
# Usage: bash scripts/deploy-vps.sh
set -e
APP_DIR="${APP_DIR:-/var/www/round.ozzy1986.com}"
cd "$APP_DIR"
git fetch origin
git reset --hard origin/main
npm ci
npm run migrate:up
npm run build
if pm2 describe round-api >/dev/null 2>&1; then
  pm2 restart round-api --update-env
else
  pm2 start dist/src/server.js --name round-api
fi
if pm2 describe round-bot >/dev/null 2>&1; then
  pm2 restart round-bot --update-env
else
  pm2 start dist/src/bot/run.js --name round-bot
fi
pm2 save
