package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorPageIntegrationTest {
	private static final Pattern CSRF_TOKEN = Pattern.compile(
			"name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

	@LocalServerPort
	private int port;

	private final HttpClient httpClient = HttpClient.newBuilder()
			.cookieHandler(new CookieManager())
			.build();

	@Test
	void rendersAnUnderstandablePageForMissingUrlsAndRequests() throws Exception {
		assertNotFound("/missing-page");
		assertNotFound("/requests/999999");
	}

	@Test
	void rendersTheNotFoundPageForMissingDraftsAndCancellationConfirmations() throws Exception {
		assertNotFound("/requests/drafts/999999");
		assertNotFound("/requests/999999/cancel");
	}

	@Test
	void rendersTheNotFoundPageWhenDeletingAMissingDraft() throws Exception {
		String csrfToken = loadCsrfToken();
		HttpResponse<String> response = httpClient.send(
				HttpRequest.newBuilder(URI.create(url("/requests/drafts/999999/delete")))
						.header("Accept", "text/html")
						.header("X-CSRF-TOKEN", csrfToken)
						.POST(HttpRequest.BodyPublishers.noBody())
						.build(),
				HttpResponse.BodyHandlers.ofString());

		assertNotFoundResponse(response);
	}

	private String loadCsrfToken() throws Exception {
		LocalDate registrableDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
		HttpResponse<String> response = httpClient.send(
				HttpRequest.newBuilder(URI.create(
						url("/requests/new?date=" + registrableDate)))
						.GET()
						.build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		Matcher matcher = CSRF_TOKEN.matcher(response.body());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private void assertNotFound(String path) throws Exception {
		HttpResponse<String> response = httpClient.send(
				HttpRequest.newBuilder(URI.create(url(path)))
						.header("Accept", "text/html")
						.GET()
						.build(),
				HttpResponse.BodyHandlers.ofString());

		assertNotFoundResponse(response);
	}

	private void assertNotFoundResponse(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.body())
				.contains("ページが見つかりません")
				.contains("指定された案件またはページは、削除されたか存在しません")
				.contains("スケジュール一覧へ戻る");
	}

	private String url(String path) {
		return "http://127.0.0.1:" + port + path;
	}
}
