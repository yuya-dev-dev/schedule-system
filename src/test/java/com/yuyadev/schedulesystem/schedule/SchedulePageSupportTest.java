package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SchedulePageSupportTest {

	@Test
	void keepsTheExistingScheduleUrlFormatForFiveDigitYears() {
		assertThat(SchedulePageSupport.scheduleUrl(LocalDate.of(10000, 1, 1)))
				.isEqualTo("/schedule?month=10000-01");
	}
}
