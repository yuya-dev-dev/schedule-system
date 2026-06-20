ALTER TABLE schedule_requests ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE schedule_requests ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_schedule_requests_draft_list
    ON schedule_requests (entry_state, work_date, updated_at);
