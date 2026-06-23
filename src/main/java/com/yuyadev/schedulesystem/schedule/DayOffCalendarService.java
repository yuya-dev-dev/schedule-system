package com.yuyadev.schedulesystem.schedule;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DayOffCalendarService {

	private final ScheduleDayOffRepository repository;

	public DayOffCalendarService(ScheduleDayOffRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public boolean isDayOff(LocalDate date) {
		return date != null && repository.existsById(date);
	}

	@Transactional(readOnly = true)
	public Set<LocalDate> dayOffDatesBetween(LocalDate startDate, LocalDate endDate) {
		return repository.findByWorkDateBetween(startDate, endDate).stream()
				.map(ScheduleDayOff::getWorkDate)
				.collect(Collectors.toUnmodifiableSet());
	}
}
