CREATE TABLE job_application (
    id           BIGSERIAL PRIMARY KEY,
    company      VARCHAR(200)  NOT NULL,
    job_title    VARCHAR(200)  NOT NULL,
    job_url      VARCHAR(1000),
    date_applied DATE,
    status       VARCHAR(30)   NOT NULL DEFAULT 'SAVED',
    notes        TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_application_status  ON job_application (status);
CREATE INDEX idx_job_application_company ON job_application (lower(company));

CREATE TABLE application_status_history (
    id                BIGSERIAL PRIMARY KEY,
    application_id    BIGINT       NOT NULL REFERENCES job_application (id) ON DELETE CASCADE,
    old_status        VARCHAR(30),
    new_status        VARCHAR(30)  NOT NULL,
    source            VARCHAR(30)  NOT NULL DEFAULT 'MANUAL',
    source_message_id VARCHAR(255),
    changed_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_status_history_application ON application_status_history (application_id);
