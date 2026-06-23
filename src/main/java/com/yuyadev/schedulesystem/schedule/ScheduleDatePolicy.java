package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.holiday.HolidayCalendarService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ScheduleDatePolicy {

	private final Clock clock;
	private final HolidayCalendarService holidayCalendarService;

	public ScheduleDatePolicy(Clock clock, HolidayCalendarService holidayCalendarService) {
		this.clock = clock;
		this.holidayCalendarService = holidayCalendarService;
	}

	public boolean isRegistrable(LocalDate date) {
		if (date == null || isPast(date) || !isWorkday(date)
				|| holidayCalendarService.isHoliday(date)) {
			return false;
		}
		return true;
	}

	public void requireRegistrable(LocalDate date) {
		if (!isRegistrable(date)) {
			throw new IllegalArgumentException(
					"対象日は今日以降の祝日ではない水曜日または金曜日を指定してください");
		}
	}

	public boolean isPast(LocalDate date) {
		return date != null && date.isBefore(LocalDate.now(clock));
	}

	private boolean isWorkday(LocalDate date) {
		return date.getDayOfWeek() == DayOfWeek.WEDNESDAY
				|| date.getDayOfWeek() == DayOfWeek.FRIDAY;
	}
}
