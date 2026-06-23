package com.yuyadev.schedulesystem.schedule;

public record ScheduleCellView(
		Long requestId,
		boolean occupied,
		boolean firstCell,
		String requesterName,
		String workTypeName,
		boolean incomplete,
		int colorIndex,
		boolean readOnly,
		String destinationUrl,
		boolean dayOff) {}
