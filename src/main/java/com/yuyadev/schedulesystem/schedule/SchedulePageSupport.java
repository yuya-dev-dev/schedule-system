package com.yuyadev.schedulesystem.schedule;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.util.StringUtils;

public final class SchedulePageSupport {

	private static final DateTimeFormatter DATE_TITLE =
			DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE);
	private static final String MONTH_SELECTION_ERROR = "正しい年と月を入力してください";
	private static final YearMonth MAXIMUM_MONTH = YearMonth.of(Year.MAX_VALUE, 12);

	private SchedulePageSupport() {}

	public static String dateTitle(LocalDate date) {
		return date == null ? "日付未指定" : date.format(DATE_TITLE);
	}

	public static String scheduleUrl(LocalDate date) {
		return date == null
				? "/schedule"
				: "/schedule?month=" + date.getYear()
						+ "-" + String.format("%02d", date.getMonthValue());
	}

	public static MonthSelection resolveMonth(
			String month,
			String year,
			String monthNumber,
			String fallbackMonth) {
		if (!StringUtils.hasText(month)
				&& !StringUtils.hasText(year)
				&& !StringUtils.hasText(monthNumber)
				&& fallbackMonth != null) {
			return MonthSelection.valid(fallbackMonth);
		}
		if (!StringUtils.hasText(year) && !StringUtils.hasText(monthNumber)) {
			return MonthSelection.valid(month);
		}
		try {
			int selectedYear = Integer.parseInt(year);
			int selectedMonth = Integer.parseInt(monthNumber);
			YearMonth selected = YearMonth.of(selectedYear, selectedMonth);
			if (!isSupportedMonth(selected)) {
				throw new DateTimeException("Month is outside the supported range");
			}
			return MonthSelection.valid(selected.toString());
		} catch (DateTimeException | NumberFormatException exception) {
			return MonthSelection.invalid(month == null ? fallbackMonth : month);
		}
	}

	static boolean isSupportedMonth(YearMonth month) {
		return month != null && month.getYear() >= 1 && month.isBefore(MAXIMUM_MONTH);
	}

	public record MonthSelection(String requestedMonth, String error) {

		private static MonthSelection valid(String requestedMonth) {
			return new MonthSelection(requestedMonth, null);
		}

		private static MonthSelection invalid(String requestedMonth) {
			return new MonthSelection(requestedMonth, MONTH_SELECTION_ERROR);
		}
	}
}
