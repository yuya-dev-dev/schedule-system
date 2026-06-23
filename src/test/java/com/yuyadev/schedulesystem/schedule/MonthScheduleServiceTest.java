package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuyadev.schedulesystem.holiday.HolidayCalendarService;
import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthScheduleServiceTest {

	@Mock
	private ScheduleRequestRepository repository;

	@Mock
	private HolidayCalendarService holidayCalendarService;

	@Mock
	private DayOffCalendarService dayOffCalendarService;

	private MonthScheduleService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(
				Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
		service = new MonthScheduleService(
				repository, holidayCalendarService, dayOffCalendarService, clock);
		when(holidayCalendarService.holidayDatesBetween(any(), any())).thenReturn(Set.of());
		when(dayOffCalendarService.dayOffDatesBetween(any(), any())).thenReturn(Set.of());
		when(repository.findByWorkDateBetweenAndEntryStateOrderByWorkDateAscStartTimeAsc(
				any(), any(), any(EntryState.class)))
				.thenReturn(List.of());
	}

	@Test
	void buildsCurrentMonthWithWorkdaysAndThirtyMinuteRows() {
		MonthScheduleView view = service.getMonth(null);

		assertThat(view.title()).isEqualTo("2026年6月");
		assertThat(view.monthTabs())
				.extracting(MonthTabView::label)
				.containsExactly("2026年5月", "2026年6月", "2026年7月");
		assertThat(view.workDates()).hasSize(8);
		assertThat(view.workDates())
				.extracting(WorkDateView::monthDayLabel)
				.containsExactly("6/3", "6/5", "6/10", "6/12", "6/17", "6/19", "6/24", "6/26");
		assertThat(view.workDates())
				.extracting(WorkDateView::past)
				.containsExactly(true, true, true, true, true, true, false, false);
		assertThat(view.initialFocusDate()).isEqualTo("2026-06-24");
		assertThat(view.timeRows()).hasSize(18);
		assertThat(view.timeRows().getFirst().cells().getFirst().destinationUrl()).isNull();
		assertThat(view.timeRows().getFirst().cells().getFirst().readOnly()).isTrue();
		assertThat(view.timeRows().getFirst().startTime()).isEqualTo(LocalTime.of(8, 30));
		assertThat(view.timeRows().getLast().endTime()).isEqualTo(LocalTime.of(17, 30));
	}

	@Test
	void excludesCachedHolidaysFromWorkDates() {
		when(holidayCalendarService.holidayDatesBetween(any(), any()))
				.thenReturn(Set.of(LocalDate.of(2026, 6, 24)));

		MonthScheduleView view = service.getMonth("2026-06");

		assertThat(view.workDates())
				.extracting(WorkDateView::date)
				.doesNotContain(LocalDate.of(2026, 6, 24));
		assertThat(view.workDates())
				.extracting(WorkDateView::monthDayLabel)
				.containsExactly("6/3", "6/5", "6/10", "6/12", "6/17", "6/19", "6/26");
	}

	@Test
	void displaysDayOffColumnAsBlockedCells() {
		when(dayOffCalendarService.dayOffDatesBetween(any(), any()))
				.thenReturn(Set.of(LocalDate.of(2026, 6, 24)));

		MonthScheduleView view = service.getMonth("2026-06");

		WorkDateView dayOff = view.workDates().stream()
				.filter(workDate -> workDate.date().equals(LocalDate.of(2026, 6, 24)))
				.findFirst()
				.orElseThrow();
		assertThat(dayOff.dayOff()).isTrue();
		int dateIndex = view.workDates().indexOf(dayOff);
		assertThat(view.timeRows())
				.extracting(row -> row.cells().get(dateIndex))
				.allMatch(ScheduleCellView::dayOff)
				.allMatch(cell -> cell.destinationUrl() == null);
		assertThat(view.timeRows().getFirst().cells().get(dateIndex).firstCell()).isTrue();
	}

	@Test
	void displaysAnyRequestedMonthWhileKeepingCurrentMonthTabs() {
		MonthScheduleView view = service.getMonth("2027-01");

		assertThat(view.selectedMonth()).isEqualTo("2027-01");
		assertThat(view.selectedYear()).isEqualTo(2027);
		assertThat(view.selectedMonthNumber()).isEqualTo(1);
		assertThat(view.initialFocusDate()).isNull();
		assertThat(view.monthTabs())
				.extracting(MonthTabView::label)
				.containsExactly("2026年5月", "2026年6月", "2026年7月");
		assertThat(view.monthTabs()).noneMatch(MonthTabView::selected);
	}

	@Test
	void fallsBackToCurrentMonthWhenRequestedMonthIsInvalid() {
		MonthScheduleView view = service.getMonth("not-a-month");

		assertThat(view.selectedMonth()).isEqualTo("2026-06");
		assertThat(view.title()).isEqualTo("2026年6月");
	}

	@Test
	void doesNotSelectAnInitialFocusDateForAnotherMonth() {
		MonthScheduleView view = service.getMonth("2026-07");

		assertThat(view.initialFocusDate()).isNull();
	}

	@Test
	void displaysArbitraryPastMonthAsReadOnly() {
		MonthScheduleView view = service.getMonth("2025-01");

		assertThat(view.selectedMonth()).isEqualTo("2025-01");
		assertThat(view.workDates()).isNotEmpty().allMatch(WorkDateView::past);
		assertThat(view.timeRows()).allSatisfy(row ->
				assertThat(row.cells()).allMatch(ScheduleCellView::readOnly));
	}
}
