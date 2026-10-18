ALTER TABLE email_message
    ADD COLUMN category VARCHAR(40),
    ADD COLUMN extracted_company VARCHAR(200),
    ADD COLUMN extracted_job_title VARCHAR(200),
    ADD COLUMN confidence NUMERIC(4,3),
    ADD COLUMN classified_by VARCHAR(10),
    ADD COLUMN next_action VARCHAR(300),
    ADD COLUMN application_id BIGINT REFERENCES job_application(id) ON DELETE SET NULL,
    ADD COLUMN processing_error VARCHAR(300),
    ADD COLUMN processed_at TIMESTAMPTZ;

CREATE TABLE review_item (
    id BIGSERIAL PRIMARY KEY,
    email_message_id BIGINT NOT NULL REFERENCES email_message(id) ON DELETE CASCADE,
    application_id BIGINT REFERENCES job_application(id) ON DELETE SET NULL,
    action VARCHAR(30) NOT NULL,
    proposed_status VARCHAR(30),
    proposed_company VARCHAR(200),
    proposed_job_title VARCHAR(200),
    category VARCHAR(40),
    confidence NUMERIC(4,3),
    reason VARCHAR(300),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_review_item_status ON review_item(status);
