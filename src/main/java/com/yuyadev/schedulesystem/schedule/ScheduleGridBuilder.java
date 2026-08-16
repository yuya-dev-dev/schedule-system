package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.request.ScheduleRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ScheduleGridBuilder {

	private static final int COLOR_COUNT = 5;
	private static final int INTERNAL_WORK_COLOR = 6;

	private final Clock clock;

	public ScheduleGridBuilder(Clock clock) {
		this.clock = clock;
	}

	public List<TimeRowView> build(
			List<LocalDate> workDates,
			Set<LocalDate> dayOffDates,
			List<ScheduleRequest> requests) {
		Map<LocalDate, List<ScheduleRequest>> requestsByDate = groupByDate(requests);
		Map<Long, Integer> colors = assignColors(requestsByDate);
		List<TimeRowView> rows = new ArrayList<>();
		for (LocalTime start : ScheduleTimeSlots.startTimes()) {
			rows.add(buildRow(start, workDates, dayOffDates, requestsByDate, colors));
		}
		return List.copyOf(rows);
	}

	private TimeRowView buildRow(
			LocalTime start,
			List<LocalDate> workDates,
			Set<LocalDate> dayOffDates,
			Map<LocalDate, List<ScheduleRequest>> requestsByDate,
			Map<Long, Integer> colors) {
		LocalTime end = start.plusMinutes(ScheduleTimeSlots.SLOT_MINUTES);
		List<ScheduleCellView> cells = workDates.stream()
				.map(date -> buildCell(
						date,
						start,
						end,
						dayOffDates.contains(date),
						requestsByDate.getOrDefault(date, List.of()),
						colors))
				.toList();
		return new TimeRowView(start, end, timeLabel(start, end), cells);
	}

	private ScheduleCellView buildCell(
			LocalDate date,
			LocalTime slotStart,
			LocalTime slotEnd,
			boolean dayOff,
			List<ScheduleRequest> requests,
			Map<Long, Integer> colors) {
		if (dayOff) {
			return ScheduleCellView.dayOff(slotStart.equals(ScheduleTimeSlots.OPENING_TIME));
		}

		ScheduleRequest request = requests.stream()
				.filter(candidate -> overlaps(candidate, slotStart, slotEnd))
				.findFirst()
				.orElse(null);
		boolean readOnly = date.isBefore(LocalDate.now(clock));
		if (request == null) {
			return ScheduleCellView.available(readOnly, newRequestUrl(date, readOnly));
		}

		boolean firstCell = slotStart.equals(ScheduleTimeSlots.OPENING_TIME)
				|| !overlaps(
						request,
						slotStart.minusMinutes(ScheduleTimeSlots.SLOT_MINUTES),
						slotStart);
		return ScheduleCellView.occupied(
				request, firstCell, colors.get(request.getId()), readOnly);
	}

	private String newRequestUrl(LocalDate date, boolean readOnly) {
		if (readOnly) {
			return null;
		}
		return UriComponentsBuilder.fromPath("/requests/new")
				.queryParam("date", date)
				.build()
				.toUriString();
	}

	private boolean overlaps(
			ScheduleRequest request, LocalTime slotStart, LocalTime slotEnd) {
		return request.getStartTime().isBefore(slotEnd)
				&& request.getEndTime().isAfter(slotStart);
	}

	private Map<LocalDate, List<ScheduleRequest>> groupByDate(List<ScheduleRequest> requests) {
		Map<LocalDate, List<ScheduleRequest>> requestsByDate = new HashMap<>();
		for (ScheduleRequest request : requests) {
			requestsByDate
					.computeIfAbsent(request.getWorkDate(), ignored -> new ArrayList<>())
					.add(request);
		}
		return requestsByDate;
	}

	private Map<Long, Integer> assignColors(
			Map<LocalDate, List<ScheduleRequest>> requestsByDate) {
		Map<Long, Integer> colors = new HashMap<>();
		for (List<ScheduleRequest> sameDay : requestsByDate.values()) {
			sameDay.sort(Comparator.comparing(ScheduleRequest::getStartTime)
					.thenComparing(ScheduleRequest::getId));
			int normalIndex = 0;
			for (ScheduleRequest request : sameDay) {
				if (ScheduleRequest.isInternalWork(request.getWorkType())) {
					colors.put(request.getId(), INTERNAL_WORK_COLOR);
				} else {
					colors.put(request.getId(), (normalIndex % COLOR_COUNT) + 1);
					normalIndex++;
				}
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
