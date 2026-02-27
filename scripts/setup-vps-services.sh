#!/bin/bash
# One-time VPS setup: install Redis and PgBouncer for scaling.
# Run as root on the VPS: bash scripts/setup-vps-services.sh
set -e
apt-get update
apt-get install -y redis-server pgbouncer
systemctl enable redis-server
systemctl enable pgbouncer
systemctl start redis-server
# PgBouncer: copy deploy/pgbouncer.ini.sample to /etc/pgbouncer/pgbouncer.ini,
# create userlist.txt for auth, then: systemctl start pgbouncer
# On VPS set PG_PORT=6432 in .env to use PgBouncer.
echo "Redis is running. Configure PgBouncer (see deploy/pgbouncer.ini.sample) then: systemctl start pgbouncer"
