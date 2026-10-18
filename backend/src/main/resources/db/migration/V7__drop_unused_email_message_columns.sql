ALTER TABLE email_message DROP COLUMN IF EXISTS ai_category;
ALTER TABLE email_message DROP COLUMN IF EXISTS extracted_company;
ALTER TABLE email_message DROP COLUMN IF EXISTS extracted_job_title;
ALTER TABLE email_message DROP COLUMN IF EXISTS classified_by;
ALTER TABLE email_message DROP COLUMN IF EXISTS next_action;
ALTER TABLE email_message DROP COLUMN IF EXISTS processing_error;
ALTER TABLE email_message DROP COLUMN IF EXISTS processed_at;
