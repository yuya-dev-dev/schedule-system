package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ScheduleDatePolicyTest {

	private final ScheduleDatePolicy policy = new ScheduleDatePolicy(Clock.fixed(
			Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo")));

	@Test
	void allowsAnyFutureWednesdayOrFriday() {
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
				Instant.parse("2026-06-24T03:00:00Z"), ZoneId.of("Asia/Tokyo")));

		assertThat(workdayPolicy.isRegistrable(LocalDate.of(2026, 6, 24))).isTrue();
	}

}
