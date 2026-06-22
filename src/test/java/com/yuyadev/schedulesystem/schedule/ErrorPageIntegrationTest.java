package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorPageIntegrationTest {

	@LocalServerPort
	private int port;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Test
	void rendersAnUnderstandablePageForMissingUrlsAndRequests() throws Exception {
		assertNotFound("/missing-page");
		assertNotFound("/requests/999999");
	}

	private void assertNotFound(String path) throws Exception {
		HttpResponse<String> response = httpClient.send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
						.header("Accept", "text/html")
						.GET()
						.build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.body())
				.contains("ページが見つかりません")
				.contains("指定された案件またはページは、削除されたか存在しません")
				.contains("スケジュール一覧へ戻る");
	}
}
