#!/usr/bin/env bash
# infra/postgres/init.sh — Postgres bootstrap for the n11-clone schema-per-service boundary.
#
# Mounted by docker-compose at /docker-entrypoint-initdb.d/00-init.sh and run ONCE on
# first container boot as the Postgres superuser.
#
# Why .sh and not .sql:
#   Files ending in .sql under /docker-entrypoint-initdb.d/ are passed to `psql` directly
#   with no shell, so ${ENV_VAR} placeholders are NOT interpolated. We need env-var-supplied
#   passwords (D-03: passwords from env vars, never hardcoded), so we use bash + heredoc:
#   bash interpolates the env vars BEFORE psql sees the SQL stream.
#   [VERIFIED: github.com/docker-library/postgres/blob/master/docker-entrypoint.sh README]
#
# Boundary model (D-01..D-04, ARCH-09, ARCH-10):
#   - 1 database `n11`, 10 schemas (one per stateful service)
#   - 1 distinct user per schema, owns its schema, USAGE on `public` (for pgvector types)
#   - server-side `search_path` default so the JDBC URL is uniform across services
#   - explicit REVOKE matrix denying cross-schema USAGE (PITFALLS #11 mitigation)
#
# Schema name note: order-service's schema is `orders` (plural), NOT `order`. `ORDER` is
# a SQL reserved word (PITFALLS #7 / PATTERNS Cross-Cutting #7), so we plural-rename
# everywhere. Env var is therefore ORDERS_DB_PASSWORD, user is orders_user.
#
# Stateless services (no schema, no user, no env var): eureka-server, config-server,
# api-gateway. CONTEXT.md D-01 mentions "13 schemas" but eureka/config/gateway are pure
# compute — they own no relational state. Net schema count is 10.

set -euo pipefail

