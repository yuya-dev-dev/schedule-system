package com.yuyadev.schedulesystem.schedule;

import java.time.LocalDate;

public record DayOffConfirmation(
		LocalDate workDate,
		String dateTitle,
		long publishedCount,
		long draftCount) {

	public long totalCount() {
		return publishedCount + draftCount;
	}

	public boolean hasRequests() {
		return totalCount() > 0;
	}
}
