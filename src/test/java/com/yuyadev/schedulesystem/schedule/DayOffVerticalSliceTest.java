package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuyadev.schedulesystem.request.DraftReason;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import java.time.LocalDate;
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
class DayOffVerticalSliceTest extends ScheduleVerticalSliceTestSupport {

	@Test
	void marksFutureWorkDateAsDayOffAfterDeletingRequests() throws Exception {
		ScheduleRequest published = repository.saveAndFlush(ScheduleRequest.published(
				LocalDate.of(2026, 6, 24), LocalTime.of(10, 0), LocalTime.of(11, 0),
				"社員A", WorkType.INSTALL));
		ScheduleRequest draft = repository.saveAndFlush(ScheduleRequest.draft(
				LocalDate.of(2026, 6, 24), LocalTime.of(13, 0), LocalTime.of(14, 0),
				"社員B", null, DraftReason.INCOMPLETE, "入力不足"));

		mockMvc.perform(get("/schedule/day-offs/new").param("date", "2026-06-24"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("休み設定確認")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("公開案件</dt><dd>1件")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("下書き</dt><dd>1件")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("削除件数を確認して次へ")));

		mockMvc.perform(post("/schedule/day-offs/confirm")
					.param("date", "2026-06-24"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("最終確認です")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("休みにする")));

		mockMvc.perform(post("/schedule/day-offs")
					.param("date", "2026-06-24"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule?month=2026-06"))
				.andExpect(flash().attribute("notice", "休みにしました。削除件数: 2件"));

		assertThat(repository.existsById(published.getId())).isFalse();
		assertThat(repository.existsById(draft.getId())).isFalse();
		assertThat(dayOffRepository.existsById(LocalDate.of(2026, 6, 24))).isTrue();

		mockMvc.perform(get("/schedule").param("month", "2026-06"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("休み解除")))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("/requests/new?date=2026-06-24"))));

		MonthScheduleView view = monthScheduleService.getMonth("2026-06");
		int dateIndex = java.util.stream.IntStream.range(0, view.workDates().size())
				.filter(index -> view.workDates().get(index).date().equals(LocalDate.of(2026, 6, 24)))
				.findFirst()
				.orElseThrow();
		assertThat(view.workDates().get(dateIndex).dayOff()).isTrue();
		assertThat(view.timeRows()).allSatisfy(row ->
				assertThat(row.cells().get(dateIndex).dayOff()).isTrue());

		mockMvc.perform(get("/requests/new").param("date", "2026-06-24"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", "10:00")
					.param("endTime", "11:00")
					.param("requesterName", "社員A"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("祝日・休みではない")));
	}

	@Test
	void unsetsDayOffAndAllowsBlankCellInputAgain() throws Exception {
		dayOffRepository.saveAndFlush(new ScheduleDayOff(LocalDate.of(2026, 6, 24)));

		mockMvc.perform(get("/schedule/day-offs/2026-06-24/delete"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("休み解除確認")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("休みを解除")));

		mockMvc.perform(post("/schedule/day-offs/2026-06-24/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule?month=2026-06"))
				.andExpect(flash().attribute("notice", "休みを解除しました"));

		assertThat(dayOffRepository.existsById(LocalDate.of(2026, 6, 24))).isFalse();
		mockMvc.perform(get("/schedule").param("month", "2026-06"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"/requests/new?date=2026-06-24")));
	}
}
