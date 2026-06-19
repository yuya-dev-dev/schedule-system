package com.yuyadev.schedulesystem.request;

import java.time.LocalDate;
import java.time.LocalTime;

public record PublishCommand(
		LocalDate workDate,
		LocalTime startTime,
		LocalTime endTime,
		String requesterName,
		WorkType workType) {}
