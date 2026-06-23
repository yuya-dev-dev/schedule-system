package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.holiday.HolidayCalendarService;
import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DayOffService {

	public record DayOffResult(LocalDate workDate, long deletedCount) {
	}

	private final ScheduleDayOffRepository dayOffRepository;
	private final ScheduleRequestRepository requestRepository;
	private final HolidayCalendarService holidayCalendarService;
	private final Clock clock;

	public DayOffService(
			ScheduleDayOffRepository dayOffRepository,
			ScheduleRequestRepository requestRepository,
			HolidayCalendarService holidayCalendarService,
			Clock clock) {
		this.dayOffRepository = dayOffRepository;
		this.requestRepository = requestRepository;
		this.holidayCalendarService = holidayCalendarService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DayOffConfirmation confirmation(LocalDate date, String dateTitle) {
		requireChangeableWorkday(date);
		return new DayOffConfirmation(
				date,
				dateTitle,
				requestRepository.countByWorkDateAndEntryState(date, EntryState.PUBLISHED),
				requestRepository.countByWorkDateAndEntryState(date, EntryState.DRAFT));
	}

	@Transactional
	public DayOffResult setDayOff(LocalDate date) {
		requireChangeableWorkday(date);
		long deletedCount = requestRepository.deleteByWorkDate(date);
		if (!dayOffRepository.existsById(date)) {
			dayOffRepository.save(new ScheduleDayOff(date));
		}
		return new DayOffResult(date, deletedCount);
	}

	@Transactional
	public void unsetDayOff(LocalDate date) {
		requireFutureDate(date);
		dayOffRepository.deleteById(date);
	}

	private void requireChangeableWorkday(LocalDate date) {
		requireFutureDate(date);
		if (!isWorkday(date) || holidayCalendarService.isHoliday(date)) {
			throw new IllegalArgumentException("休み設定できるのは祝日ではない水曜日または金曜日です");
		}
	}

	private void requireFutureDate(LocalDate date) {
		if (date == null || date.isBefore(LocalDate.now(clock))) {
			throw new IllegalArgumentException("過去日は変更できません");
		}
	}

	private boolean isWorkday(LocalDate date) {
		return date.getDayOfWeek() == DayOfWeek.WEDNESDAY
				|| date.getDayOfWeek() == DayOfWeek.FRIDAY;
	}
}
