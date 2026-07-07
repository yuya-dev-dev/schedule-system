package com.yuyadev.schedulesystem.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "schedule.retention.enabled=false")
@ActiveProfiles("test")
@Import(ScheduleDataRetentionMonthEndServiceTest.MonthEndClockConfiguration.class)
class ScheduleDataRetentionMonthEndServiceTest {

	@Autowired
	private ScheduleDataRetentionService service;

	@Autowired
	private ScheduleRequestRepository repository;

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
	}

	@Test
	void keepsMinusMonthsBoundaryOnEndOfMonth() {
		ScheduleRequest expired = savePublished(LocalDate.of(2026, 2, 27));
		ScheduleRequest boundary = savePublished(LocalDate.of(2026, 2, 28));

		ScheduleDataRetentionService.RetentionCleanupResult result =
				service.deleteExpiredScheduleData();

		assertThat(result.publishedRequests()).isEqualTo(1);
		assertThat(repository.existsById(expired.getId())).isFalse();
		assertThat(repository.existsById(boundary.getId())).isTrue();
	}

	private ScheduleRequest savePublished(LocalDate date) {
		return repository.saveAndFlush(ScheduleRequest.published(
				date, LocalTime.of(10, 0), LocalTime.of(11, 0), "社員A", WorkType.INSTALL));
	}

	@TestConfiguration
	static class MonthEndClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(
					Instant.parse("2026-03-31T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
		}
	}
}
