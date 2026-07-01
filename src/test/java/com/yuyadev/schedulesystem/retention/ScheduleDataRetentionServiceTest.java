package com.yuyadev.schedulesystem.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuyadev.schedulesystem.holiday.CalendarHoliday;
import com.yuyadev.schedulesystem.holiday.CalendarHolidayRepository;
import com.yuyadev.schedulesystem.request.DraftReason;
import com.yuyadev.schedulesystem.request.DispatchStatus;
import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestInput;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import com.yuyadev.schedulesystem.schedule.ScheduleDayOff;
import com.yuyadev.schedulesystem.schedule.ScheduleDayOffRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "schedule.retention.enabled=false")
@ActiveProfiles("test")
@Import(ScheduleDataRetentionServiceTest.FixedClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ScheduleDataRetentionServiceTest {

	@Autowired
	private ScheduleDataRetentionService service;

	@Autowired
	private ScheduleRequestRepository requestRepository;

	@Autowired
	private ScheduleDayOffRepository dayOffRepository;

	@Autowired
	private CalendarHolidayRepository holidayRepository;

	@AfterEach
	void cleanUp() {
		requestRepository.deleteAll();
		dayOffRepository.deleteAll();
		holidayRepository.deleteAll();
	}

	@Test
	void deletesOnlyScheduleDataBeforeOneMonthCutoffAndKeepsHolidayCache(
			CapturedOutput output) {
		ScheduleRequest expiredPublished = savePublished(
				LocalDate.of(2026, 5, 29), "社員古い公開");
		ScheduleRequest cutoffPublished = savePublished(
				LocalDate.of(2026, 6, 1), "社員境界公開");
		ScheduleRequest futurePublished = savePublished(
				LocalDate.of(2026, 7, 3), "社員未来公開");
		ScheduleRequest expiredDraft = saveDetailedDraft(
				LocalDate.of(2026, 5, 29), "社員古い下書き");
		ScheduleRequest cutoffDraft = saveDraft(
				LocalDate.of(2026, 6, 1), "社員境界下書き");
		ScheduleDayOff expiredDayOff = dayOffRepository.saveAndFlush(
				new ScheduleDayOff(LocalDate.of(2026, 5, 29)));
		ScheduleDayOff cutoffDayOff = dayOffRepository.saveAndFlush(
				new ScheduleDayOff(LocalDate.of(2026, 6, 1)));
		ScheduleDayOff futureDayOff = dayOffRepository.saveAndFlush(
				new ScheduleDayOff(LocalDate.of(2026, 7, 3)));
		CalendarHoliday oldHoliday = holidayRepository.saveAndFlush(new CalendarHoliday(
				LocalDate.of(2026, 5, 29), "架空祝日", "test",
				LocalDateTime.of(2026, 5, 1, 9, 0)));

		ScheduleDataRetentionService.RetentionCleanupResult result =
				service.deleteExpiredScheduleData();

		assertThat(result.publishedRequests()).isEqualTo(1);
		assertThat(result.drafts()).isEqualTo(1);
		assertThat(result.dayOffs()).isEqualTo(1);
		assertThat(result.totalDeleted()).isEqualTo(3);
		assertThat(requestRepository.existsById(expiredPublished.getId())).isFalse();
		assertThat(requestRepository.existsById(expiredDraft.getId())).isFalse();
		assertThat(requestRepository.existsById(cutoffPublished.getId())).isTrue();
		assertThat(requestRepository.existsById(cutoffDraft.getId())).isTrue();
		assertThat(requestRepository.existsById(futurePublished.getId())).isTrue();
		assertThat(dayOffRepository.existsById(expiredDayOff.getWorkDate())).isFalse();
		assertThat(dayOffRepository.existsById(cutoffDayOff.getWorkDate())).isTrue();
		assertThat(dayOffRepository.existsById(futureDayOff.getWorkDate())).isTrue();
		assertThat(holidayRepository.existsById(oldHoliday.getHolidayDate())).isTrue();
		assertThat(requestRepository.findAll())
				.extracting(ScheduleRequest::getEntryState)
				.containsExactlyInAnyOrder(
						EntryState.PUBLISHED, EntryState.PUBLISHED, EntryState.DRAFT);
		assertThat(output).contains(
				"publishedRequests=1", "drafts=1", "dayOffs=1");
		assertThat(output).doesNotContain(
				"社員古い公開",
				"社員古い下書き",
				"架空祝日",
				"架空詳細コーヒーサーバー",
				"架空住所中区丸の内",
				"架空備考",
				"架空集合場所",
				"架空車両");
	}

	@Test
	void returnsZeroCountsWhenNothingIsExpired() {
		savePublished(LocalDate.of(2026, 6, 1), "社員境界公開");
		saveDraft(LocalDate.of(2026, 7, 3), "社員未来下書き");
		dayOffRepository.saveAndFlush(new ScheduleDayOff(LocalDate.of(2026, 6, 1)));

		ScheduleDataRetentionService.RetentionCleanupResult result =
				service.deleteExpiredScheduleData();

		assertThat(result.totalDeleted()).isZero();
		assertThat(requestRepository.count()).isEqualTo(2);
		assertThat(dayOffRepository.count()).isEqualTo(1);
	}

	private ScheduleRequest savePublished(LocalDate date, String requester) {
		return requestRepository.saveAndFlush(ScheduleRequest.published(
				date, LocalTime.of(10, 0), LocalTime.of(11, 0), requester, WorkType.INSTALL));
	}

	private ScheduleRequest saveDraft(LocalDate date, String requester) {
		return requestRepository.saveAndFlush(ScheduleRequest.draft(
				date, null, null, requester, null, DraftReason.INCOMPLETE, "入力不足"));
	}

	private ScheduleRequest saveDetailedDraft(LocalDate date, String requester) {
		return requestRepository.saveAndFlush(ScheduleRequest.draft(new ScheduleRequestInput(
				date,
				LocalTime.of(13, 0),
				LocalTime.of(14, 0),
				WorkType.INSTALL,
				requester,
				"架空詳細コーヒーサーバー",
				"架空住所中区丸の内",
				"午後ならいつでも",
				true,
				"架空集合場所",
				LocalTime.of(12, 30),
				"架空車両",
				DispatchStatus.REQUIRED,
				"架空備考")));
	}

	@TestConfiguration
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(
					Instant.parse("2026-07-01T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
		}
	}
}
