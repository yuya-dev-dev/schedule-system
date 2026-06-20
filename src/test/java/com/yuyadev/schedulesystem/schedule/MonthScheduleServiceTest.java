package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthScheduleServiceTest {

	@Mock
	private ScheduleRequestRepository repository;

	private MonthScheduleService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(
				Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
		service = new MonthScheduleService(repository, clock);
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
		assertThat(view.timeRows()).hasSize(18);
		assertThat(view.timeRows().getFirst().startTime()).isEqualTo(LocalTime.of(8, 30));
		assertThat(view.timeRows().getLast().endTime()).isEqualTo(LocalTime.of(17, 30));
	}

	@Test
	void fallsBackToCurrentMonthWhenRequestedMonthIsOutsideTabs() {
		MonthScheduleView view = service.getMonth("2027-01");

		assertThat(view.selectedMonth()).isEqualTo("2026-06");
	}
}
