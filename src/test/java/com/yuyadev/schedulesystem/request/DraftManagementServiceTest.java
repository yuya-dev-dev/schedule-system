package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(DraftManagementServiceTest.FixedClockConfiguration.class)
class DraftManagementServiceTest {

	@Autowired
	private DraftManagementService service;

	@Autowired
	private ScheduleRequestRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
	}

	@Test
	void keepsTodaysDraftAndListsAllFutureMonthsByLatestUpdate() {
		ScheduleRequest today = saveDraft(LocalDate.of(2026, 6, 20), "社員A");
		ScheduleRequest june = saveDraft(LocalDate.of(2026, 6, 24), "社員B");
		ScheduleRequest july = saveDraft(LocalDate.of(2026, 7, 1), "社員C");
		setUpdatedAt(today.getId(), LocalDateTime.of(2026, 6, 20, 9, 0));
		setUpdatedAt(june.getId(), LocalDateTime.of(2026, 6, 20, 10, 0));
		setUpdatedAt(july.getId(), LocalDateTime.of(2026, 6, 20, 11, 0));

		List<DraftListItem> drafts = service.activeDrafts();

		assertThat(drafts).extracting(DraftListItem::id)
				.containsExactly(july.getId(), june.getId(), today.getId());
		assertThat(repository.existsById(today.getId())).isTrue();
	}

	private ScheduleRequest saveDraft(LocalDate date, String requester) {
		return repository.saveAndFlush(ScheduleRequest.draft(
				date, null, null, requester, null, DraftReason.INCOMPLETE, "入力不足"));
	}

	private void setUpdatedAt(Long id, LocalDateTime updatedAt) {
		jdbcTemplate.update(
				"UPDATE schedule_requests SET updated_at = ? WHERE id = ?",
				Timestamp.valueOf(updatedAt), id);
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
