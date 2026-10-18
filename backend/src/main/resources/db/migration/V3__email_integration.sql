CREATE TABLE mailbox_connection (
    id                      BIGSERIAL PRIMARY KEY,
    provider                VARCHAR(30)  NOT NULL UNIQUE,
    email                   VARCHAR(320) NOT NULL,
    encrypted_refresh_token TEXT         NOT NULL,
    status                  VARCHAR(30)  NOT NULL,
    connected_at            TIMESTAMPTZ  NOT NULL,
    last_sync_at            TIMESTAMPTZ,
    last_sync_status        VARCHAR(30),
    last_sync_summary       TEXT
);

CREATE TABLE email_message (
    id                 BIGSERIAL PRIMARY KEY,
    provider_message_id VARCHAR(255) NOT NULL UNIQUE,
    thread_id          VARCHAR(255),
    from_address       TEXT,
    subject            TEXT,
    received_at        TIMESTAMPTZ NOT NULL,
    snippet            TEXT,
    body_text          TEXT,
    processed          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_message_received_at ON email_message (received_at);
