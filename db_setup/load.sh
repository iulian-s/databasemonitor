#!/bin/sh

# Use environment variables if provided, otherwise default to Docker settings
DB_HOST=${PGHOST:-"postgres"}
DB_USER=${PGUSER:-"monitor_user"}
DB_NAME=${PGDATABASE:-"monitor_db"}
export PGPASSWORD=${PGPASSWORD:-"monitor_password"}

echo "Waiting for PostgreSQL at $DB_HOST to start..."
until psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c '\q' 2>/dev/null; do
  sleep 2
done

echo "Database is up. Starting load generation..."

while true; do
  # 1. Quick query
  psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c "SELECT COUNT(*) FROM users;" > /dev/null 2>&1

  # 2. Slow query using pg_sleep (takes 3s)
  psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c "SELECT pg_sleep(3), count(*) FROM logs WHERE action = 'action_5';" > /dev/null 2>&1 &

  # 3. Slow query using unindexed sequential scan and sort
  psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c "
    SELECT u.username, COUNT(l.id)
    FROM users u
    JOIN logs l ON u.id = l.user_id
    GROUP BY u.username
    ORDER BY COUNT(l.id) DESC
    LIMIT 10;
  " > /dev/null 2>&1 &

  # 4. Random fast reads to increase cache hit ratio
  for i in $(seq 1 50); do
    # Busy loop of selects using an inline random generator for ash compatibility
    RND=$(awk 'BEGIN {srand(); print int(rand()*100000)+1}')
    psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c "SELECT * FROM users WHERE id = $RND;" > /dev/null 2>&1
  done

  # Wait before next cycle
  sleep 5
done
