package com.yuyadev.schedulesystem.schedule;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component
public class ScheduleDatePolicy {

	private final Clock clock;

	public ScheduleDatePolicy(Clock clock) {
		this.clock = clock;
	}

	public boolean isRegistrable(LocalDate date) {
		if (date == null || isPast(date) || !isWorkday(date)) {
			return false;
		}
		YearMonth currentMonth = YearMonth.now(clock);
		YearMonth requestedMonth = YearMonth.from(date);
		return !requestedMonth.isBefore(currentMonth.minusMonths(1))
				&& !requestedMonth.isAfter(currentMonth.plusMonths(1));
	}

	public void requireRegistrable(LocalDate date) {
		if (!isRegistrable(date)) {
			throw new IllegalArgumentException(
					"対象日は今日以降かつ表示対象月の水曜日または金曜日を指定してください");
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
