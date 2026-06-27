package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuyadev.schedulesystem.TestClockConfiguration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
class ScheduleRequestWorkflowTest {

	private static final LocalDate WORK_DATE = LocalDate.of(2026, 6, 24);

	@Autowired
	private ScheduleRequestPublishingService publishingService;

	@Autowired
	private ScheduleRequestRepository repository;

	@AfterEach
	void deleteRequests() {
		repository.deleteAll();
	}

	@Test
	void savesAnOverlappingRequestAsDraftOnH2() {
		publishingService.publish(command(10, 0, 12, 0, "社員A"));

		PublishResult result =
				publishingService.publish(command(11, 0, 13, 0, "社員B"));

		assertThat(result.status()).isEqualTo(PublishResult.Status.TIME_CONFLICT);
		assertThat(result.requestId()).isNotNull();
		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
		ScheduleRequest draft = repository.findById(result.requestId()).orElseThrow();
		assertThat(draft.getDraftReason()).isEqualTo(DraftReason.TIME_CONFLICT);
		assertThat(draft.getDraftErrorDetail()).contains("10:00", "12:00");
		assertThat(draft.getRequesterName()).isEqualTo("社員B");
		assertThat(draft.getStartTime()).isEqualTo(LocalTime.of(11, 0));
		assertThat(draft.getEndTime()).isEqualTo(LocalTime.of(13, 0));
	}

	@Test
	void draftDoesNotReserveTimeAndIsRecheckedBeforePublishing() {
		Long draftId = publishingService.saveDraft(command(9, 0, 11, 0, "社員A"));

		PublishResult published =
				publishingService.publish(command(10, 0, 12, 0, "社員B"));
		PublishResult draftResult = publishingService.publishDraft(draftId);

		assertThat(published.status()).isEqualTo(PublishResult.Status.PUBLISHED);
		assertThat(draftResult.status()).isEqualTo(PublishResult.Status.TIME_CONFLICT);
		ScheduleRequest draft = repository.findById(draftId).orElseThrow();
		assertThat(draft.getEntryState()).isEqualTo(EntryState.DRAFT);
		assertThat(draft.getDraftReason()).isEqualTo(DraftReason.TIME_CONFLICT);
	}

	@Test
	void publishesACompleteDraftWhenThereIsNoConflict() {
		Long draftId = publishingService.saveDraft(command(14, 0, 15, 0, "社員A"));

		PublishResult result = publishingService.publishDraft(draftId);

		assertThat(result.status()).isEqualTo(PublishResult.Status.PUBLISHED);
		ScheduleRequest request = repository.findById(draftId).orElseThrow();
		assertThat(request.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(request.getDraftReason()).isNull();
	}

	private PublishCommand command(
			int startHour, int startMinute, int endHour, int endMinute, String requesterName) {
		return new PublishCommand(
				WORK_DATE,
				LocalTime.of(startHour, startMinute),
				LocalTime.of(endHour, endMinute),
				requesterName,
				WorkType.INSTALL);
	}
}
