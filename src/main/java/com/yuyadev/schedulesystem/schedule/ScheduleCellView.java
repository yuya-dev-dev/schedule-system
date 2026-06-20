package com.yuyadev.schedulesystem.schedule;

public record ScheduleCellView(
		Long requestId,
		boolean occupied,
		boolean firstCell,
		String requesterName,
		String workTypeName,
		int colorIndex,
		String destinationUrl) {}
