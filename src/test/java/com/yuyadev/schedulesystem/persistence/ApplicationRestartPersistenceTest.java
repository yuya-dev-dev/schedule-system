package com.yuyadev.schedulesystem.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuyadev.schedulesystem.ScheduleSystemApplication;
import com.yuyadev.schedulesystem.request.AutosaveResult;
import com.yuyadev.schedulesystem.request.DispatchStatus;
import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestAutosaveService;
import com.yuyadev.schedulesystem.request.ScheduleRequestInput;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationRestartPersistenceTest {

	private static final LocalDate WORK_DATE = LocalDate.of(2026, 6, 24);

	@TempDir
	Path temporaryDirectory;

	@Test
	void preservesPublishedRequestAndFlywayHistoryAcrossApplicationRestart() throws Exception {
		String databasePath = temporaryDirectory.resolve("schedule-system-restart")
				.toAbsolutePath().toString().replace('\\', '/');
		String databaseUrl = "jdbc:h2:file:" + databasePath
				+ ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
		Long requestId;
		int migrationCount;

		try (ConfigurableApplicationContext firstApplication = startApplication(databaseUrl)) {
			ScheduleRequestAutosaveService autosaveService =
					firstApplication.getBean(ScheduleRequestAutosaveService.class);
			AutosaveResult saved = autosaveService.save(null, 0, new ScheduleRequestInput(
					WORK_DATE,
					LocalTime.of(10, 0),
					LocalTime.of(12, 0),
					WorkType.INSTALL,
					"架空社員A",
					"架空オフィスへコーヒーサーバー一式を設置",
					"愛知県名古屋市中区架空1-2-3",
					"午後かつ17時まで",
					true,
					"名古屋支店",
					LocalTime.of(9, 0),
					"車両A",
					DispatchStatus.DISPATCHED,
					"到着後に架空担当者へ連絡"));
			requestId = saved.requestId();
			migrationCount = successfulMigrationCount(firstApplication);

			assertThat(saved.status()).isEqualTo(AutosaveResult.Status.SAVED);
			assertThat(saved.entryState()).isEqualTo(EntryState.PUBLISHED);
			assertThat(requestId).isNotNull();
			assertThat(migrationCount).isPositive();
		}

		assertThat(Path.of(databasePath + ".mv.db")).exists();

		try (ConfigurableApplicationContext restartedApplication = startApplication(databaseUrl)) {
			ScheduleRequestRepository repository =
					restartedApplication.getBean(ScheduleRequestRepository.class);
			ScheduleRequest restored = repository.findById(requestId).orElseThrow();

			assertThat(restored.getEntryState()).isEqualTo(EntryState.PUBLISHED);
			assertThat(restored.getWorkDate()).isEqualTo(WORK_DATE);
			assertThat(restored.getStartTime()).isEqualTo(LocalTime.of(10, 0));
			assertThat(restored.getEndTime()).isEqualTo(LocalTime.of(12, 0));
			assertThat(restored.getRequesterName()).isEqualTo("架空社員A");
			assertThat(restored.getWorkType()).isEqualTo(WorkType.INSTALL);
			assertThat(restored.getRequestDetail())
					.isEqualTo("架空オフィスへコーヒーサーバー一式を設置");
			assertThat(restored.getAddress()).isEqualTo("愛知県名古屋市中区架空1-2-3");
			assertThat(restored.getDesiredArrivalTime()).isEqualTo("午後かつ17時まで");
			assertThat(restored.isCompanionRequired()).isTrue();
			assertThat(restored.getMeetingPlace()).isEqualTo("名古屋支店");
			assertThat(restored.getDepartureTime()).isEqualTo(LocalTime.of(9, 0));
			assertThat(restored.getVehicleName()).isEqualTo("車両A");
			assertThat(restored.getDispatchStatus()).isEqualTo(DispatchStatus.DISPATCHED);
			assertThat(restored.getNote()).isEqualTo("到着後に架空担当者へ連絡");
			assertThat(successfulMigrationCount(restartedApplication)).isEqualTo(migrationCount);

			int port = ((WebServerApplicationContext) restartedApplication)
					.getWebServer().getPort();
			assertPageContains(port, "/schedule?month=2026-06", "架空社員A", "設置");
			assertPageContains(port, "/requests/" + requestId, "架空社員A", "2026年6月24日");
		}
	}

	private ConfigurableApplicationContext startApplication(String databaseUrl) {
		return new SpringApplicationBuilder(
				ScheduleSystemApplication.class, FixedClockConfiguration.class)
				.web(WebApplicationType.SERVLET)
				.run(
						"--server.port=0",
						"--spring.datasource.url=" + databaseUrl,
						"--spring.datasource.username=sa",
						"--spring.datasource.password=",
						"--spring.jpa.hibernate.ddl-auto=validate",
						"--spring.flyway.locations=classpath:db/migration/common",
						"--spring.thymeleaf.cache=false");
	}

	private int successfulMigrationCount(ConfigurableApplicationContext application) {
		JdbcTemplate jdbcTemplate = application.getBean(JdbcTemplate.class);
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from flyway_schema_history where success = true", Integer.class);
		return count == null ? 0 : count;
	}

	private void assertPageContains(int port, String path, String... expectedValues)
			throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + port + path))
				.GET()
				.build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains(expectedValues);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedRestartTestClock() {
			return Clock.fixed(
					Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
		}
	}
}
