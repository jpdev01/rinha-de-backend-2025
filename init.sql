CREATE UNLOGGED TABLE payments (
    correlation_id UUID PRIMARY KEY,
    amount DECIMAL NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at_default BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX payments_requested_at_processed_default ON payments (requested_at, processed_at_default);

CREATE UNLOGGED TABLE payment_processors_state (
       default_healthy BOOLEAN NOT NULL DEFAULT false,
       default_min_response_time_ms INTEGER NOT NULL DEFAULT 0,
       fallback_healthy BOOLEAN NOT NULL DEFAULT false,
       fallback_min_response_time_ms INTEGER NOT NULL DEFAULT 0,
       default_last_checked BIGINT NOT NULL DEFAULT 0,
       fallback_last_checked BIGINT NOT NULL DEFAULT 0
);

select * from payments;