package com.yuyadev.schedulesystem.schedule;

import java.time.LocalTime;
import java.util.List;

public record TimeRowView(
		LocalTime startTime, LocalTime endTime, String label, List<ScheduleCellView> cells) {}
