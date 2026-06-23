CREATE TABLE calendar_holidays (
    holiday_date DATE PRIMARY KEY,
    holiday_name VARCHAR(100) NOT NULL,
    source VARCHAR(100) NOT NULL,
    synced_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_calendar_holidays_synced_at
    ON calendar_holidays (synced_at);
