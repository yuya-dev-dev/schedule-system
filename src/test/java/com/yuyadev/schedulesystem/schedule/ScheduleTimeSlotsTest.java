package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ScheduleTimeSlotsTest {

	@Test
	void providesStartAndEndOptionsForEveryScheduleSlot() {
		assertThat(ScheduleTimeSlots.startTimes())
				.hasSize(18)
				.startsWith(LocalTime.of(8, 30))
				.endsWith(LocalTime.of(17, 0));
		assertThat(ScheduleTimeSlots.endTimes())
				.hasSize(18)
				.startsWith(LocalTime.of(9, 0))
				.endsWith(LocalTime.of(17, 30));
	}

	@Test
	void acceptsOnlyExactThirtyMinuteBoundaries() {
		assertThat(ScheduleTimeSlots.isAligned(LocalTime.of(8, 30))).isTrue();
		assertThat(ScheduleTimeSlots.isAligned(LocalTime.of(17, 0))).isTrue();
		assertThat(ScheduleTimeSlots.isAligned(LocalTime.of(14, 1))).isFalse();
		assertThat(ScheduleTimeSlots.isAligned(LocalTime.of(14, 0, 1))).isFalse();
		assertThat(ScheduleTimeSlots.isAligned(null)).isFalse();
	}
}
