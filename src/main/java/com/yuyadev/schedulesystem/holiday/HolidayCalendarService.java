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

	private final CalendarHolidayRepository holidayRepository;
	private final Clock clock;
	private final Duration cacheMaxAge;

	public HolidayCalendarService(
			CalendarHolidayRepository holidayRepository,
			Clock clock,
			@Value("${schedule.holidays.cache-days:7}") long cacheDays) {
		this.holidayRepository = holidayRepository;
		this.clock = clock;
		this.cacheMaxAge = Duration.ofDays(cacheDays);
	}

	public boolean isHoliday(LocalDate date) {
		return date != null && holidayRepository.existsById(date);
	}

	public boolean hasCachedHolidays() {
		return holidayRepository.count() > 0;
	}

	public Set<LocalDate> holidayDatesBetween(LocalDate startDate, LocalDate endDate) {
		return holidayRepository.findByHolidayDateBetween(startDate, endDate).stream()
				.map(CalendarHoliday::getHolidayDate)
				.collect(Collectors.toUnmodifiableSet());
	}

	public boolean cacheIsFresh() {
		LocalDateTime threshold = LocalDateTime.ofInstant(
				clock.instant().minus(cacheMaxAge), clock.getZone());
		return holidayRepository.findTopByOrderBySyncedAtDesc()
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
		holidayRepository.deleteAllInBatch();
		holidayRepository.saveAll(holidays);
	}
}
