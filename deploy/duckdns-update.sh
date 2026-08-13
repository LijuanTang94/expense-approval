#!/usr/bin/env bash
# Keeps a DuckDNS subdomain pointed at this VM's current public IP.
#
# The VM's public IP is ephemeral, so a bare-IP URL breaks if the instance is ever stopped and
# restarted with a different address. Run this on a timer and the hostname follows the machine —
# calling DuckDNS with an empty ip= makes it use the requester's source address, which is exactly
# this VM's current public IP.
#
# Reads DUCKDNS_DOMAIN and DUCKDNS_TOKEN from the .env next to this script (gitignored).
set -euo pipefail
cd "$(dirname "$0")"

# shellcheck disable=SC1091
set -a; . ./.env; set +a

: "${DUCKDNS_DOMAIN:?DUCKDNS_DOMAIN not set in deploy/.env}"
: "${DUCKDNS_TOKEN:?DUCKDNS_TOKEN not set in deploy/.env}"

response=$(curl -fsS \
  "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=")

# DuckDNS answers with the literal string "OK" or "KO" — not an HTTP error code — so the body has
# to be checked explicitly.
if [ "$response" = "OK" ]; then
  echo "$(date -Is) duckdns ${DUCKDNS_DOMAIN}.duckdns.org -> $(curl -fsS ifconfig.me) OK"
else
  echo "$(date -Is) duckdns update FAILED (response: ${response})" >&2
  exit 1
fi
