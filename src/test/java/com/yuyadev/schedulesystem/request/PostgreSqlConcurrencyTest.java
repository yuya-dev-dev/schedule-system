package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
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

	private PublishResult publishAfterSignal(
			PublishCommand command, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent publish did not start in time");
		}
		return publishingService.publish(command);
	}
}
