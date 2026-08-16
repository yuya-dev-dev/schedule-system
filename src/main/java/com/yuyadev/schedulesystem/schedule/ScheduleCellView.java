package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.request.ScheduleRequest;

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
		boolean dayOff) {

	public static ScheduleCellView available(boolean readOnly, String destinationUrl) {
		return new ScheduleCellView(
				null, false, false, null, null, false, 0, readOnly, destinationUrl, false);
	}

	public static ScheduleCellView occupied(
			ScheduleRequest request,
			boolean firstCell,
			int colorIndex,
			boolean readOnly) {
		return new ScheduleCellView(
				request.getId(),
				true,
				firstCell,
				request.getRequesterName(),
				request.getWorkType() == null ? null : request.getWorkType().getDisplayName(),
				request.hasMissingRequiredFields(),
				colorIndex,
				readOnly,
				"/requests/" + request.getId(),
				false);
	}

	public static ScheduleCellView dayOff(boolean firstCell) {
		return new ScheduleCellView(
				null, false, firstCell, null, null, false, 0, true, null, true);
	}
}
