package com.yuyadev.schedulesystem.holiday;

import static org.assertj.core.api.Assertions.assertThat;

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
}
