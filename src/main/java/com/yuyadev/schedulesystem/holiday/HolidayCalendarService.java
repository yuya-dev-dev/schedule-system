package com.yuyadev.schedulesystem.holiday;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HolidayCalendarService {

	static final String SOURCE_NAME = "CABINET_OFFICE_JAPAN";

	private final CalendarHolidayRepository repository;
	private final Clock clock;
	private final Duration maxAge;

	public HolidayCalendarService(
			CalendarHolidayRepository repository,
			Clock clock,
			@Value("${schedule.holidays.cache-days:7}") long cacheDays) {
		this.repository = repository;
		this.clock = clock;
		this.maxAge = Duration.ofDays(cacheDays);
	}

	public boolean isHoliday(LocalDate date) {
		return date != null && repository.existsById(date);
	}

	public Set<LocalDate> holidayDatesBetween(LocalDate startDate, LocalDate endDate) {
		return repository.findByHolidayDateBetween(startDate, endDate).stream()
				.map(CalendarHoliday::getHolidayDate)
				.collect(Collectors.toUnmodifiableSet());
	}

	public boolean cacheIsFresh() {
		LocalDateTime threshold = LocalDateTime.ofInstant(
				clock.instant().minus(maxAge), clock.getZone());
		return repository.findTopByOrderBySyncedAtDesc()
				.map(CalendarHoliday::getSyncedAt)
				.filter(syncedAt -> !syncedAt.isBefore(threshold))
				.isPresent();
	}

	@Transactional
	public void replaceAll(List<HolidayDefinition> definitions) {
		LocalDateTime syncedAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		List<CalendarHoliday> holidays = definitions.stream()
				.map(definition -> new CalendarHoliday(
						definition.date(), definition.name(), SOURCE_NAME, syncedAt))
				.toList();
		repository.deleteAllInBatch();
		repository.saveAll(holidays);
	}
}
