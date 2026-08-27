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

	@Test
	void rejectsTheMaximumYearAndMonthThatCannotBeExpanded() {
		SchedulePageSupport.MonthSelection selection = SchedulePageSupport.resolveMonth(
				null, "999999999", "12", null);

		assertThat(selection.error()).isEqualTo("正しい年と月を入力してください");
		assertThat(selection.requestedMonth()).isNull();
	}
}
