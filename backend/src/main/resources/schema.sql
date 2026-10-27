CREATE TABLE IF NOT EXISTS payments (
    id                        UUID                     NOT NULL PRIMARY KEY,
    amount                    DECIMAL(19, 2)           NOT NULL,
    currency                  VARCHAR(3)               NOT NULL,
    customer_reference        VARCHAR(30)              NOT NULL,
    status                    VARCHAR(20)              NOT NULL,
    provider_payment_id       VARCHAR(64)              NOT NULL,
    provider_status           VARCHAR(64),
    last_callback_occurred_at TIMESTAMP WITH TIME ZONE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_payments_provider_payment_id UNIQUE (provider_payment_id),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN'))
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key UUID                     NOT NULL PRIMARY KEY,
    request_data    VARCHAR(128)             NOT NULL,
    payment_id      UUID                     NOT NULL,
    CONSTRAINT fk_idempotency_keys_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);
