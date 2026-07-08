package com.yuyadev.schedulesystem.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.yuyadev.schedulesystem.request.DraftReason;
import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Import(E2eClockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase4PerformanceTest {

	private static final List<LocalDate> WORK_DATES = List.of(
			LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 5),
			LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 12),
			LocalDate.of(2026, 6, 17), LocalDate.of(2026, 6, 19),
			LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 26));

	@LocalServerPort
	private int port;

	@Autowired
	private ScheduleRequestRepository repository;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;

	@BeforeAll
	void launchBrowser() {
		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
	}

	@BeforeEach
	void prepareFixture() {
		repository.deleteAll();
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 900));
		page = context.newPage();
	}

	@AfterEach
	void cleanUp() {
		context.close();
		repository.deleteAll();
	}

	@AfterAll
	void closeBrowser() {
		browser.close();
		playwright.close();
	}

	@Test
	void perf001RendersMonthWithFiftyRequests() {
		List<ScheduleRequest> requests = new ArrayList<>();
		for (int index = 0; index < 50; index++) {
			LocalDate date = WORK_DATES.get(index / 18);
			LocalTime start = LocalTime.of(8, 30).plusMinutes((index % 18) * 30L);
			requests.add(ScheduleRequest.published(
					date, start, start.plusMinutes(30), "架空社員" + index, WorkType.DELIVERY));
		}
		repository.saveAllAndFlush(requests);

		PageMetrics metrics = measurePage("/schedule?month=2026-06");

		assertThat(page.locator(".occupied .schedule-cell-link")
				.filter(new Locator.FilterOptions().setHasText("架空社員"))
				.count()).isEqualTo(50);
		metrics.print("PERF-001", "1か月50案件");
	}

	@Test
	void perf002RendersFiftyDrafts() {
		List<ScheduleRequest> drafts = new ArrayList<>();
		for (int index = 0; index < 50; index++) {
			drafts.add(ScheduleRequest.draft(
					LocalDate.of(2026, 6, 24), null, null, "架空社員" + index, null,
					DraftReason.INCOMPLETE, "開始時間が未入力です"));
		}
		repository.saveAllAndFlush(drafts);

		PageMetrics metrics = measurePage("/schedule?month=2026-06");

		assertThat(page.locator(".draft-item").count()).isEqualTo(50);
		assertThat(page.locator(".draft-item a").count()).isEqualTo(50);
		metrics.print("PERF-002", "下書き50件");
	}

	@Test
	void perf003RendersAndOpensSixRequestsOnOneDay() {
		LocalDate date = LocalDate.of(2026, 6, 24);
		for (int index = 0; index < 6; index++) {
			LocalTime start = LocalTime.of(8, 30).plusMinutes(index * 60L);
			repository.save(ScheduleRequest.published(
					date, start, start.plusMinutes(30), "架空社員" + index, WorkType.INSTALL));
		}
		repository.flush();
		PageMetrics metrics = measurePage("/schedule?month=2026-06");

		assertThat(page.locator(".occupied .cell-main")
				.filter(new Locator.FilterOptions().setHasText("架空社員"))
				.count()).isEqualTo(6);
		long startedAt = System.nanoTime();
		page.locator("a[aria-label='案件を開く']")
				.filter(new Locator.FilterOptions().setHasText("架空社員"))
				.first()
				.click();
		page.waitForLoadState();
		long openMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
		assertThat(page.locator("#requesterName").inputValue()).startsWith("架空社員");
		metrics.print("PERF-003", "同日6件、詳細遷移 " + openMillis + "ms");
	}

	@Test
	void perf004KeepsOneRecordAfterTenConsecutiveAutosaves() throws Exception {
		Long requestId = null;
		long version = 0;
		List<Long> durations = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			Map<String, String> form = new LinkedHashMap<>();
			if (requestId != null) {
				form.put("id", requestId.toString());
			}
			form.put("version", Long.toString(version));
			form.put("workDate", "2026-06-24");
			form.put("startTime", "13:00");
			form.put("endTime", "14:00");
			form.put("workType", "INSTALL");
			form.put("requesterName", "架空社員A");
			form.put("requestDetail", "架空の設置作業 " + index);
			form.put("address", "愛知県名古屋市中区架空町1-1");
			form.put("desiredArrivalTime", "午後");

			long startedAt = System.nanoTime();
			HttpResponse<String> response = httpClient.send(
					HttpRequest.newBuilder(URI.create(url("/requests/autosave")))
							.header("Content-Type", "application/x-www-form-urlencoded")
							.POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
							.build(),
					HttpResponse.BodyHandlers.ofString());
			durations.add(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
			assertThat(response.statusCode()).isEqualTo(200);
			JsonNode json = objectMapper.readTree(response.body());
			assertThat(json.get("status").asText()).isEqualTo("SAVED");
			requestId = json.get("requestId").asLong();
			version = json.get("version").asLong();
		}

		assertThat(repository.count()).isOne();
		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
		printDurations("PERF-004", "10回連続自動保存", durations);
	}

	private PageMetrics measurePage(String path) {
		page.navigate(url(path));
		List<Long> totalMillis = new ArrayList<>();
		List<Long> domContentLoadedMillis = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			long startedAt = System.nanoTime();
			page.navigate(url(path));
			totalMillis.add(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
			Number dom = (Number) page.evaluate(
					"performance.getEntriesByType('navigation')[0].domContentLoadedEventEnd");
			domContentLoadedMillis.add(Math.round(dom.doubleValue()));
		}
		return new PageMetrics(totalMillis, domContentLoadedMillis);
	}

	private String formBody(Map<String, String> form) {
		return form.entrySet().stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.collect(java.util.stream.Collectors.joining("&"));
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private String url(String path) {
		return "http://127.0.0.1:" + port + path;
	}

	private static void printDurations(String id, String condition, List<Long> values) {
		List<Long> sorted = values.stream().sorted().toList();
		System.out.printf(
				"%s %s: median=%dms max=%dms%n",
				id, condition, sorted.get(sorted.size() / 2), sorted.getLast());
	}

	private record PageMetrics(List<Long> totalMillis, List<Long> domContentLoadedMillis) {
		private void print(String id, String condition) {
			printDurations(id, condition + " HTTP+描画", totalMillis);
			printDurations(id, condition + " DOMContentLoaded", domContentLoadedMillis);
		}
	}
}
