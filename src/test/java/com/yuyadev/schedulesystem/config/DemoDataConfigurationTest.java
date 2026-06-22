package com.yuyadev.schedulesystem.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

@SpringBootTest(properties =
		"spring.datasource.url=jdbc:h2:mem:demo-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "demo"})
@Import(DemoDataConfigurationTest.FixedClockConfiguration.class)
class DemoDataConfigurationTest {

	@Autowired
	private ScheduleRequestRepository repository;

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
	}

	@Test
	void loadsSixFictitiousWorkTypesOnlyForTheDemoProfile() {
		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isEqualTo(6);
		assertThat(repository.findAll())
				.extracting(request -> request.getWorkType())
				.containsExactlyInAnyOrder(WorkType.values());
		assertThat(repository.findAll())
				.allSatisfy(request -> assertThat(request.getWorkDate())
						.isIn(LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 26)));
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
