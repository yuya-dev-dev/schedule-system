ALTER TABLE schedule_requests DROP CONSTRAINT ck_published_schedule_fields;

ALTER TABLE schedule_requests ADD CONSTRAINT ck_published_schedule_fields
    CHECK (
        entry_state <> 'PUBLISHED'
        OR (
            start_time IS NOT NULL
            AND end_time IS NOT NULL
            AND (
                COALESCE(work_type IN ('RECEIVING', 'PRODUCT_MANAGEMENT'), FALSE)
                OR NULLIF(TRIM(requester_name), '') IS NOT NULL
            )
        )
    );

ALTER TABLE schedule_requests ADD CONSTRAINT ck_schedule_time_slots
    CHECK (
        (start_time IS NULL OR (
            start_time >= TIME '08:30:00'
            AND start_time <= TIME '17:00:00'
            AND EXTRACT(MINUTE FROM start_time) IN (0, 30)
            AND EXTRACT(SECOND FROM start_time) = 0
        ))
        AND
        (end_time IS NULL OR (
            end_time <= TIME '17:30:00'
            AND end_time >= TIME '09:00:00'
            AND EXTRACT(MINUTE FROM end_time) IN (0, 30)
            AND EXTRACT(SECOND FROM end_time) = 0
        ))
    );
