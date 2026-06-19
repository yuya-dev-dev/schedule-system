ALTER TABLE schedule_requests
    ADD CONSTRAINT ex_schedule_requests_published_time
    EXCLUDE USING gist (
        tsrange(work_date + start_time, work_date + end_time, '[)') WITH &&
    )
    WHERE (entry_state = 'PUBLISHED');
