package com.yuyadev.schedulesystem.holiday;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CabinetOfficeHolidayClient implements HolidayDataSource {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/M/d");
	private static final Charset WINDOWS_31J = Charset.forName("MS932");

	private final HttpClient httpClient;
	private final URI sourceUri;

	@Autowired
	public CabinetOfficeHolidayClient(
			@Value("${schedule.holidays.source-url}") String sourceUrl) {
		this(HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build(), URI.create(sourceUrl));
	}

	CabinetOfficeHolidayClient(HttpClient httpClient, URI sourceUri) {
		this.httpClient = httpClient;
		this.sourceUri = sourceUri;
	}

	@Override
	public List<HolidayDefinition> fetch() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(sourceUri)
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
		HttpResponse<byte[]> response = httpClient.send(
				request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			throw new IOException("祝日データの取得に失敗しました: HTTP " + response.statusCode());
		}
		return parse(response.body());
	}

	List<HolidayDefinition> parse(byte[] content) throws IOException {
		String csv = decode(content);
		List<HolidayDefinition> holidays = new ArrayList<>();
		for (String line : csv.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			String[] columns = line.split(",", 2);
			if (columns.length != 2) {
				continue;
			}
			try {
				LocalDate date = LocalDate.parse(unquote(columns[0]), DATE_FORMAT);
				String name = unquote(columns[1]);
				if (!name.isBlank()) {
					holidays.add(new HolidayDefinition(date, name));
				}
			} catch (DateTimeParseException ignored) {
				// The official CSV starts with a header row.
			}
		}
		if (holidays.isEmpty()) {
			throw new IOException("祝日データに有効な日付がありません");
		}
		return List.copyOf(holidays);
	}

	private String decode(byte[] content) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(content))
					.toString();
		} catch (CharacterCodingException exception) {
			return new String(content, WINDOWS_31J);
		}
	}

	private String unquote(String value) {
		String trimmed = value.trim().replace("\uFEFF", "");
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1).trim();
		}
		return trimmed;
	}
}
