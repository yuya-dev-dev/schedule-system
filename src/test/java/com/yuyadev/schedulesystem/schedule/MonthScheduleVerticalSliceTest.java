package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static com.yuyadev.schedulesystem.testsupport.CsrfRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuyadev.schedulesystem.holiday.CalendarHoliday;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.yuyadev.schedulesystem.request.WorkType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ScheduleVerticalSliceTestSupport.FixedClockConfiguration.class)
class MonthScheduleVerticalSliceTest extends ScheduleVerticalSliceTestSupport {

	@Test
	void redirectsRootToSchedule() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule"));
	}

	@Test
	void mapsThirtyMinuteRequestsToCellsWithColorsAndContinuationArrows() {
		LocalDate workDate = LocalDate.of(2026, 6, 24);
		savePublished(
				workDate, LocalTime.of(10, 0), LocalTime.of(11, 0), "社員A", WorkType.INSTALL);
		savePublished(
				workDate, LocalTime.of(14, 0), LocalTime.of(15, 0), "社員B", WorkType.DELIVERY);

		MonthScheduleView view = monthScheduleService.getMonth("2026-06");
		int dateIndex = java.util.stream.IntStream.range(0, view.workDates().size())
				.filter(index -> view.workDates().get(index).date().equals(workDate))
				.findFirst()
				.orElseThrow();
		ScheduleCellView firstRequest = cellAt(view, dateIndex, LocalTime.of(10, 0));
		ScheduleCellView secondRequestFirstCell = cellAt(view, dateIndex, LocalTime.of(14, 0));
		ScheduleCellView secondRequestArrow = cellAt(view, dateIndex, LocalTime.of(14, 30));

		assertThat(firstRequest.colorIndex()).isEqualTo(1);
		assertThat(secondRequestFirstCell.colorIndex()).isEqualTo(2);
		assertThat(secondRequestFirstCell.firstCell()).isTrue();
		assertThat(secondRequestArrow.firstCell()).isFalse();
		assertThat(secondRequestArrow.requestId()).isEqualTo(secondRequestFirstCell.requestId());
	}

	@Test
	void reusesFiveColorsWithoutGivingAdjacentRequestsTheSameColor() {
		LocalDate workDate = LocalDate.of(2026, 6, 24);
		for (int index = 0; index < 6; index++) {
			LocalTime start = LocalTime.of(8, 30).plusMinutes(index * 30L);
			savePublished(
					workDate, start, start.plusMinutes(30), "社員" + index, WorkType.DELIVERY);
		}

		MonthScheduleView view = monthScheduleService.getMonth("2026-06");
		int dateIndex = java.util.stream.IntStream.range(0, view.workDates().size())
				.filter(index -> view.workDates().get(index).date().equals(workDate))
				.findFirst()
				.orElseThrow();

		assertThat(java.util.stream.IntStream.range(0, 6)
				.mapToObj(index -> cellAt(
						view, dateIndex, LocalTime.of(8, 30).plusMinutes(index * 30L)).colorIndex()))
				.containsExactly(1, 2, 3, 4, 5, 1);
	}

	@Test
	void givesInternalWorkTypesDedicatedColorWithoutAdvancingNormalColorRotation() {
		LocalDate workDate = LocalDate.of(2026, 6, 24);
		savePublished(
				workDate, LocalTime.of(13, 0), LocalTime.of(13, 30),
				null, WorkType.RECEIVING);
		savePublished(
				workDate, LocalTime.of(14, 0), LocalTime.of(14, 30),
				"社員A", WorkType.INSTALL);

		MonthScheduleView view = monthScheduleService.getMonth("2026-06");
		int dateIndex = java.util.stream.IntStream.range(0, view.workDates().size())
				.filter(index -> view.workDates().get(index).date().equals(workDate))
				.findFirst()
				.orElseThrow();

		assertThat(cellAt(view, dateIndex, LocalTime.of(13, 0)).colorIndex()).isEqualTo(6);
		assertThat(cellAt(view, dateIndex, LocalTime.of(14, 0)).colorIndex()).isEqualTo(1);
	}

	@Test
	void recalculatesColorsAfterAnEarlierRequestIsDeleted() {
		LocalDate workDate = LocalDate.of(2026, 6, 24);
		ScheduleRequest first = savePublished(
				workDate, LocalTime.of(9, 0), LocalTime.of(9, 30),
				"社員A", WorkType.INSTALL);
		ScheduleRequest second = savePublished(
				workDate, LocalTime.of(10, 0), LocalTime.of(10, 30),
				"社員B", WorkType.DELIVERY);
		savePublished(
				workDate, LocalTime.of(11, 0), LocalTime.of(11, 30),
				"社員C", WorkType.COLLECT);

		repository.deleteById(first.getId());
		repository.flush();
		MonthScheduleView view = monthScheduleService.getMonth("2026-06");
		int dateIndex = java.util.stream.IntStream.range(0, view.workDates().size())
				.filter(index -> view.workDates().get(index).date().equals(workDate))
				.findFirst()
				.orElseThrow();

		assertThat(cellAt(view, dateIndex, LocalTime.of(10, 0)).requestId())
				.isEqualTo(second.getId());
		assertThat(cellAt(view, dateIndex, LocalTime.of(10, 0)).colorIndex()).isEqualTo(1);
		assertThat(cellAt(view, dateIndex, LocalTime.of(11, 0)).colorIndex()).isEqualTo(2);
	}

	@Test
	void excludesHolidayColumnsAndRejectsHolidayRegistration() throws Exception {
		holidayRepository.save(new CalendarHoliday(
				LocalDate.of(2026, 6, 24), "架空の祝日", "test",
				LocalDateTime.of(2026, 6, 20, 12, 0)));

		mockMvc.perform(get("/schedule").param("month", "2026-06"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("6/26")))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("6/24"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("/requests/new?date=2026-06-24"))));

		mockMvc.perform(get("/requests/new").param("date", "2026-06-24"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", "10:00")
					.param("endTime", "11:00")
					.param("requesterName", "社員A"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"祝日・休みではない水曜日または金曜日")));
		assertThat(countRequestsWithRequester("社員A")).isZero();
	}

	@Test
	void rejectsInvalidMonthSelectionAndFallsBackToCurrentMonth() throws Exception {
		mockMvc.perform(get("/schedule")
					.param("year", "2027")
					.param("monthNumber", "13"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"正しい年と月を入力してください")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"2026年6月 スケジュール")));
	}

	@Test
	void rejectsMaximumMonthWithoutReturningServerError() throws Exception {
		mockMvc.perform(get("/schedule")
					.param("year", "999999999")
					.param("monthNumber", "12"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"正しい年と月を入力してください")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"2026年6月 スケジュール")));
	}
}
