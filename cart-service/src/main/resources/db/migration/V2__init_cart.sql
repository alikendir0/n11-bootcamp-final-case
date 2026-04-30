-- V2__init_cart.sql
-- Cart schema for cart-service per Phase 5 D-11 (one-cart-per-user) + D-02 (composite PK on
-- (user_id, product_id)) + CD-03 (NUMERIC(10,2) precision matches product-service.price_gross).
-- NO outbox table — cart-service is consumer-only in v1 (D-07: cart cleared by order.confirmed
-- event; no cart.checked_out event published in v1).

CREATE TABLE carts (
    user_id    UUID         PRIMARY KEY,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    user_id              UUID            NOT NULL,
    product_id           UUID            NOT NULL,
    qty                  INTEGER         NOT NULL CHECK (qty > 0 AND qty <= 99),
    unit_price_snapshot  NUMERIC(10, 2)  NOT NULL CHECK (unit_price_snapshot >= 0),
    name_snapshot        TEXT            NOT NULL,
    image_url_snapshot   TEXT            NULL,
    added_at             TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES carts(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_cart_items_user_added ON cart_items (user_id, added_at DESC);
