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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CabinetOfficeHolidayClient implements HolidayDataSource {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/M/d");
	private static final Charset WINDOWS_31J = Charset.forName("MS932");
	private static final String DATE_HEADER = "国民の祝日・休日月日";
	private static final String NAME_HEADER = "国民の祝日・休日名称";

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
		List<String> lines = csv.lines().filter(line -> !line.isBlank()).toList();
		if (lines.isEmpty()) {
			throw new IOException("祝日データが空です");
		}
		validateHeader(lines.getFirst());

		List<HolidayDefinition> holidays = new ArrayList<>();
		Set<LocalDate> dates = new HashSet<>();
		for (int index = 1; index < lines.size(); index++) {
			int lineNumber = index + 1;
			String[] columns = lines.get(index).split(",", -1);
			if (columns.length != 2) {
				throw invalidRow(lineNumber, "列数が2ではありません", null);
			}
			try {
				LocalDate date = LocalDate.parse(unquote(columns[0]), DATE_FORMAT);
				String name = unquote(columns[1]);
				if (name.isBlank()) {
					throw invalidRow(lineNumber, "祝日名が空です", null);
				}
				if (!dates.add(date)) {
					throw invalidRow(lineNumber, "日付が重複しています", null);
				}
				holidays.add(new HolidayDefinition(date, name));
			} catch (DateTimeParseException exception) {
				throw invalidRow(lineNumber, "日付形式が不正です", exception);
			}
		}
		if (holidays.isEmpty()) {
			throw new IOException("祝日データに有効な日付がありません");
		}
		return List.copyOf(holidays);
	}

	private void validateHeader(String line) throws IOException {
		String[] columns = line.split(",", -1);
		if (columns.length != 2
				|| !DATE_HEADER.equals(unquote(columns[0]))
				|| !NAME_HEADER.equals(unquote(columns[1]))) {
			throw new IOException("祝日データのヘッダ形式が不正です");
		}
	}

	private IOException invalidRow(int lineNumber, String detail, Exception cause) {
		return new IOException(
				"祝日データの" + lineNumber + "行目が不正です: " + detail, cause);
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
