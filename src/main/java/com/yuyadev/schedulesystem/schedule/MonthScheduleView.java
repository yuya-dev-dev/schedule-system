package com.yuyadev.schedulesystem.schedule;

import java.util.List;

public record MonthScheduleView(
		String title,
		String selectedMonth,
		String initialFocusDate,
		List<MonthTabView> monthTabs,
		List<WorkDateView> workDates,
		List<TimeRowView> timeRows) {}
