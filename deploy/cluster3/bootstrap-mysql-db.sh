#!/usr/bin/env bash
# Unattended MySQL role provisioning on approved Cluster-3 DB VM.
# The orchestration controller streams one 64-digit generated hex secret
# over an explicitly authorized SSH session to this script's standard input.
# Never print that secret, never include it in command-line arguments.
set -euo pipefail
set +x
umask 077

[[ "$(id -u)" == 0 && "$(hostname)" == "sibyl-db-lab" ]] || {
  echo "Database bootstrap refused: unexpected host/user" >&2; exit 2;
}
ip -4 -o addr show | grep -q '172[.]22[.]120[.]242/' || {
  echo "Database bootstrap refused: wrong network interface" >&2; exit 2;
}
IFS= read -r password || {
  echo "An authorized installer must supply a secret on standard input" >&2; exit 2;
}
[[ "$password" =~ ^[a-f0-9]{64}$ ]] || {
  unset password; echo "Invalid generated credential format" >&2; exit 2;
}
export DEBIAN_FRONTEND=noninteractive
if ! command -v mysql >/dev/null 2>&1 || ! command -v mysqld >/dev/null 2>&1; then
  apt-get update -qq
  apt-get install -y --no-install-recommends mysql-server mysql-client
fi
systemctl enable --now mysql
config=/etc/mysql/mysql.conf.d/zz-sibyl-lab.cnf
if [[ ! -f "$config" ]]; then
  cat >"$config" <<'MYSQLCONF'
[mysqld]
bind-address = 172.22.120.242
require_secure_transport = ON
local_infile = OFF
MYSQLCONF
  chmod 0644 "$config"
  systemctl restart mysql
fi
for attempt in $(seq 1 20); do
  if mysqladmin --protocol=socket --user=root ping >/dev/null 2>&1; then break; fi
  sleep 1
done
mysqladmin --protocol=socket --user=root ping >/dev/null
# The secret consists exclusively of random lowercase hex digits, not user SQL.
# stdin is private and the mysql subprocess does not receive it as an argument.
{
  printf "CREATE DATABASE IF NOT EXISTS sibyl CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
  printf "CREATE USER IF NOT EXISTS 'sibyl'@'172.22.120.241' IDENTIFIED BY '%s' REQUIRE SSL;\n" "$password"
  printf "ALTER USER 'sibyl'@'172.22.120.241' IDENTIFIED BY '%s' REQUIRE SSL;\n" "$password"
  printf "GRANT ALL PRIVILEGES ON sibyl.* TO 'sibyl'@'172.22.120.241';\n"
} | mysql --protocol=socket --user=root --batch --silent
unset password

# Preserve SSH management access before enabling deny-by-default.
if command -v ufw >/dev/null; then
  ufw allow from 172.22.100.0/24 to any port 22 proto tcp >/dev/null
  ufw allow from 172.22.120.0/24 to any port 22 proto tcp >/dev/null
  ufw allow from 172.22.120.241 to any port 3306 proto tcp >/dev/null
  ufw default deny incoming >/dev/null
  ufw default allow outgoing >/dev/null
  ufw --force enable >/dev/null
fi

test "$(mysql --protocol=socket --user=root -NBe 'SELECT @@require_secure_transport;')" = 1
test "$(mysql --protocol=socket --user=root -NBe 'SELECT @@bind_address;')" = 172.22.120.242
test "$(mysql --protocol=socket --user=root -NBe \
  "SELECT COUNT(*) FROM mysql.user WHERE User='sibyl' AND Host='172.22.120.241' AND ssl_type <> '';")" = 1
echo "MYSQL_CLUSTER3_DB_READY: restricted account and TLS required"
