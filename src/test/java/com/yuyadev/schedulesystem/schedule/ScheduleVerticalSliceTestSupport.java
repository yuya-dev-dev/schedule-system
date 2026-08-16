package com.yuyadev.schedulesystem.schedule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuyadev.schedulesystem.holiday.CalendarHolidayRepository;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import com.yuyadev.schedulesystem.testsupport.SavedScheduleRequestFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

abstract class ScheduleVerticalSliceTestSupport {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ScheduleRequestRepository repository;

	@Autowired
	protected CalendarHolidayRepository holidayRepository;

	@Autowired
	protected ScheduleDayOffRepository dayOffRepository;

	@Autowired
	protected MonthScheduleService monthScheduleService;

	private SavedScheduleRequestFactory savedRequests;

	@BeforeEach
	void prepareRequestFactory() {
		savedRequests = new SavedScheduleRequestFactory(repository);
	}

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
		dayOffRepository.deleteAll();
		holidayRepository.deleteAll();
	}

	protected String tagWithAttribute(
			String html, String tagName, String attributeName, String attributeValue) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("<" + tagName + "[^>]*" + attributeName + "=\""
						+ java.util.regex.Pattern.quote(attributeValue) + "\"[^>]*>")
				.matcher(html);
		if (!matcher.find()) {
			throw new AssertionError("Tag not found: " + tagName + "[" + attributeName + "]");
		}
		return matcher.group();
	}

	protected void createRequest(String start, String end, String requester) throws Exception {
		mockMvc.perform(post("/requests/save")
					.param("workDate", "2026-06-24")
					.param("startTime", start)
					.param("endTime", end)
					.param("workType", "INSTALL")
					.param("requesterName", requester))
				.andExpect(status().is3xxRedirection());
	}

	protected ScheduleRequest createDetailedRequest(
			LocalDate workDate, String start, String end, String requester) throws Exception {
		MvcResult result = mockMvc.perform(post("/requests/autosave")
					.param("workDate", workDate.toString())
					.param("startTime", start)
					.param("endTime", end)
					.param("workType", "INSTALL")
					.param("requesterName", requester)
					.param("requestDetail", "架空の設置作業")
					.param("address", "愛知県名古屋市架空町1-1")
					.param("desiredArrivalTime", "午後ならいつでも")
					.param("companionRequired", "true")
					.param("meetingPlace", "名古屋支店")
					.param("departureTime", "09:30")
					.param("vehicleName", "車両A")
					.param("dispatchStatus", "DISPATCHED")
					.param("note", "架空の注意事項"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SAVED"))
				.andReturn();
		Long id = new ObjectMapper()
				.readTree(result.getResponse().getContentAsString())
				.get("requestId").asLong();
		return repository.findById(id).orElseThrow();
	}

	protected long countRequestsWithRequester(String requester) {
		return repository.findAll().stream()
				.filter(request -> requester.equals(request.getRequesterName()))
				.count();
	}

	protected ScheduleRequest savePublished(
			LocalDate workDate,
			LocalTime startTime,
			LocalTime endTime,
			String requesterName,
			WorkType workType) {
		return savedRequests.published(
				workDate, startTime, endTime, requesterName, workType);
	}

	protected ScheduleCellView cellAt(
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
