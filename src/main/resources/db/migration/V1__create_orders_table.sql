-- Flash sale orders table.
-- idempotency_key prevents duplicate rows when Kafka redelivers events.

CREATE TABLE IF NOT EXISTS orders (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL,
    product_id      VARCHAR(64)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    idempotency_key VARCHAR(36)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id    ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_product_id ON orders (product_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at DESC);
