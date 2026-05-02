#!/usr/bin/env bash
set -euo pipefail

if [ -z "${DEMO_TUNNEL_HOSTNAME:-}" ]; then
  echo "Error: DEMO_TUNNEL_HOSTNAME is not set." >&2
  echo "Export it without https:// prefix, e.g.:" >&2
  echo '  export DEMO_TUNNEL_HOSTNAME=n11-demo.example.com' >&2
  exit 1
fi

# Strip optional https:// prefix
DEMO_TUNNEL_HOSTNAME="${DEMO_TUNNEL_HOSTNAME#https://}"

STATUS=$(curl -fsS -o /tmp/n11-products-tunnel.json -w '%{http_code}' "https://${DEMO_TUNNEL_HOSTNAME}/api/v1/products")

if [ "$STATUS" != "200" ]; then
  echo "DEV-05 tunnel proof FAILED: https://${DEMO_TUNNEL_HOSTNAME}/api/v1/products returned HTTP ${STATUS}" >&2
  exit 1
fi

echo "DEV-05 tunnel proof passed: https://${DEMO_TUNNEL_HOSTNAME}/api/v1/products returned 200"
