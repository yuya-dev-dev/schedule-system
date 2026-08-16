package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;
import static com.yuyadev.schedulesystem.testsupport.ScheduleRequestInputTestBuilder.requestInput;

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
	private ScheduleRequestAutosaveService autosaveService;

	@Autowired
	private ScheduleRequestRepository repository;

	@AfterEach
	void deleteRequests() {
		repository.deleteAll();
	}

	@Test
	void savesAnOverlappingRequestAsDraftOnH2() {
		autosaveService.save(null, 0, input(10, 0, 12, 0, "社員A"));

		AutosaveResult result =
				autosaveService.save(null, 0, input(11, 0, 13, 0, "社員B"));

		assertThat(result.status()).isEqualTo(AutosaveResult.Status.TIME_CONFLICT);
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
		AutosaveResult initialDraft =
				autosaveService.save(null, 0, input(9, 0, 11, 0, null));

		AutosaveResult published =
				autosaveService.save(null, 0, input(10, 0, 12, 0, "社員B"));
		AutosaveResult draftResult = autosaveService.save(
				initialDraft.requestId(), initialDraft.version(), input(9, 0, 11, 0, "社員A"));

		assertThat(published.status()).isEqualTo(AutosaveResult.Status.SAVED);
		assertThat(draftResult.status()).isEqualTo(AutosaveResult.Status.TIME_CONFLICT);
		assertThat(draftResult.requestId()).isEqualTo(initialDraft.requestId());
		assertThat(repository.count()).isEqualTo(2);
		ScheduleRequest draft = repository.findById(initialDraft.requestId()).orElseThrow();
		assertThat(draft.getEntryState()).isEqualTo(EntryState.DRAFT);
		assertThat(draft.getDraftReason()).isEqualTo(DraftReason.TIME_CONFLICT);
	}

	@Test
	void publishesACompleteDraftWhenThereIsNoConflict() {
		AutosaveResult initialDraft =
				autosaveService.save(null, 0, input(14, 0, 15, 0, null));

		AutosaveResult result = autosaveService.save(
				initialDraft.requestId(),
				initialDraft.version(),
				input(14, 0, 15, 0, "社員A"));

		assertThat(result.status()).isEqualTo(AutosaveResult.Status.SAVED);
		assertThat(result.requestId()).isEqualTo(initialDraft.requestId());
		assertThat(repository.count()).isOne();
		ScheduleRequest request = repository.findById(initialDraft.requestId()).orElseThrow();
		assertThat(request.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(request.getDraftReason()).isNull();
	}

	private ScheduleRequestInput input(
			int startHour, int startMinute, int endHour, int endMinute, String requesterName) {
		return requestInput()
				.workDate(WORK_DATE)
				.startTime(LocalTime.of(startHour, startMinute))
				.endTime(LocalTime.of(endHour, endMinute))
				.requesterName(requesterName)
				.build();
	}
}
