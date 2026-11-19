-- Tone Marketplace V3 (PostgreSQL-specific): Trigram search index, ON DELETE RESTRICT on financial FKs, TIMESTAMPTZ

CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE products ADD COLUMN IF NOT EXISTS search_text TEXT
    GENERATED ALWAYS AS (
        lower(title || ' ' || coalesce(tags, '') || ' ' || genre || ' ' || target_gear || ' ' || source_gear)
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_products_search_trgm
    ON products USING gin (search_text gin_trgm_ops);

-- Replace ON DELETE CASCADE with ON DELETE RESTRICT on financial child tables
DO $$
DECLARE
    fk_name TEXT;
BEGIN
    SELECT conname INTO fk_name
    FROM pg_constraint
    WHERE conrelid = 'order_items'::regclass AND confrelid = 'orders'::regclass AND contype = 'f'
    LIMIT 1;
    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE order_items DROP CONSTRAINT %I', fk_name);
    END IF;
    ALTER TABLE order_items
        ADD CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

    SELECT conname INTO fk_name
    FROM pg_constraint
    WHERE conrelid = 'payment_splits'::regclass AND confrelid = 'orders'::regclass AND contype = 'f'
    LIMIT 1;
    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE payment_splits DROP CONSTRAINT %I', fk_name);
    END IF;
    ALTER TABLE payment_splits
        ADD CONSTRAINT fk_payment_splits_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;
END $$;

-- Convert financial timestamps to TIMESTAMPTZ assuming existing values are UTC
ALTER TABLE orders
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN paid_at TYPE TIMESTAMPTZ USING paid_at AT TIME ZONE 'UTC',
    ALTER COLUMN pix_expires_at TYPE TIMESTAMPTZ USING pix_expires_at AT TIME ZONE 'UTC';

ALTER TABLE payment_splits
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN settled_at TYPE TIMESTAMPTZ USING settled_at AT TIME ZONE 'UTC',
    ALTER COLUMN available_at TYPE TIMESTAMPTZ USING available_at AT TIME ZONE 'UTC';

ALTER TABLE payouts
    ALTER COLUMN requested_at TYPE TIMESTAMPTZ USING requested_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC';

ALTER TABLE payment_events
    ALTER COLUMN received_at TYPE TIMESTAMPTZ USING received_at AT TIME ZONE 'UTC';

ALTER TABLE order_status_history
    ALTER COLUMN occurred_at TYPE TIMESTAMPTZ USING occurred_at AT TIME ZONE 'UTC';
