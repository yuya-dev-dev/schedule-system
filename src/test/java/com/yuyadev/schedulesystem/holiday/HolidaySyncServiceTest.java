package com.yuyadev.schedulesystem.holiday;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(HolidaySyncServiceTest.TestClockConfiguration.class)
class HolidaySyncServiceTest {

	@Autowired
	private CalendarHolidayRepository repository;

	@Autowired
	private HolidayCalendarService calendarService;

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
	}

	@Test
	void syncsWhenCacheIsEmpty() {
		FakeHolidayDataSource dataSource = new FakeHolidayDataSource(List.of(
				new HolidayDefinition(LocalDate.of(2026, 1, 1), "元日"),
				new HolidayDefinition(LocalDate.of(2026, 2, 11), "建国記念の日")));
		HolidaySyncService service = new HolidaySyncService(dataSource, calendarService);

		HolidaySyncService.SyncResult result = service.syncIfStale();

		assertThat(result).isEqualTo(HolidaySyncService.SyncResult.UPDATED);
		assertThat(repository.findAll())
				.extracting(CalendarHoliday::getHolidayDate)
				.containsExactlyInAnyOrder(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 11));
		assertThat(dataSource.calls).isEqualTo(1);
	}

	@Test
	void skipsSyncWhenCacheIsFresh() {
		repository.save(new CalendarHoliday(
				LocalDate.of(2026, 1, 1), "元日", "test",
				LocalDateTime.of(2026, 6, 20, 10, 0)));
		FakeHolidayDataSource dataSource = new FakeHolidayDataSource(List.of(
				new HolidayDefinition(LocalDate.of(2026, 2, 11), "建国記念の日")));
		HolidaySyncService service = new HolidaySyncService(dataSource, calendarService);

		HolidaySyncService.SyncResult result = service.syncIfStale();

		assertThat(result).isEqualTo(HolidaySyncService.SyncResult.FRESH_CACHE);
		assertThat(repository.findAll())
				.extracting(CalendarHoliday::getHolidayDate)
				.containsExactly(LocalDate.of(2026, 1, 1));
		assertThat(dataSource.calls).isZero();
	}

	@Test
	void keepsExistingCacheWhenFetchFails() {
		repository.save(new CalendarHoliday(
				LocalDate.of(2026, 1, 1), "元日", "test",
				LocalDateTime.of(2026, 6, 1, 10, 0)));
		HolidaySyncService service = new HolidaySyncService(
				new FailingHolidayDataSource(), calendarService);

		HolidaySyncService.SyncResult result = service.syncIfStale();

		assertThat(result).isEqualTo(HolidaySyncService.SyncResult.FAILED_USING_CACHE);
		assertThat(repository.findAll())
				.extracting(CalendarHoliday::getHolidayDate)
				.containsExactly(LocalDate.of(2026, 1, 1));
	}

	private static class FakeHolidayDataSource implements HolidayDataSource {
		private final List<HolidayDefinition> holidays;
		private int calls;

		FakeHolidayDataSource(List<HolidayDefinition> holidays) {
			this.holidays = holidays;
		}

		@Override
		public List<HolidayDefinition> fetch() {
			calls++;
			return holidays;
		}
	}

	private static class FailingHolidayDataSource implements HolidayDataSource {
		@Override
		public List<HolidayDefinition> fetch() throws IOException {
			throw new IOException("network down");
		}
	}

	@TestConfiguration
	static class TestClockConfiguration {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(
					Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
		}
	}
}
