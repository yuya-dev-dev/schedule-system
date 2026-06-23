package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuyadev.schedulesystem.holiday.HolidayCalendarService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleDatePolicyTest {

	@Mock
	private HolidayCalendarService holidayCalendarService;

	@Mock
	private DayOffCalendarService dayOffCalendarService;

	private ScheduleDatePolicy policy;

	@BeforeEach
	void setUp() {
		policy = new ScheduleDatePolicy(Clock.fixed(
				Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo")),
				holidayCalendarService,
				dayOffCalendarService);
	}

	@Test
	void allowsAnyFutureWednesdayOrFriday() {
		when(holidayCalendarService.isHoliday(LocalDate.of(2026, 6, 24))).thenReturn(false);
		when(holidayCalendarService.isHoliday(LocalDate.of(2026, 7, 31))).thenReturn(false);
		when(holidayCalendarService.isHoliday(LocalDate.of(2026, 8, 5))).thenReturn(false);
		when(holidayCalendarService.isHoliday(LocalDate.of(2035, 1, 3))).thenReturn(false);
		assertThat(policy.isRegistrable(LocalDate.of(2026, 5, 1))).isFalse();
		assertThat(policy.isRegistrable(LocalDate.of(2026, 6, 19))).isFalse();
		assertThat(policy.isRegistrable(LocalDate.of(2026, 6, 24))).isTrue();
		assertThat(policy.isRegistrable(LocalDate.of(2026, 7, 31))).isTrue();
		assertThat(policy.isRegistrable(LocalDate.of(2026, 8, 5))).isTrue();
		assertThat(policy.isRegistrable(LocalDate.of(2035, 1, 3))).isTrue();
		assertThat(policy.isRegistrable(LocalDate.of(2026, 6, 23))).isFalse();
		assertThat(policy.isPast(LocalDate.of(2026, 6, 19))).isTrue();
		assertThat(policy.isPast(LocalDate.of(2026, 6, 20))).isFalse();
	}

	@Test
	void allowsTodayWhenTodayIsAWorkday() {
		ScheduleDatePolicy workdayPolicy = new ScheduleDatePolicy(Clock.fixed(
				Instant.parse("2026-06-24T03:00:00Z"), ZoneId.of("Asia/Tokyo")),
				holidayCalendarService,
				dayOffCalendarService);
		when(holidayCalendarService.isHoliday(LocalDate.of(2026, 6, 24))).thenReturn(false);

		assertThat(workdayPolicy.isRegistrable(LocalDate.of(2026, 6, 24))).isTrue();
	}

	@Test
	void rejectsHolidayEvenWhenItIsFutureWorkday() {
		when(holidayCalendarService.isHoliday(LocalDate.of(2026, 6, 24))).thenReturn(true);

		assertThat(policy.isRegistrable(LocalDate.of(2026, 6, 24))).isFalse();
	}

	@Test
	void rejectsDayOffEvenWhenItIsFutureWorkday() {
		when(holidayCalendarService.isHoliday(LocalDate.of(2026, 6, 24))).thenReturn(false);
		when(dayOffCalendarService.isDayOff(LocalDate.of(2026, 6, 24))).thenReturn(true);

		assertThat(policy.isRegistrable(LocalDate.of(2026, 6, 24))).isFalse();
	}

}
