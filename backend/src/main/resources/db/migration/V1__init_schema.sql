-- Tone Marketplace Schema (PostgreSQL & H2 PostgreSQL Mode Compatible)

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE creator_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    display_name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    bio TEXT,
    avatar_url VARCHAR(500),
    main_gear VARCHAR(255),
    youtube_url VARCHAR(500),
    instagram_url VARCHAR(500),
    website_url VARCHAR(500),
    portfolio_demo_url VARCHAR(500),
    pix_key VARCHAR(180),
    status VARCHAR(20) NOT NULL,
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL REFERENCES creator_profiles(id),
    title VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    genre VARCHAR(80) NOT NULL,
    target_gear VARCHAR(120) NOT NULL,
    source_gear VARCHAR(120) NOT NULL,
    pickup_type VARCHAR(30) NOT NULL,
    tags VARCHAR(500),
    video_url VARCHAR(500),
    demo_audio_key VARCHAR(500),
    demo_duration_seconds INTEGER DEFAULT 0,
    demo_loudness_lufs NUMERIC(5, 2) DEFAULT -14.00,
    demo_bitrate_kbps INTEGER DEFAULT 96,
    current_version VARCHAR(30) NOT NULL DEFAULT '1.0.0',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    average_rating NUMERIC(3, 2) NOT NULL DEFAULT 0.00,
    review_count INTEGER NOT NULL DEFAULT 0,
    sales_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_products_filters ON products(active, genre, target_gear, source_gear, pickup_type);
CREATE INDEX idx_products_creator ON products(creator_id);

CREATE TABLE product_versions (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    version_number VARCHAR(30) NOT NULL,
    changelog TEXT,
    patch_file_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_product_versions_product ON product_versions(product_id, created_at DESC);

CREATE TABLE bundle_items (
    id UUID PRIMARY KEY,
    bundle_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    included_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT uq_bundle_item UNIQUE (bundle_id, included_product_id)
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    added_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_cart_item UNIQUE (user_id, product_id)
);

CREATE TABLE wishlist_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_wishlist_item UNIQUE (user_id, product_id)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    order_code VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    platform_fee_total NUMERIC(12, 2) NOT NULL,
    creator_net_total NUMERIC(12, 2) NOT NULL,
    gateway_transaction_id VARCHAR(120),
    pix_copy_paste TEXT,
    pix_expires_at TIMESTAMP,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_orders_buyer ON orders(buyer_id, created_at DESC);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    creator_id UUID NOT NULL REFERENCES creator_profiles(id),
    product_title_snapshot VARCHAR(180) NOT NULL,
    purchased_version VARCHAR(30) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    take_rate_percent NUMERIC(5, 2) NOT NULL,
    platform_fee_amount NUMERIC(12, 2) NOT NULL,
    creator_net_amount NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_creator ON order_items(creator_id);

CREATE TABLE payment_splits (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES creator_profiles(id),
    gross_amount NUMERIC(12, 2) NOT NULL,
    platform_fee_amount NUMERIC(12, 2) NOT NULL,
    creator_net_amount NUMERIC(12, 2) NOT NULL,
    take_rate_percent NUMERIC(5, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    gateway_split_reference VARCHAR(120),
    settled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_payment_splits_creator ON payment_splits(creator_id, status);

CREATE TABLE payouts (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL REFERENCES creator_profiles(id),
    amount NUMERIC(12, 2) NOT NULL,
    pix_key VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL,
    comment TEXT,
    verified_purchase BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_review_product_user UNIQUE (product_id, user_id),
    CONSTRAINT chk_review_rating CHECK (rating >= 1 AND rating <= 5)
);

CREATE INDEX idx_reviews_product ON reviews(product_id, created_at DESC);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message TEXT NOT NULL,
    related_product_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications(user_id, is_read, created_at DESC);

CREATE TABLE platform_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO platform_settings (setting_key, setting_value, updated_at)
VALUES ('DEFAULT_TAKE_RATE_PERCENT', '15.00', CURRENT_TIMESTAMP);
