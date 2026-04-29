-- V2__init_users_addresses.sql
-- Identity schema DDL: users, roles, user_roles, addresses.
-- Runs against the `identity` schema (Flyway default-schema = identity).
-- NO CREATE SCHEMA -- schema already created by infra/postgres/init.sh as superuser.

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT         NOT NULL UNIQUE,
    password_hash TEXT         NOT NULL,
    full_name     TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_email ON users (email);

CREATE TABLE roles (
    id   INT  PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);
INSERT INTO roles (id, name) VALUES (1, 'ROLE_USER'), (2, 'ROLE_ADMIN');

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INT  NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE addresses (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title          VARCHAR(50)  NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    il             VARCHAR(50)  NOT NULL,
    ilce           VARCHAR(80)  NOT NULL,
    mahalle        VARCHAR(120),
    street_line    VARCHAR(255) NOT NULL,
    postal_code    CHAR(5)      NOT NULL CHECK (postal_code ~ '^\d{5}$'),
    is_default     BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user_id ON addresses (user_id);

-- D-11: partial unique index — at most one is_default=true address per user.
CREATE UNIQUE INDEX idx_addresses_user_default
    ON addresses (user_id) WHERE is_default;
