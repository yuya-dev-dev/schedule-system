package com.yuyadev.schedulesystem.schedule;

import java.util.List;

public record MonthScheduleView(
		String title,
		String selectedMonth,
		List<MonthTabView> monthTabs,
		List<WorkDateView> workDates,
		List<TimeRowView> timeRows) {}
