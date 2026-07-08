package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuyadev.schedulesystem.TestClockConfiguration;
import com.yuyadev.schedulesystem.holiday.CalendarHoliday;
import com.yuyadev.schedulesystem.holiday.CalendarHolidayRepository;
import com.yuyadev.schedulesystem.schedule.DayOffService;
import com.yuyadev.schedulesystem.schedule.ScheduleDayOff;
import com.yuyadev.schedulesystem.schedule.ScheduleDayOffRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
class RecurringFixedRequestServiceTest {

	private static final LocalTime FIXED_START = LocalTime.of(8, 30);
	private static final LocalTime FIXED_END = LocalTime.of(10, 0);

	@Autowired
	private RecurringFixedRequestService service;

	@Autowired
	private ScheduleRequestRepository requestRepository;

	@Autowired
	private RecurringFixedRequestSkipRepository skipRepository;

	@Autowired
	private ScheduleDayOffRepository dayOffRepository;

	@Autowired
	private CalendarHolidayRepository holidayRepository;

	@Autowired
	private RequestDeletionService deletionService;

	@Autowired
	private DayOffService dayOffService;

	@AfterEach
	void cleanUp() {
		skipRepository.deleteAll();
		requestRepository.deleteAll();
		dayOffRepository.deleteAll();
		holidayRepository.deleteAll();
	}

	@Test
	void createsFixedRequestsForCurrentAndNextMonthOnly() {
		service.ensureCurrentAndNextMonth();

		assertThat(requestRepository.findAll()).hasSize(12);
		assertFixedRequest(LocalDate.of(2026, 6, 24), WorkType.RECEIVING);
		assertFixedRequest(LocalDate.of(2026, 6, 26), WorkType.PRODUCT_MANAGEMENT);
		assertFixedRequest(LocalDate.of(2026, 7, 1), WorkType.RECEIVING);
		assertFixedRequest(LocalDate.of(2026, 7, 31), WorkType.PRODUCT_MANAGEMENT);
		assertThat(findFixedRequest(LocalDate.of(2026, 6, 19))).isEmpty();
		assertThat(findFixedRequest(LocalDate.of(2026, 8, 5))).isEmpty();
	}

	@Test
	void skipsHolidaysDayOffsAndExistingPublishedConflicts() {
		holidayRepository.save(new CalendarHoliday(
				LocalDate.of(2026, 6, 24), "架空の祝日", "test", LocalDateTime.now()));
		dayOffRepository.save(new ScheduleDayOff(LocalDate.of(2026, 6, 26)));
		requestRepository.save(ScheduleRequest.published(
				LocalDate.of(2026, 7, 1), LocalTime.of(9, 0), LocalTime.of(9, 30),
				"社員A", WorkType.INSTALL));

		service.ensureCurrentAndNextMonth();

		assertThat(findFixedRequest(LocalDate.of(2026, 6, 24))).isEmpty();
		assertThat(findFixedRequest(LocalDate.of(2026, 6, 26))).isEmpty();
		assertThat(findFixedRequest(LocalDate.of(2026, 7, 1))).isEmpty();
		assertFixedRequest(LocalDate.of(2026, 7, 3), WorkType.PRODUCT_MANAGEMENT);
	}

	@Test
	void doesNotRecreateIndividuallyCanceledFixedRequest() {
		service.ensureCurrentAndNextMonth();
		ScheduleRequest fixed = findFixedRequest(LocalDate.of(2026, 6, 24)).orElseThrow();

		RequestDeletionService.CancellationResult result =
				deletionService.cancelPublished(fixed.getId(), fixed.getVersion());
		service.ensureCurrentAndNextMonth();

		assertThat(result.status())
				.isEqualTo(RequestDeletionService.CancellationStatus.DELETED);
		assertThat(skipRepository.existsById(LocalDate.of(2026, 6, 24))).isTrue();
		assertThat(findFixedRequest(LocalDate.of(2026, 6, 24))).isEmpty();
		assertFixedRequest(LocalDate.of(2026, 6, 26), WorkType.PRODUCT_MANAGEMENT);
	}

	@Test
	void recreatesFixedRequestAfterDayOffIsUnset() {
		service.ensureCurrentAndNextMonth();

		dayOffService.setDayOff(LocalDate.of(2026, 6, 24));
		assertThat(findFixedRequest(LocalDate.of(2026, 6, 24))).isEmpty();
		assertThat(skipRepository.existsById(LocalDate.of(2026, 6, 24))).isFalse();

		dayOffService.unsetDayOff(LocalDate.of(2026, 6, 24));
		service.ensureCurrentAndNextMonth();

		assertFixedRequest(LocalDate.of(2026, 6, 24), WorkType.RECEIVING);
	}

	private void assertFixedRequest(LocalDate date, WorkType workType) {
		ScheduleRequest request = findFixedRequest(date).orElseThrow();
		assertThat(request.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(request.getStartTime()).isEqualTo(FIXED_START);
		assertThat(request.getEndTime()).isEqualTo(FIXED_END);
		assertThat(request.getRequesterName()).isNull();
		assertThat(request.getWorkType()).isEqualTo(workType);
	}

	private Optional<ScheduleRequest> findFixedRequest(LocalDate date) {
		return requestRepository.findAll().stream()
				.filter(request -> request.getWorkDate().equals(date))
				.filter(request -> request.getStartTime().equals(FIXED_START))
				.filter(request -> request.getEndTime().equals(FIXED_END))
				.filter(request -> request.getWorkType() == WorkType.RECEIVING
						|| request.getWorkType() == WorkType.PRODUCT_MANAGEMENT)
				.findFirst();
	}
}
