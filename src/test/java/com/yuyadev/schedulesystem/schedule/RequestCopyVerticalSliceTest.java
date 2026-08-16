package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuyadev.schedulesystem.request.EntryState;
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
class RequestCopyVerticalSliceTest extends ScheduleVerticalSliceTestSupport {

	@Test
	void selectsDestinationAndCopiesPublishedRequestToAnotherWorkDate() throws Exception {
		ScheduleRequest source = createDetailedRequest(
				LocalDate.of(2026, 6, 24), "10:00", "12:00", "社員A");

		mockMvc.perform(get("/requests/{id}", source.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"この入力内容をほかの日時にコピーする")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"/requests/" + source.getId() + "/copy")));

		mockMvc.perform(get("/requests/{id}/copy", source.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("コピー先選択")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月24日（水）")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("日付を直接入力")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("コピー元")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月 スケジュール")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026-06-26")));

		mockMvc.perform(post("/requests/{id}/copy", source.getId())
					.param("targetDate", "2026-06-26"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/requests/*"));

		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isEqualTo(2);
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isZero();
		ScheduleRequest copied = repository.findAll().stream()
				.filter(request -> request.getWorkDate().equals(LocalDate.of(2026, 6, 26)))
				.findFirst()
				.orElseThrow();
		assertThat(copied.getRequesterName()).isEqualTo("社員A");
		assertThat(copied.getStartTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(copied.getEndTime()).isEqualTo(LocalTime.of(12, 0));
		assertThat(copied.getWorkType()).isEqualTo(WorkType.INSTALL);
		assertThat(copied.getRequestDetail()).isEqualTo("架空の設置作業");
		assertThat(copied.getAddress()).isEqualTo("愛知県名古屋市架空町1-1");
		assertThat(copied.getDesiredArrivalTime()).isEqualTo("午後ならいつでも");
		assertThat(copied.isCompanionRequired()).isTrue();
		assertThat(copied.getMeetingPlace()).isEqualTo("名古屋支店");
		assertThat(copied.getDepartureTime()).isEqualTo(LocalTime.of(9, 30));
		assertThat(copied.getVehicleName()).isEqualTo("車両A");
		assertThat(copied.getDispatchStatus()).isEqualTo(
				com.yuyadev.schedulesystem.request.DispatchStatus.DISPATCHED);
		assertThat(copied.getNote()).isEqualTo("架空の注意事項");
	}

	@Test
	void selectsCopyMonthAndFallsBackToSourceMonthAfterInvalidSelection() throws Exception {
		ScheduleRequest source = createDetailedRequest(
				LocalDate.of(2026, 6, 24), "10:00", "12:00", "社員A");

		mockMvc.perform(get("/requests/{id}/copy", source.getId())
					.param("year", "2027")
					.param("monthNumber", "1"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"2027年1月 スケジュール")));

		mockMvc.perform(get("/requests/{id}/copy", source.getId())
					.param("year", "2027")
					.param("monthNumber", "13"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"正しい年と月を入力してください")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"2026年6月 スケジュール")));
	}

	@Test
	void keepsCopiedValuesWithoutCreatingRecordWhenDestinationConflicts() throws Exception {
		ScheduleRequest source = createDetailedRequest(
				LocalDate.of(2026, 6, 24), "10:00", "12:00", "社員A");
		createDetailedRequest(LocalDate.of(2026, 6, 26), "11:00", "13:00", "社員B");

		mockMvc.perform(post("/requests/{id}/copy", source.getId())
					.param("targetDate", "2026-06-26"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("その時間はすでに埋まっています")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月26日（金）")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"社員A\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("架空の設置作業")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"workDate\" value=\"2026-06-26\"")));

		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isEqualTo(2);
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isZero();
	}

	@Test
	void rejectsSameDayAndUnavailableCopyDestinations() throws Exception {
		ScheduleRequest source = createDetailedRequest(
				LocalDate.of(2026, 6, 24), "10:00", "12:00", "社員A");
		dayOffRepository.saveAndFlush(new ScheduleDayOff(LocalDate.of(2026, 6, 26)));

		mockMvc.perform(post("/requests/{id}/copy", source.getId())
					.param("targetDate", "2026-06-24"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"コピー元と同じ日は選択できません")));

		mockMvc.perform(post("/requests/{id}/copy", source.getId())
					.param("targetDate", "2026-06-26"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"祝日・休みではない水曜日または金曜日")));

		assertThat(repository.count()).isOne();
	}

	@Test
	void doesNotAllowCopyingPastPublishedRequest() throws Exception {
		ScheduleRequest past = repository.saveAndFlush(ScheduleRequest.published(
				LocalDate.of(2026, 6, 19), LocalTime.of(10, 0), LocalTime.of(11, 0),
				"社員A", WorkType.INSTALL));

		mockMvc.perform(get("/requests/{id}", past.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("ほかの日時にコピー"))));

		mockMvc.perform(get("/requests/{id}/copy", past.getId()))
				.andExpect(status().isBadRequest());
	}
}
