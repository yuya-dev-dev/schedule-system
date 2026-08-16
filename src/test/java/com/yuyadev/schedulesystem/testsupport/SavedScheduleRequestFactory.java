package com.yuyadev.schedulesystem.testsupport;

import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import java.time.LocalDate;
import java.time.LocalTime;

public final class SavedScheduleRequestFactory {

	private final ScheduleRequestRepository repository;

	public SavedScheduleRequestFactory(ScheduleRequestRepository repository) {
		this.repository = repository;
	}

	public ScheduleRequest published(
			LocalDate workDate,
			LocalTime startTime,
			LocalTime endTime,
			String requesterName,
			WorkType workType) {
		return repository.saveAndFlush(ScheduleRequest.published(
				workDate, startTime, endTime, requesterName, workType));
	}
}
