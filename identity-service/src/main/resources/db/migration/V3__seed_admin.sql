-- V3__seed_admin.sql
-- Seeds one admin user via Flyway placeholders (D-06).
-- Env vars ADMIN_SEED_EMAIL + ADMIN_SEED_PASSWORD_HASH must be set in .env.
-- Generate hash: htpasswd -bnBC 10 "" "your-password" | tr -d ':\n'

INSERT INTO users (id, email, password_hash, full_name, created_at)
VALUES (gen_random_uuid(), '${adminSeedEmail}', '${adminSeedPasswordHash}', 'Admin', now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
     JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.email = '${adminSeedEmail}'
ON CONFLICT DO NOTHING;