# Required env vars — fail loudly if any are missing rather than creating users with
# empty passwords. Bash `${VAR:?msg}` exits non-zero if VAR is unset OR empty.
: "${IDENTITY_DB_PASSWORD:?IDENTITY_DB_PASSWORD env var is required}"
: "${PRODUCT_DB_PASSWORD:?PRODUCT_DB_PASSWORD env var is required}"
: "${INVENTORY_DB_PASSWORD:?INVENTORY_DB_PASSWORD env var is required}"
: "${CART_DB_PASSWORD:?CART_DB_PASSWORD env var is required}"
: "${ORDERS_DB_PASSWORD:?ORDERS_DB_PASSWORD env var is required}"
: "${PAYMENT_DB_PASSWORD:?PAYMENT_DB_PASSWORD env var is required}"
: "${NOTIFICATION_DB_PASSWORD:?NOTIFICATION_DB_PASSWORD env var is required}"
: "${SEARCH_DB_PASSWORD:?SEARCH_DB_PASSWORD env var is required}"
: "${AI_DB_PASSWORD:?AI_DB_PASSWORD env var is required}"
: "${MCP_DB_PASSWORD:?MCP_DB_PASSWORD env var is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL

  -- ───────────────────── pgvector ────────────────────────────────────────
  -- Must be created by superuser; only place this happens. (T-01-03)
  CREATE EXTENSION IF NOT EXISTS vector;

  -- ───────────────────── identity ────────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS identity;
  CREATE USER identity_user WITH PASSWORD '${IDENTITY_DB_PASSWORD}';
  ALTER SCHEMA identity OWNER TO identity_user;
  GRANT USAGE ON SCHEMA public TO identity_user;
  ALTER USER identity_user SET search_path = identity, public;

  -- ───────────────────── product ─────────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS product;
  CREATE USER product_user WITH PASSWORD '${PRODUCT_DB_PASSWORD}';
  ALTER SCHEMA product OWNER TO product_user;
  GRANT USAGE ON SCHEMA public TO product_user;
  ALTER USER product_user SET search_path = product, public;

  -- ───────────────────── inventory ───────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS inventory;
  CREATE USER inventory_user WITH PASSWORD '${INVENTORY_DB_PASSWORD}';
  ALTER SCHEMA inventory OWNER TO inventory_user;
  GRANT USAGE ON SCHEMA public TO inventory_user;
  ALTER USER inventory_user SET search_path = inventory, public;

  -- ───────────────────── cart ────────────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS cart;
  CREATE USER cart_user WITH PASSWORD '${CART_DB_PASSWORD}';
  ALTER SCHEMA cart OWNER TO cart_user;
  GRANT USAGE ON SCHEMA public TO cart_user;
  ALTER USER cart_user SET search_path = cart, public;

  -- ───────────────────── orders (plural -- 'order' is SQL reserved) ─────
  CREATE SCHEMA IF NOT EXISTS orders;
  CREATE USER orders_user WITH PASSWORD '${ORDERS_DB_PASSWORD}';
  ALTER SCHEMA orders OWNER TO orders_user;
  GRANT USAGE ON SCHEMA public TO orders_user;
  ALTER USER orders_user SET search_path = orders, public;

  -- ───────────────────── payment ─────────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS payment;
  CREATE USER payment_user WITH PASSWORD '${PAYMENT_DB_PASSWORD}';
  ALTER SCHEMA payment OWNER TO payment_user;
  GRANT USAGE ON SCHEMA public TO payment_user;
  ALTER USER payment_user SET search_path = payment, public;

  -- ───────────────────── notification ────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS notification;
  CREATE USER notification_user WITH PASSWORD '${NOTIFICATION_DB_PASSWORD}';
  ALTER SCHEMA notification OWNER TO notification_user;
  GRANT USAGE ON SCHEMA public TO notification_user;
  ALTER USER notification_user SET search_path = notification, public;

  -- ───────────────────── search ──────────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS search;
  CREATE USER search_user WITH PASSWORD '${SEARCH_DB_PASSWORD}';
  ALTER SCHEMA search OWNER TO search_user;
  GRANT USAGE ON SCHEMA public TO search_user;
  ALTER USER search_user SET search_path = search, public;

  -- ───────────────────── ai ──────────────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS ai;
  CREATE USER ai_user WITH PASSWORD '${AI_DB_PASSWORD}';
  ALTER SCHEMA ai OWNER TO ai_user;
  GRANT USAGE ON SCHEMA public TO ai_user;
  ALTER USER ai_user SET search_path = ai, public;

  -- ───────────────────── mcp ─────────────────────────────────────────────
  CREATE SCHEMA IF NOT EXISTS mcp;
  CREATE USER mcp_user WITH PASSWORD '${MCP_DB_PASSWORD}';
  ALTER SCHEMA mcp OWNER TO mcp_user;
  GRANT USAGE ON SCHEMA public TO mcp_user;
  ALTER USER mcp_user SET search_path = mcp, public;

  -- ───────────────────── deny matrix (PITFALLS #11) ──────────────────────
  -- New users have no privileges by default — these REVOKEs are explicit
  -- documentation of the boundary. Idempotent: REVOKE on a never-granted
  -- privilege is a no-op. Plan 01-08 (CrossSchemaDenyTest) verifies at runtime.
  REVOKE USAGE ON SCHEMA identity     FROM product_user, inventory_user, cart_user, orders_user, payment_user, notification_user, search_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA product      FROM identity_user, inventory_user, cart_user, orders_user, payment_user, notification_user, search_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA inventory    FROM identity_user, product_user,   cart_user, orders_user, payment_user, notification_user, search_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA cart         FROM identity_user, product_user,   inventory_user, orders_user, payment_user, notification_user, search_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA orders       FROM identity_user, product_user,   inventory_user, cart_user, payment_user, notification_user, search_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA payment      FROM identity_user, product_user,   inventory_user, cart_user, orders_user, notification_user, search_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA notification FROM identity_user, product_user,   inventory_user, cart_user, orders_user, payment_user, search_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA search       FROM identity_user, product_user,   inventory_user, cart_user, orders_user, payment_user, notification_user, ai_user, mcp_user;
  REVOKE USAGE ON SCHEMA ai           FROM identity_user, product_user,   inventory_user, cart_user, orders_user, payment_user, notification_user, search_user, mcp_user;
  REVOKE USAGE ON SCHEMA mcp          FROM identity_user, product_user,   inventory_user, cart_user, orders_user, payment_user, notification_user, search_user, ai_user;

EOSQL

echo "init.sh: bootstrapped 10 schemas + 10 users + deny matrix + pgvector extension"
