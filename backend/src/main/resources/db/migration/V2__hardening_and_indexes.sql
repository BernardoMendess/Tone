-- Tone Marketplace V2: Concurrency locking, idempotency, security hardening, audit & performance indexes
-- Compatible with PostgreSQL 17 and H2 PostgreSQL Mode

-- 1. Optimistic locking, idempotency, and audit columns
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN updated_at TIMESTAMP;
UPDATE orders SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE orders ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(80);

ALTER TABLE payouts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payouts ADD COLUMN idempotency_key VARCHAR(80);

ALTER TABLE creator_profiles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 2. Payout hold period (MED/chargeback protection)
ALTER TABLE payment_splits ADD COLUMN available_at TIMESTAMP;
UPDATE payment_splits SET available_at = settled_at WHERE available_at IS NULL AND settled_at IS NOT NULL;

-- 3. Account security, token revocation & login lockout
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;

-- 4. Refresh tokens (rotatable, revocable, hashed at rest)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- 5. Payment webhook event deduplication & audit
CREATE TABLE payment_events (
    id UUID PRIMARY KEY,
    gateway_event_id VARCHAR(120) NOT NULL,
    order_id UUID NOT NULL REFERENCES orders(id),
    event_type VARCHAR(40) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_payment_events_gateway_event UNIQUE (gateway_event_id)
);
CREATE INDEX idx_payment_events_order ON payment_events(order_id);

-- 6. Order state transition history
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(255),
    actor VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_order_status_history_order ON order_status_history(order_id, occurred_at);

-- 7. Unique & performance indexes
CREATE UNIQUE INDEX uq_orders_gateway_tx ON orders(gateway_transaction_id);
CREATE UNIQUE INDEX uq_orders_buyer_idem ON orders(buyer_id, idempotency_key);
CREATE UNIQUE INDEX uq_payouts_creator_idem ON payouts(creator_id, idempotency_key);

CREATE INDEX idx_orders_status_pix_exp ON orders(status, pix_expires_at);
CREATE INDEX idx_order_items_product ON order_items(product_id, order_id);
CREATE INDEX idx_bundle_items_included ON bundle_items(included_product_id);
CREATE INDEX idx_payouts_creator_status ON payouts(creator_id, status);
CREATE INDEX idx_payment_splits_order ON payment_splits(order_id);
CREATE INDEX idx_payment_splits_creator_avail ON payment_splits(creator_id, status, available_at);
CREATE INDEX idx_products_active_sales ON products(active, sales_count DESC, created_at DESC);
