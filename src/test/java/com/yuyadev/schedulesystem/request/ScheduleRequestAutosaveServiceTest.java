package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuyadev.schedulesystem.TestClockConfiguration;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
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
				.isFalse();
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
	void keepsPublishedRequestAndOriginalSlotWhenEditedIntoAConflict() {
		AutosaveResult first = service.save(
				null, 0, input("社員A", WorkType.INSTALL, "9:00", "10:00"));
		AutosaveResult second = service.save(
				null, 0, input("社員B", WorkType.DELIVERY, "11:00", "12:00"));

		AutosaveResult conflict = service.save(
				second.requestId(), second.version(),
				input("社員B", WorkType.DELIVERY, "9:30", "10:30"));

		ScheduleRequest unchanged = repository.findById(second.requestId()).orElseThrow();
		assertThat(conflict.status()).isEqualTo(AutosaveResult.Status.TIME_CONFLICT);
		assertThat(unchanged.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(unchanged.getStartTime()).isEqualTo(LocalTime.of(11, 0));
		assertThat(unchanged.getEndTime()).isEqualTo(LocalTime.of(12, 0));
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isZero();
		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isEqualTo(2);
		assertThat(first.status()).isEqualTo(AutosaveResult.Status.SAVED);
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

	@Test
	void keepsPublishedSlotWhileListFieldsAreTemporarilyMissing() {
		AutosaveResult created = service.save(
				null, 0, input("社員A", WorkType.INSTALL, "13:00", "14:00"));
		ScheduleRequest before = repository.findById(created.requestId()).orElseThrow();
		long originalVersion = before.getVersion();

		AutosaveResult rejected = service.save(
				created.requestId(), originalVersion,
				input(null, WorkType.INSTALL, "13:00", "14:00"));

		ScheduleRequest unchanged = repository.findById(created.requestId()).orElseThrow();
		assertThat(rejected.status()).isEqualTo(AutosaveResult.Status.INVALID);
		assertThat(rejected.entryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(rejected.missingFields()).contains("依頼者名");
		assertThat(rejected.message()).contains("元の予定を維持");
		assertThat(unchanged.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(unchanged.getRequesterName()).isEqualTo("社員A");
		assertThat(unchanged.getStartTime()).isEqualTo(LocalTime.of(13, 0));
		assertThat(unchanged.getEndTime()).isEqualTo(LocalTime.of(14, 0));
		assertThat(unchanged.getVersion()).isEqualTo(originalVersion);
	}

	@Test
	void savesDetailChangesWithoutReleasingPublishedSlot() {
		AutosaveResult created = service.save(
				null, 0, input("社員A", WorkType.INSTALL, "15:00", "16:00"));
		ScheduleRequestInput changed = new ScheduleRequestInput(
				WORK_DATE, LocalTime.of(15, 0), LocalTime.of(16, 0), WorkType.INSTALL,
				"社員A", "更新後の作業内容", "愛知県豊田市架空町", "17時まで",
				false, null, null, null, DispatchStatus.REQUIRED, "更新後の備考");

		AutosaveResult edited = service.save(
				created.requestId(), created.version(), changed);

		ScheduleRequest saved = repository.findById(created.requestId()).orElseThrow();
		assertThat(edited.status()).isEqualTo(AutosaveResult.Status.SAVED);
		assertThat(edited.entryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(saved.getRequestDetail()).isEqualTo("更新後の作業内容");
		assertThat(saved.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isZero();
	}

	@Test
	void identifiesPostgreSqlDeadlocksAsAutosaveConflicts() {
		CannotAcquireLockException publishedTimeDeadlock = new CannotAcquireLockException(
				"排他制約の検査でデッドロック",
				new SQLException(
						"ex_schedule_requests_published_time の検査中",
						"40P01"));
		CannotAcquireLockException nonDeadlock = new CannotAcquireLockException(
				"別のロックエラー",
				new SQLException("lock timeout", "55P03"));

		assertThat(ScheduleRequestAutosaveService.isPublishedTimeDeadlock(publishedTimeDeadlock))
				.isTrue();
		assertThat(ScheduleRequestAutosaveService.isPublishedTimeDeadlock(nonDeadlock))
				.isFalse();
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
