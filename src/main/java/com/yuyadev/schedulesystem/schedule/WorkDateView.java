package com.yuyadev.schedulesystem.schedule;

import java.time.LocalDate;

public record WorkDateView(
		LocalDate date, String monthDayLabel, String weekdayLabel, boolean past) {}
