package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.DraftReason;
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
import org.springframework.test.web.servlet.MvcResult;
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
	void listsResumesAndDeletesAnActiveDraftWhilePurgingPastDrafts() throws Exception {
		ScheduleRequest active = repository.saveAndFlush(ScheduleRequest.draft(
				LocalDate.of(2026, 6, 24), null, null, "社員A", null,
				DraftReason.INCOMPLETE, "入力不足"));
		ScheduleRequest expired = repository.saveAndFlush(ScheduleRequest.draft(
				LocalDate.of(2026, 6, 19), null, null, "社員B", null,
				DraftReason.INCOMPLETE, "入力不足"));

		mockMvc.perform(get("/schedule").param("month", "2026-06"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("下書き一覧")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月24日 社員A")))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("2026年6月19日 社員B"))));
		assertThat(repository.existsById(expired.getId())).isFalse();

		mockMvc.perform(get("/requests/drafts/{id}", active.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("下書きを削除")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("開始時間が未入力です")));

		mockMvc.perform(post("/requests/drafts/{id}/delete", active.getId()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule?month=2026-06"));
		assertThat(repository.existsById(active.getId())).isFalse();
	}

	@Test
	void confirmsAndPhysicallyDeletesPublishedRequest() throws Exception {
		ScheduleRequest request = repository.saveAndFlush(ScheduleRequest.published(
				LocalDate.of(2026, 6, 24), LocalTime.of(10, 0), LocalTime.of(11, 0),
				"社員A", WorkType.INSTALL));

		mockMvc.perform(get("/requests/{id}/cancel", request.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("依頼キャンセル確認")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月24日（水）")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("社員A")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("設置")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"name=\"version\" value=\"" + request.getVersion() + "\"")));

		mockMvc.perform(post("/requests/{id}/cancel", request.getId())
					.param("version", Long.toString(request.getVersion())))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule?month=2026-06"));
		assertThat(repository.existsById(request.getId())).isFalse();
	}

	@Test
	void requiresReconfirmationWhenRequestChangedAfterCancellationPageOpened() throws Exception {
		ScheduleRequest request = repository.saveAndFlush(ScheduleRequest.published(
				LocalDate.of(2026, 6, 24), LocalTime.of(10, 0), LocalTime.of(11, 0),
				"社員A", WorkType.INSTALL));
		long confirmedVersion = request.getVersion();

		request.changeRequesterName("社員B");
		repository.saveAndFlush(request);

		mockMvc.perform(post("/requests/{id}/cancel", request.getId())
					.param("version", Long.toString(confirmedVersion)))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/requests/" + request.getId() + "/cancel"))
				.andExpect(flash().attribute(
						"cancelError", "内容が変更されました。最新内容を確認してください"));
		assertThat(repository.existsById(request.getId())).isTrue();

		mockMvc.perform(get("/requests/{id}/cancel", request.getId())
					.flashAttr("cancelError", "内容が変更されました。最新内容を確認してください"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("社員B")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"内容が変更されました。最新内容を確認してください")));
	}

	@Test
	void redirectsToScheduleWhenRequestWasAlreadyDeleted() throws Exception {
		ScheduleRequest request = repository.saveAndFlush(ScheduleRequest.published(
				LocalDate.of(2026, 6, 24), LocalTime.of(10, 0), LocalTime.of(11, 0),
				"社員A", WorkType.INSTALL));
		Long id = request.getId();
		long version = request.getVersion();
		repository.deleteById(id);
		repository.flush();

		mockMvc.perform(post("/requests/{id}/cancel", id)
					.param("version", Long.toString(version)))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule"))
				.andExpect(flash().attribute("notice", "案件はすでに削除されています"));
	}

	@Test
	void createsRequestFromBlankCellAndOpensItAgain() throws Exception {
		mockMvc.perform(get("/schedule"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月 スケジュール")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"data-initial-focus-date=\"2026-06-24\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("/requests/new?date=2026-06-24")));

		mockMvc.perform(get("/requests/new").param("date", "2026-06-24"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月24日")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"id=\"destructive-actions\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"id=\"draft-delete-form\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"id=\"cancel-request-link\"")));

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

		MvcResult conflictResult = mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", "11:00")
					.param("endTime", "13:00")
					.param("workType", "DELIVERY")
					.param("requesterName", "社員B"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("その時間はすでに埋まっています")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"社員B\"")))
				.andReturn();
		String html = conflictResult.getResponse().getContentAsString();
		assertThat(tagWithAttribute(html, "a", "href", "/schedule?month=2026-06"))
				.contains("back-submit");
		assertThat(html).doesNotContain("<button type=\"submit\" class=\"back-submit\"");

		mockMvc.perform(get("/schedule").param("month", "2026-06"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月 スケジュール")));

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
	void mapsThirtyMinuteRequestsToCellsWithColorsAndContinuationArrows() {
		LocalDate workDate = LocalDate.of(2026, 6, 24);
		publishingService.publish(new PublishCommand(
				workDate, LocalTime.of(10, 0), LocalTime.of(11, 0), "社員A", WorkType.INSTALL));
		publishingService.publish(new PublishCommand(
				workDate, LocalTime.of(14, 0), LocalTime.of(15, 0), "社員B", WorkType.DELIVERY));

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
			repository.saveAndFlush(ScheduleRequest.published(
					workDate, start, start.plusMinutes(30), "社員" + index, WorkType.DELIVERY));
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
	void makesPastDatesReadOnlyFromScheduleThroughServiceLayer() throws Exception {
		LocalDate workDate = LocalDate.of(2026, 6, 19);
		ScheduleRequest past = repository.saveAndFlush(ScheduleRequest.published(
				workDate, LocalTime.of(10, 0), LocalTime.of(11, 0),
				"社員A", WorkType.INSTALL));

		mockMvc.perform(get("/schedule").param("month", "2026-06"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("/requests/new?date=2026-06-19"))))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"/requests/" + past.getId())));

		MvcResult formResult = mockMvc.perform(get("/requests/{id}", past.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"過去日の案件は閲覧専用です")))
				.andReturn();
		String html = formResult.getResponse().getContentAsString();
		assertThat(html).contains("閲覧専用</div>");
		assertThat(html).doesNotContain("入力内容は自動保存されます");
		assertThat(tagWithAttribute(html, "select", "name", "startTime")).contains("disabled");
		assertThat(tagWithAttribute(html, "input", "name", "requesterName")).contains("disabled");
		assertThat(tagWithAttribute(html, "div", "id", "destructive-actions")).contains("hidden");

		mockMvc.perform(get("/requests/new").param("date", "2026-06-19"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/requests/{id}/cancel", past.getId()))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/requests/autosave")
					.param("id", past.getId().toString())
					.param("version", Long.toString(past.getVersion()))
					.param("workDate", "2026-06-19")
					.param("startTime", "12:00")
					.param("endTime", "13:00")
					.param("requesterName", "社員B"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INVALID"))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("今日以降")));

		mockMvc.perform(post("/requests/{id}/cancel", past.getId())
					.param("version", Long.toString(past.getVersion())))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/requests/" + past.getId()));

		ScheduleRequest unchanged = repository.findById(past.getId()).orElseThrow();
		assertThat(unchanged.getRequesterName()).isEqualTo("社員A");
		assertThat(unchanged.getStartTime()).isEqualTo(LocalTime.of(10, 0));
	}

	@Test
	void publishesWithoutWorkTypeAndShowsIncompleteMarker() throws Exception {
		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", "10:00")
					.param("endTime", "11:00")
					.param("requesterName", "社員A"))
				.andExpect(status().is3xxRedirection());

		ScheduleRequest saved = repository.findAll().getFirst();
		assertThat(saved.getWorkType()).isNull();
		mockMvc.perform(get("/schedule").param("month", "2026-06"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("＊未入力")));
	}

	@Test
	void rejectsMinuteLevelTimesWithoutSaving() throws Exception {
		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", "14:01")
					.param("endTime", "15:00")
					.param("requesterName", "社員A"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("30分単位で入力してください")));

		assertThat(repository.count()).isZero();
	}

	@Test
	void rejectsDatesOutsideVisibleMonths() throws Exception {
		mockMvc.perform(get("/requests/new").param("date", "2026-10-07"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-10-07")
					.param("startTime", "10:00")
					.param("endTime", "11:00")
					.param("requesterName", "社員A"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("表示対象月")));

		assertThat(repository.count()).isZero();
	}

	@Test
	void rendersJapaneseWeekdayAndRequesterRequirement() throws Exception {
		MvcResult result = mockMvc.perform(get("/requests/new").param("date", "2026-06-24"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("2026年6月24日（水）")))
				.andReturn();
		String html = result.getResponse().getContentAsString();

		assertThat(tagWithAttribute(html, "span", "id", "requester-required"))
				.doesNotContain("hidden");
		assertThat(tagWithAttribute(html, "input", "name", "requesterName"))
				.contains("required");
	}

	@Test
	void requesterIsOptionalForReceiving() throws Exception {
		publishingService.publish(new PublishCommand(
				LocalDate.of(2026, 6, 24),
				LocalTime.of(8, 30),
				LocalTime.of(9, 0),
				null,
				WorkType.RECEIVING));
		ScheduleRequest saved = repository.findAll().getFirst();

		MvcResult result = mockMvc.perform(get("/requests/{id}", saved.getId()))
				.andExpect(status().isOk())
				.andReturn();
		String html = result.getResponse().getContentAsString();

		assertThat(tagWithAttribute(html, "span", "id", "requester-required"))
				.contains("hidden");
		assertThat(tagWithAttribute(html, "input", "name", "requesterName"))
				.doesNotContain("required");
	}

	@Test
	void rendersCompanionBeforeDetailsAndMakesAddressOptionalForCompanion() throws Exception {
		MvcResult result = mockMvc.perform(get("/requests/new").param("date", "2026-06-24"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("現場住所もしくは会社名")))
				.andReturn();
		String html = result.getResponse().getContentAsString();

		assertThat(html.indexOf("id=\"companion-toggle\""))
				.isBetween(html.indexOf("name=\"requesterName\""), html.indexOf("name=\"requestDetail\""));
		assertThat(html.indexOf("id=\"companion-fields\""))
				.isBetween(html.indexOf("id=\"companion-toggle\""), html.indexOf("name=\"requestDetail\""));

		MvcResult companionResult = mockMvc.perform(post("/requests/autosave")
					.param("workDate", "2026-06-24")
					.param("startTime", "13:00")
					.param("endTime", "14:00")
					.param("workType", "INSTALL")
					.param("requesterName", "社員A")
					.param("requestDetail", "架空の設置作業")
					.param("desiredArrivalTime", "午後ならいつでも")
					.param("companionRequired", "true")
					.param("meetingPlace", "名古屋支店")
					.param("departureTime", "12:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.missingFields").isEmpty())
				.andReturn();
		Long companionId = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(companionResult.getResponse().getContentAsString())
				.get("requestId").asLong();

		MvcResult companionForm = mockMvc.perform(get("/requests/{id}", companionId))
				.andExpect(status().isOk())
				.andReturn();
		String companionHtml = companionForm.getResponse().getContentAsString();
		assertThat(tagWithAttribute(companionHtml, "span", "id", "address-required"))
				.contains("hidden");
		assertThat(tagWithAttribute(companionHtml, "input", "name", "address"))
				.doesNotContain("required");
	}

	@Test
	void autosavesDetailsAndReturnsTheSameRequestIdentity() throws Exception {
		MvcResult first = mockMvc.perform(post("/requests/autosave")
					.param("workDate", "2026-06-24")
					.param("startTime", "13:00")
					.param("endTime", "14:00")
					.param("workType", "INSTALL")
					.param("requesterName", "社員A")
					.param("requestDetail", "架空の設置作業")
					.param("address", "愛知県豊田市架空町1-1")
					.param("desiredArrivalTime", "午後ならいつでも")
					.param("companionRequired", "true")
					.param("meetingPlace", "名古屋支店")
					.param("departureTime", "12:00")
					.param("vehicleName", "車両A")
					.param("dispatchStatus", "DISPATCHED")
					.param("note", "到着時に連絡"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SAVED"))
				.andExpect(jsonPath("$.entryState").value("PUBLISHED"))
				.andExpect(jsonPath("$.missingFields").isEmpty())
				.andReturn();

		com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(first.getResponse().getContentAsString());
		Long id = json.get("requestId").asLong();
		long version = json.get("version").asLong();

		mockMvc.perform(post("/requests/autosave")
					.param("id", id.toString())
					.param("version", Long.toString(version))
					.param("workDate", "2026-06-24")
					.param("startTime", "13:00")
					.param("endTime", "14:00")
					.param("workType", "INSTALL")
					.param("requesterName", "社員A")
					.param("requestDetail", "更新後の架空作業")
					.param("address", "愛知県豊田市架空町1-1")
					.param("desiredArrivalTime", "午後ならいつでも")
					.param("dispatchStatus", "UNANSWERED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestId").value(id));

		ScheduleRequest saved = repository.findById(id).orElseThrow();
		assertThat(saved.getRequestDetail()).isEqualTo("更新後の架空作業");
		assertThat(saved.isCompanionRequired()).isFalse();
		assertThat(saved.getMeetingPlace()).isNull();
		assertThat(repository.count()).isOne();
	}

	private String tagWithAttribute(
			String html, String tagName, String attributeName, String attributeValue) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("<" + tagName + "[^>]*" + attributeName + "=\\\""
						+ java.util.regex.Pattern.quote(attributeValue) + "\\\"[^>]*>")
				.matcher(html);
		if (!matcher.find()) {
			throw new AssertionError("Tag not found: " + tagName + "[" + attributeName + "]");
		}
		return matcher.group();
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
