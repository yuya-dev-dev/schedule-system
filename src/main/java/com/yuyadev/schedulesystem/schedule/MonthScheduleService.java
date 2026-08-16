package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.holiday.HolidayCalendarService;
import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MonthScheduleService {

	private final ScheduleRequestRepository repository;
	private final HolidayCalendarService holidayCalendarService;
	private final DayOffCalendarService dayOffCalendarService;
	private final ScheduleDatePolicy datePolicy;
	private final ScheduleGridBuilder gridBuilder;
	private final Clock clock;

	public MonthScheduleService(
			ScheduleRequestRepository repository,
			HolidayCalendarService holidayCalendarService,
			DayOffCalendarService dayOffCalendarService,
			ScheduleDatePolicy datePolicy,
			ScheduleGridBuilder gridBuilder,
			Clock clock) {
		this.repository = repository;
		this.holidayCalendarService = holidayCalendarService;
		this.dayOffCalendarService = dayOffCalendarService;
		this.datePolicy = datePolicy;
		this.gridBuilder = gridBuilder;
		this.clock = clock;
	}

	public MonthScheduleView getMonth(String requestedMonth) {
		LocalDate today = LocalDate.now(clock);
		YearMonth currentMonth = YearMonth.from(today);
		YearMonth selectedMonth = selectMonth(requestedMonth, currentMonth);
		List<LocalDate> workDates = workDates(selectedMonth);
		Set<LocalDate> dayOffDates = dayOffCalendarService.dayOffDatesBetween(
				selectedMonth.atDay(1), selectedMonth.atEndOfMonth());
		List<ScheduleRequest> requests = repository
				.findByWorkDateBetweenAndEntryStateOrderByWorkDateAscStartTimeAsc(
						selectedMonth.atDay(1), selectedMonth.atEndOfMonth(), EntryState.PUBLISHED);
		return new MonthScheduleView(
				selectedMonth.getYear() + "年" + selectedMonth.getMonthValue() + "月",
				selectedMonth.toString(),
				selectedMonth.getYear(),
				selectedMonth.getMonthValue(),
				initialFocusDate(today, selectedMonth, workDates),
				monthTabs(currentMonth, selectedMonth),
				workDates.stream()
						.map(date -> toWorkDateView(date, dayOffDates.contains(date), today))
						.toList(),
				gridBuilder.build(workDates, dayOffDates, requests, today));
	}

	private String initialFocusDate(
			LocalDate today, YearMonth selectedMonth, List<LocalDate> workDates) {
		if (!selectedMonth.equals(YearMonth.from(today))) {
			return null;
		}
		return workDates.stream()
				.filter(date -> !date.isBefore(today))
				.findFirst()
				.map(LocalDate::toString)
				.orElse(null);
	}

	private YearMonth selectMonth(String requestedMonth, YearMonth currentMonth) {
		if (requestedMonth == null || requestedMonth.isBlank()) {
			return currentMonth;
		}
		YearMonth parsed;
		try {
			parsed = YearMonth.parse(requestedMonth);
		} catch (DateTimeParseException exception) {
			return currentMonth;
		}
		return parsed;
	}

	private List<MonthTabView> monthTabs(YearMonth currentMonth, YearMonth selectedMonth) {
		return List.of(
				monthTab(currentMonth.minusMonths(1), selectedMonth),
				monthTab(currentMonth, selectedMonth),
				monthTab(currentMonth.plusMonths(1), selectedMonth));
	}

	private MonthTabView monthTab(YearMonth month, YearMonth selectedMonth) {
		return new MonthTabView(
				month.toString(),
				month.getYear() + "年" + month.getMonthValue() + "月",
				month.equals(selectedMonth));
	}

	private List<LocalDate> workDates(YearMonth month) {
		Set<LocalDate> holidays = holidayCalendarService.holidayDatesBetween(
				month.atDay(1), month.atEndOfMonth());
		return month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
				.filter(datePolicy::isScheduleWeekday)
				.filter(date -> !holidays.contains(date))
				.toList();
	}

	private WorkDateView toWorkDateView(LocalDate date, boolean dayOff, LocalDate today) {
		String weekday = date.getDayOfWeek() == DayOfWeek.WEDNESDAY ? "水" : "金";
		return new WorkDateView(
				date,
				date.getMonthValue() + "/" + date.getDayOfMonth(),
				weekday,
				date.isBefore(today),
				dayOff);
	}
}
