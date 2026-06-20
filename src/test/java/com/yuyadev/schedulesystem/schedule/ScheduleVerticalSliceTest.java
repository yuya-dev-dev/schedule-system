package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import com.yuyadev.schedulesystem.request.ScheduleRequestPublishingService;
import com.yuyadev.schedulesystem.request.PublishCommand;
import com.yuyadev.schedulesystem.request.WorkType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ScheduleVerticalSliceTest.FixedClockConfiguration.class)
class ScheduleVerticalSliceTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ScheduleRequestRepository repository;

	@Autowired
	private ScheduleRequestPublishingService publishingService;

	@Autowired
	private MonthScheduleService monthScheduleService;

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
	}

	@Test
	void createsRequestFromBlankCellAndOpensItAgain() throws Exception {
		mockMvc.perform(get("/schedule"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月 スケジュール")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("/requests/new?date=2026-06-24")));

		mockMvc.perform(get("/requests/new").param("date", "2026-06-24"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月24日")));

		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", "10:00")
					.param("endTime", "12:00")
					.param("workType", "INSTALL")
					.param("requesterName", "山本"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule?month=2026-06"));

		ScheduleRequest saved = repository.findAll().getFirst();
		assertThat(saved.getEntryState()).isEqualTo(EntryState.PUBLISHED);

		mockMvc.perform(get("/schedule").param("month", "2026-06"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("山本")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("設置")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("/requests/" + saved.getId())));

		mockMvc.perform(get("/requests/{id}", saved.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("案件の確認・編集")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"山本\"")));
	}

	@Test
	void keepsInputOnScreenWhenTimeConflicts() throws Exception {
		createRequest("10:00", "12:00", "社員A");

		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", "11:00")
					.param("endTime", "13:00")
					.param("workType", "DELIVERY")
					.param("requesterName", "社員B"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("その時間はすでに埋まっています")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"社員B\"")));

		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
	}

	@Test
	void editsAnExistingRequestAndReturnsToTheMonth() throws Exception {
		createRequest("10:00", "12:00", "社員A");
		ScheduleRequest saved = repository.findAll().getFirst();

		mockMvc.perform(post("/requests/save")
					.param("id", saved.getId().toString())
					.param("version", Long.toString(saved.getVersion()))
					.param("workDate", "2026-06-24")
					.param("startTime", "13:00")
					.param("endTime", "14:30")
					.param("workType", "EXCHANGE")
					.param("requesterName", "社員C"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule?month=2026-06"));

		ScheduleRequest updated = repository.findById(saved.getId()).orElseThrow();
		assertThat(updated.getRequesterName()).isEqualTo("社員C");
		assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(13, 0));
		assertThat(updated.getWorkType()).isEqualTo(WorkType.EXCHANGE);
	}

	@Test
	void mapsMinuteLevelRequestsToCellsWithColorsAndContinuationArrows() {
		LocalDate workDate = LocalDate.of(2026, 6, 24);
		publishingService.publish(new PublishCommand(
				workDate, LocalTime.of(10, 0), LocalTime.of(11, 0), "社員A", WorkType.INSTALL));
		publishingService.publish(new PublishCommand(
				workDate, LocalTime.of(14, 1), LocalTime.of(15, 0), "社員B", WorkType.DELIVERY));

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

	private void createRequest(String start, String end, String requester) throws Exception {
		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", start)
					.param("endTime", end)
					.param("workType", "INSTALL")
					.param("requesterName", requester))
				.andExpect(status().is3xxRedirection());
	}

	private ScheduleCellView cellAt(
			MonthScheduleView view, int dateIndex, LocalTime startTime) {
		return view.timeRows().stream()
				.filter(row -> row.startTime().equals(startTime))
				.findFirst()
				.orElseThrow()
				.cells()
				.get(dateIndex);
	}

	@TestConfiguration
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(
					Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
		}
	}
}
