#!/bin/bash
# Run on VPS from app dir: /var/www/round.ozzy1986.com
# Usage: ./scripts/deploy-vps.sh   or   bash scripts/deploy-vps.sh
set -e
cd /var/www/round.ozzy1986.com
git fetch origin
git reset --hard origin/main
npm ci
npm run migrate:up
npm run build
if pm2 describe round-api >/dev/null 2>&1; then
  pm2 restart round-api
else
  pm2 start dist/src/server.js --name round-api
  pm2 save
fi
