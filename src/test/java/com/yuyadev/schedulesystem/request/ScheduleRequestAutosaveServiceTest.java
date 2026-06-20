package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ScheduleRequestAutosaveServiceTest {

	private static final LocalDate WORK_DATE = LocalDate.of(2026, 6, 24);
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

	@Autowired
	private ScheduleRequestAutosaveService service;

	@Autowired
	private ScheduleRequestRepository repository;

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
	}

	@Test
	void updatesOneDraftUntilListRequirementsArePresent() {
		AutosaveResult first = service.save(null, 0, input(null, null, null, null));
		AutosaveResult second = service.save(
				first.requestId(), first.version(), input("社員A", null, "9:00", "10:00"));

		assertThat(first.entryState()).isEqualTo(EntryState.DRAFT);
		assertThat(second.entryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(second.requestId()).isEqualTo(first.requestId());
		assertThat(repository.count()).isOne();
		assertThat(second.missingFields()).isEmpty();
		assertThat(repository.findById(second.requestId()).orElseThrow().hasMissingRequiredFields())
				.isTrue();
	}

	@Test
	void keepsConflictingInputInTheSameDraftAcrossRetries() {
		AutosaveResult existing = service.save(null, 0, input("社員A", WorkType.INSTALL, "9:00", "10:00"));
		AutosaveResult conflict = service.save(null, 0, input("社員B", WorkType.DELIVERY, "9:30", "10:30"));
		AutosaveResult retry = service.save(
				conflict.requestId(), conflict.version(),
				input("社員B", WorkType.DELIVERY, "9:30", "10:30"));

		assertThat(existing.status()).isEqualTo(AutosaveResult.Status.SAVED);
		assertThat(conflict.status()).isEqualTo(AutosaveResult.Status.TIME_CONFLICT);
		assertThat(retry.status()).isEqualTo(AutosaveResult.Status.TIME_CONFLICT);
		assertThat(retry.requestId()).isEqualTo(conflict.requestId());
		assertThat(repository.count()).isEqualTo(2);
	}

	@Test
	void rejectsAnOldVersionWithoutOverwritingLatestInput() {
		AutosaveResult first = service.save(null, 0, input("社員A", WorkType.INSTALL, "11:00", "12:00"));
		AutosaveResult latest = service.save(
				first.requestId(), first.version(), input("社員B", WorkType.INSTALL, "11:00", "12:00"));
		AutosaveResult stale = service.save(
				first.requestId(), first.version(), input("社員C", WorkType.INSTALL, "11:00", "12:00"));
		AutosaveResult retriedWithOldVersion = service.save(
				first.requestId(), first.version(), input("社員C", WorkType.INSTALL, "11:00", "12:00"));

		assertThat(latest.status()).isEqualTo(AutosaveResult.Status.SAVED);
		assertThat(stale.status()).isEqualTo(AutosaveResult.Status.STALE);
		assertThat(retriedWithOldVersion.status()).isEqualTo(AutosaveResult.Status.STALE);
		assertThat(repository.findById(first.requestId()).orElseThrow().getRequesterName())
				.isEqualTo("社員B");
	}

	@Test
	void keepsAnEditedPublishedRequestPublishedWhenThereIsNoConflict() {
		AutosaveResult created = service.save(
				null, 0, input("社員A", WorkType.INSTALL, "13:00", "14:00"));

		AutosaveResult edited = service.save(
				created.requestId(), created.version(),
				input("社員B", WorkType.DELIVERY, "13:00", "14:00"));

		assertThat(edited.status()).isEqualTo(AutosaveResult.Status.SAVED);
		assertThat(edited.entryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isZero();
		assertThat(repository.findById(created.requestId()).orElseThrow().getRequesterName())
				.isEqualTo("社員B");
	}

	private ScheduleRequestInput input(
			String requester, WorkType workType, String start, String end) {
		return new ScheduleRequestInput(
				WORK_DATE,
				start == null ? null : LocalTime.parse(start, TIME_FORMAT),
				end == null ? null : LocalTime.parse(end, TIME_FORMAT),
				workType,
				requester,
				workType == null ? null : "架空の作業内容",
				workType == null ? null : "愛知県名古屋市中区架空町1-1",
				workType == null ? null : "午後",
				false,
				null,
				null,
				null,
				DispatchStatus.UNANSWERED,
				null);
	}
}
