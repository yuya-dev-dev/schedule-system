package com.yuyadev.schedulesystem.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CabinetOfficeHolidayClientTest {

	@Test
	void parsesOfficialCsvWithJapaneseEncoding() throws Exception {
		CabinetOfficeHolidayClient client = new CabinetOfficeHolidayClient(
				HttpClient.newHttpClient(), URI.create("https://example.invalid/holidays.csv"));
		String csv = "国民の祝日・休日月日,国民の祝日・休日名称\r\n"
				+ "2026/1/1,元日\r\n"
				+ "2026/2/11,建国記念の日\r\n";

		List<HolidayDefinition> holidays = client.parse(csv.getBytes(Charset.forName("MS932")));

		assertThat(holidays)
				.extracting(HolidayDefinition::date)
				.containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 11));
		assertThat(holidays)
				.extracting(HolidayDefinition::name)
				.containsExactly("元日", "建国記念の日");
	}

	@Test
	void rejectsMalformedDataRowInsteadOfReplacingCacheWithPartialData() {
		CabinetOfficeHolidayClient client = client();
		String csv = "国民の祝日・休日月日,国民の祝日・休日名称\n"
				+ "2026/1/1,元日\n"
				+ "invalid-date,架空の祝日\n";

		assertThatThrownBy(() -> client.parse(csv.getBytes(Charset.forName("MS932"))))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("3行目")
				.hasMessageContaining("日付形式");
	}

	@Test
	void rejectsUnexpectedHeader() {
		CabinetOfficeHolidayClient client = client();
		String csv = "date,name\n2026/1/1,元日\n";

		assertThatThrownBy(() -> client.parse(csv.getBytes(Charset.forName("MS932"))))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("ヘッダ形式");
	}

	@Test
	void rejectsRowsWithMissingColumns() {
		assertInvalidRow("2026/1/1", "列数が2ではありません");
	}

	@Test
	void rejectsRowsWithBlankNames() {
		assertInvalidRow("2026/1/1,", "祝日名が空です");
	}

	@Test
	void rejectsDuplicateDates() {
		CabinetOfficeHolidayClient client = client();
		String csv = "国民の祝日・休日月日,国民の祝日・休日名称\n"
				+ "2026/1/1,元日\n"
				+ "2026/1/1,重複した祝日\n";

		assertThatThrownBy(() -> client.parse(csv.getBytes(Charset.forName("MS932"))))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("3行目")
				.hasMessageContaining("日付が重複");
	}

	private void assertInvalidRow(String row, String expectedMessage) {
		CabinetOfficeHolidayClient client = client();
		String csv = "国民の祝日・休日月日,国民の祝日・休日名称\n" + row + "\n";

		assertThatThrownBy(() -> client.parse(csv.getBytes(Charset.forName("MS932"))))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("2行目")
				.hasMessageContaining(expectedMessage);
	}

	private CabinetOfficeHolidayClient client() {
		return new CabinetOfficeHolidayClient(
				HttpClient.newHttpClient(), URI.create("https://example.invalid/holidays.csv"));
	}
}
