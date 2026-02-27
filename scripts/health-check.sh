#!/bin/bash
# Cron: curl health endpoint. Exit non-zero on failure for alerting.
# Example: */5 * * * * /var/www/round.ozzy1986.com/scripts/health-check.sh
BASE="${HEALTH_URL:-https://round.ozzy1986.com}"
if curl -sf --max-time 10 "$BASE/health" >/dev/null; then
  exit 0
fi
exit 1
