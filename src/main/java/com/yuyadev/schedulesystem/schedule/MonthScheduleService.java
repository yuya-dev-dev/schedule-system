package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MonthScheduleService {

	private static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
	private static final LocalTime CLOSING_TIME = LocalTime.of(17, 30);
	private static final int SLOT_MINUTES = 30;
	private static final int COLOR_COUNT = 5;
	private static final DateTimeFormatter MONTH_VALUE = DateTimeFormatter.ofPattern("yyyy-MM");

	private final ScheduleRequestRepository repository;
	private final Clock clock;

	public MonthScheduleService(ScheduleRequestRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public MonthScheduleView getMonth(String requestedMonth) {
		YearMonth currentMonth = YearMonth.now(clock);
		YearMonth selectedMonth = selectMonth(requestedMonth, currentMonth);
		List<LocalDate> workDates = workDates(selectedMonth);
		List<ScheduleRequest> requests = repository
				.findByWorkDateBetweenAndEntryStateOrderByWorkDateAscStartTimeAsc(
						selectedMonth.atDay(1), selectedMonth.atEndOfMonth(), EntryState.PUBLISHED);
		Map<Long, Integer> colors = assignColors(requests);

		return new MonthScheduleView(
				selectedMonth.getYear() + "年" + selectedMonth.getMonthValue() + "月",
				selectedMonth.format(MONTH_VALUE),
				initialFocusDate(currentMonth, selectedMonth, workDates),
				monthTabs(currentMonth, selectedMonth),
				workDates.stream().map(this::toWorkDateView).toList(),
				timeRows(workDates, requests, colors));
	}

	private String initialFocusDate(
			YearMonth currentMonth, YearMonth selectedMonth, List<LocalDate> workDates) {
		if (!selectedMonth.equals(currentMonth)) {
			return null;
		}
		LocalDate today = LocalDate.now(clock);
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
			parsed = YearMonth.parse(requestedMonth, MONTH_VALUE);
		} catch (RuntimeException exception) {
			return currentMonth;
		}
		if (parsed.isBefore(currentMonth.minusMonths(1))
				|| parsed.isAfter(currentMonth.plusMonths(1))) {
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
				month.format(MONTH_VALUE),
				month.getYear() + "年" + month.getMonthValue() + "月",
				month.equals(selectedMonth));
	}

	private List<LocalDate> workDates(YearMonth month) {
		return month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
				.filter(date -> date.getDayOfWeek() == DayOfWeek.WEDNESDAY
						|| date.getDayOfWeek() == DayOfWeek.FRIDAY)
				.toList();
	}

	private WorkDateView toWorkDateView(LocalDate date) {
		String weekday = date.getDayOfWeek() == DayOfWeek.WEDNESDAY ? "水" : "金";
		return new WorkDateView(
				date,
				date.getMonthValue() + "/" + date.getDayOfMonth(),
				weekday,
				date.isBefore(LocalDate.now(clock)));
	}

	private List<TimeRowView> timeRows(
			List<LocalDate> workDates,
			List<ScheduleRequest> requests,
			Map<Long, Integer> colors) {
		List<TimeRowView> rows = new ArrayList<>();
		for (LocalTime start = OPENING_TIME;
				start.isBefore(CLOSING_TIME);
				start = start.plusMinutes(SLOT_MINUTES)) {
			LocalTime end = start.plusMinutes(SLOT_MINUTES);
			List<ScheduleCellView> cells = new ArrayList<>();
			for (LocalDate date : workDates) {
				cells.add(toCell(date, start, end, requests, colors));
			}
			rows.add(new TimeRowView(start, end, timeLabel(start, end), List.copyOf(cells)));
		}
		return List.copyOf(rows);
	}

	private ScheduleCellView toCell(
			LocalDate date,
			LocalTime slotStart,
			LocalTime slotEnd,
			List<ScheduleRequest> requests,
			Map<Long, Integer> colors) {
		ScheduleRequest request = requests.stream()
				.filter(candidate -> candidate.getWorkDate().equals(date))
				.filter(candidate -> overlaps(candidate, slotStart, slotEnd))
				.findFirst()
				.orElse(null);
		boolean readOnly = date.isBefore(LocalDate.now(clock));
		if (request == null) {
			String url = readOnly ? null : UriComponentsBuilder.fromPath("/requests/new")
					.queryParam("date", date)
					.build()
					.toUriString();
			return new ScheduleCellView(
					null, false, false, null, null, false, 0, readOnly, url);
		}

		boolean firstCell = slotStart.equals(OPENING_TIME)
				|| !overlaps(request, slotStart.minusMinutes(SLOT_MINUTES), slotStart);
		return new ScheduleCellView(
				request.getId(),
				true,
				firstCell,
				request.getRequesterName(),
				request.getWorkType() == null ? null : request.getWorkType().getDisplayName(),
				request.hasMissingRequiredFields(),
				colors.get(request.getId()),
				readOnly,
				"/requests/" + request.getId());
	}

	private boolean overlaps(
			ScheduleRequest request, LocalTime slotStart, LocalTime slotEnd) {
		return request.getStartTime().isBefore(slotEnd) && request.getEndTime().isAfter(slotStart);
	}

	private Map<Long, Integer> assignColors(List<ScheduleRequest> requests) {
		Map<Long, Integer> colors = new HashMap<>();
		Map<LocalDate, List<ScheduleRequest>> byDate = new HashMap<>();
		for (ScheduleRequest request : requests) {
			byDate.computeIfAbsent(request.getWorkDate(), ignored -> new ArrayList<>()).add(request);
		}
		for (List<ScheduleRequest> sameDay : byDate.values()) {
			sameDay.sort(Comparator.comparing(ScheduleRequest::getStartTime)
					.thenComparing(ScheduleRequest::getId));
			for (int index = 0; index < sameDay.size(); index++) {
				colors.put(sameDay.get(index).getId(), (index % COLOR_COUNT) + 1);
			}
		}
		return colors;
	}

	private String timeLabel(LocalTime start, LocalTime end) {
		return formatTime(start) + "〜" + formatTime(end);
	}

	private String formatTime(LocalTime time) {
		return time.getHour() + ":" + String.format("%02d", time.getMinute());
	}
}
