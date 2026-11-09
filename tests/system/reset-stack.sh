#!/bin/sh
# Recreates ONLY the isolated stack from scratch (project cbstest); the dev stack is never touched.
set -e
cd "$(dirname "$0")/../.."
docker compose -p cbstest -f tests/system/docker-compose.test.yml down -v --remove-orphans
docker compose -p cbstest -f tests/system/docker-compose.test.yml up -d --no-build
i=0
# The web port opens before the data initializer creates the admin account.
until docker logs cbstest-backend 2>&1 | grep -q 'Started CoreTraining'; do
  i=$((i+1)); [ $i -gt 90 ] && { echo backend not ready; exit 1; }; sleep 2
done
echo stack ready
