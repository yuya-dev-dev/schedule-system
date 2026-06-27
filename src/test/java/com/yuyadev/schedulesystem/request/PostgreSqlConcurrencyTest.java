package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuyadev.schedulesystem.TestClockConfiguration;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@Import(TestClockConfiguration.class)
@TestPropertySource(
		properties = {
			"spring.flyway.locations=classpath:db/migration/common,classpath:db/migration/postgresql",
			"spring.jpa.hibernate.ddl-auto=validate",
			"spring.jpa.open-in-view=false"
		})
class PostgreSqlConcurrencyTest {

	private static final LocalDate WORK_DATE = LocalDate.of(2026, 6, 24);

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17-alpine").withStartupTimeout(Duration.ofMinutes(2));

	@Autowired
	private ScheduleRequestPublishingService publishingService;

	@Autowired
	private ScheduleRequestAutosaveService autosaveService;

	@Autowired
	private ScheduleRequestRepository repository;

	@AfterEach
	void deleteRequests() {
		repository.deleteAll();
	}

	@Test
	void onlyOneOverlappingRequestIsPublishedWhenSavedConcurrently() throws Exception {
		PublishCommand firstCommand = new PublishCommand(
				WORK_DATE,
				LocalTime.of(10, 0),
				LocalTime.of(12, 0),
				"社員A",
				WorkType.INSTALL);
		PublishCommand overlappingCommand = new PublishCommand(
				WORK_DATE,
				LocalTime.of(11, 0),
				LocalTime.of(13, 0),
				"社員B",
				WorkType.DELIVERY);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<PublishResult> first =
					executor.submit(() -> publishAfterSignal(firstCommand, ready, start));
			Future<PublishResult> second =
					executor.submit(() -> publishAfterSignal(overlappingCommand, ready, start));

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<PublishResult> results = List.of(first.get(), second.get());
			List<PublishResult.Status> statuses =
					results.stream().map(PublishResult::status).toList();

			assertThat(statuses)
					.containsExactlyInAnyOrder(
							PublishResult.Status.PUBLISHED, PublishResult.Status.TIME_CONFLICT);
			assertThat(results)
					.filteredOn(result -> result.status() == PublishResult.Status.TIME_CONFLICT)
					.singleElement()
					.extracting(PublishResult::requestId)
					.isNotNull();
			assertThat(repository.count()).isEqualTo(2);
			assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
			assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
			ScheduleRequest conflictDraft = repository.findAll().stream()
					.filter(request -> request.getEntryState() == EntryState.DRAFT)
					.findFirst()
					.orElseThrow();
			assertThat(repository.findAll())
					.extracting(ScheduleRequest::getRequesterName)
					.containsExactlyInAnyOrder("社員A", "社員B");
			assertThat(conflictDraft.getDraftReason()).isEqualTo(DraftReason.TIME_CONFLICT);
			assertThat(conflictDraft.getDraftErrorDetail()).contains("既存案件", "と重複");
			if (conflictDraft.getRequesterName().equals("社員A")) {
				assertThat(conflictDraft.getStartTime()).isEqualTo(LocalTime.of(10, 0));
				assertThat(conflictDraft.getEndTime()).isEqualTo(LocalTime.of(12, 0));
			} else {
				assertThat(conflictDraft.getStartTime()).isEqualTo(LocalTime.of(11, 0));
				assertThat(conflictDraft.getEndTime()).isEqualTo(LocalTime.of(13, 0));
			}
		}
	}

	@Test
	void autosaveKeepsTheLosingConcurrentInputAsAConflictDraft() throws Exception {
		ScheduleRequestInput firstInput = input(
				LocalTime.of(10, 0), LocalTime.of(12, 0), "社員A", WorkType.INSTALL);
		ScheduleRequestInput overlappingInput = input(
				LocalTime.of(11, 0), LocalTime.of(13, 0), "社員B", WorkType.DELIVERY);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<AutosaveResult> first =
					executor.submit(() -> autosaveAfterSignal(firstInput, ready, start));
			Future<AutosaveResult> second =
					executor.submit(() -> autosaveAfterSignal(overlappingInput, ready, start));

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<AutosaveResult> results = List.of(first.get(), second.get());
			assertThat(results)
					.extracting(AutosaveResult::status)
					.containsExactlyInAnyOrder(
							AutosaveResult.Status.SAVED, AutosaveResult.Status.TIME_CONFLICT);
			assertThat(results)
					.filteredOn(result -> result.status() == AutosaveResult.Status.TIME_CONFLICT)
					.singleElement()
					.satisfies(result -> {
						assertThat(result.requestId()).isNotNull();
						assertThat(result.entryState()).isEqualTo(EntryState.DRAFT);
					});
		}

		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
		assertThat(repository.findAll())
				.extracting(ScheduleRequest::getRequesterName)
				.containsExactlyInAnyOrder("社員A", "社員B");
		ScheduleRequest conflictDraft = repository.findAll().stream()
				.filter(request -> request.getEntryState() == EntryState.DRAFT)
				.findFirst()
				.orElseThrow();
		assertThat(conflictDraft.getDraftReason()).isEqualTo(DraftReason.TIME_CONFLICT);
		assertThat(conflictDraft.getDraftErrorDetail()).contains("重複");
	}

	@Test
	void autosavePublishesAdjacentRequestsSavedConcurrently() throws Exception {
		ScheduleRequestInput firstInput = input(
				LocalTime.of(12, 0), LocalTime.of(14, 0), "社員A", WorkType.INSTALL);
		ScheduleRequestInput adjacentInput = input(
				LocalTime.of(14, 0), LocalTime.of(16, 0), "社員B", WorkType.COLLECT);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<AutosaveResult> first =
					executor.submit(() -> autosaveAfterSignal(firstInput, ready, start));
			Future<AutosaveResult> second =
					executor.submit(() -> autosaveAfterSignal(adjacentInput, ready, start));

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(first.get().status(), second.get().status()))
					.containsOnly(AutosaveResult.Status.SAVED);
		}

		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isEqualTo(2);
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isZero();
	}

	@Test
	void allowsARequestToStartWhenThePreviousRequestEnds() {
		PublishResult first = publishingService.publish(new PublishCommand(
				WORK_DATE,
				LocalTime.of(12, 0),
				LocalTime.of(14, 0),
				"社員A",
				WorkType.INSTALL));
		PublishResult next = publishingService.publish(new PublishCommand(
				WORK_DATE,
				LocalTime.of(14, 0),
				LocalTime.of(15, 0),
				"社員B",
				WorkType.COLLECT));

		assertThat(first.status()).isEqualTo(PublishResult.Status.PUBLISHED);
		assertThat(next.status()).isEqualTo(PublishResult.Status.PUBLISHED);
		assertThat(repository.count()).isEqualTo(2);
	}

	@Test
	void rejectsUpdateFromAStaleEntityVersion() {
		PublishResult result = publishingService.publish(new PublishCommand(
				WORK_DATE,
				LocalTime.of(13, 0),
				LocalTime.of(14, 0),
				"社員A",
				WorkType.COLLECT));

		ScheduleRequest firstCopy = repository.findById(result.requestId()).orElseThrow();
		ScheduleRequest staleCopy = repository.findById(result.requestId()).orElseThrow();
		firstCopy.changeRequesterName("社員B");
		repository.saveAndFlush(firstCopy);
		staleCopy.changeRequesterName("社員C");

		assertThatThrownBy(() -> repository.saveAndFlush(staleCopy))
				.isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void keepsPublishedRequestWhenAnotherRequestTargetsItsSlotDuringEditing() throws Exception {
		PublishResult existingResult = publishingService.publish(new PublishCommand(
				WORK_DATE, LocalTime.of(9, 0), LocalTime.of(10, 0),
				"社員A", WorkType.INSTALL));
		ScheduleRequest existing = repository.findById(existingResult.requestId()).orElseThrow();
		ScheduleRequestInput edit = new ScheduleRequestInput(
				WORK_DATE, LocalTime.of(9, 0), LocalTime.of(10, 0), WorkType.INSTALL,
				"社員A", "更新後の作業内容", "愛知県名古屋市架空町", "午前中",
				false, null, null, null, DispatchStatus.UNANSWERED, null);
		PublishCommand newcomer = new PublishCommand(
				WORK_DATE, LocalTime.of(9, 30), LocalTime.of(10, 30),
				"社員B", WorkType.DELIVERY);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<AutosaveResult> edited = executor.submit(() -> {
				ready.countDown();
				if (!start.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Concurrent edit did not start in time");
				}
				return autosaveService.save(existing.getId(), existing.getVersion(), edit);
			});
			Future<PublishResult> competing =
					executor.submit(() -> publishAfterSignal(newcomer, ready, start));

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(edited.get().status()).isEqualTo(AutosaveResult.Status.SAVED);
			assertThat(competing.get().status()).isEqualTo(PublishResult.Status.TIME_CONFLICT);
		}

		ScheduleRequest unchangedSlot = repository.findById(existing.getId()).orElseThrow();
		assertThat(unchangedSlot.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(unchangedSlot.getStartTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(unchangedSlot.getEndTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(unchangedSlot.getRequestDetail()).isEqualTo("更新後の作業内容");
		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
	}

	@Test
	void databaseConstraintRejectsDirectOverlappingInsert() {
		repository.saveAndFlush(ScheduleRequest.published(
				WORK_DATE, LocalTime.of(10, 0), LocalTime.of(11, 0),
				"社員A", WorkType.INSTALL));

		assertThatThrownBy(() -> repository.saveAndFlush(ScheduleRequest.published(
					WORK_DATE, LocalTime.of(10, 30), LocalTime.of(11, 30),
					"社員B", WorkType.DELIVERY)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void publishesDifferentSlotsConcurrently() throws Exception {
		PublishCommand firstCommand = new PublishCommand(
				WORK_DATE, LocalTime.of(13, 0), LocalTime.of(14, 0),
				"社員A", WorkType.INSTALL);
		PublishCommand secondCommand = new PublishCommand(
				WORK_DATE, LocalTime.of(14, 0), LocalTime.of(15, 0),
				"社員B", WorkType.COLLECT);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<PublishResult> first =
					executor.submit(() -> publishAfterSignal(firstCommand, ready, start));
			Future<PublishResult> second =
					executor.submit(() -> publishAfterSignal(secondCommand, ready, start));

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(first.get().status(), second.get().status()))
					.containsOnly(PublishResult.Status.PUBLISHED);
		}
		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isEqualTo(2);
	}

	@Test
	void incompletePublishedRequestStillReservesItsSlot() {
		repository.saveAndFlush(ScheduleRequest.published(
				WORK_DATE, LocalTime.of(15, 0), LocalTime.of(16, 0), "社員A", null));

		PublishResult result = publishingService.publish(new PublishCommand(
				WORK_DATE, LocalTime.of(15, 30), LocalTime.of(16, 30),
				"社員B", WorkType.DELIVERY));

		assertThat(result.status()).isEqualTo(PublishResult.Status.TIME_CONFLICT);
		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
	}

	private PublishResult publishAfterSignal(
			PublishCommand command, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent publish did not start in time");
		}
		return publishingService.publish(command);
	}

	private AutosaveResult autosaveAfterSignal(
			ScheduleRequestInput input, CountDownLatch ready, CountDownLatch start)
			throws InterruptedException {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent autosave did not start in time");
		}
		return autosaveService.save(null, 0, input);
	}

	private ScheduleRequestInput input(
			LocalTime start,
			LocalTime end,
			String requester,
			WorkType workType) {
		return new ScheduleRequestInput(
				WORK_DATE,
				start,
				end,
				workType,
				requester,
				"架空の作業内容",
				"愛知県名古屋市中区架空町1-1",
				"午後",
				false,
				null,
				null,
				null,
				DispatchStatus.UNANSWERED,
				null);
	}
}
