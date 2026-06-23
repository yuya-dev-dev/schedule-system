package com.yuyadev.schedulesystem.request;

public record RequestCopyResult(
		Status status,
		Long copiedRequestId,
		ScheduleRequestForm form,
		String message) {

	public enum Status {
		COPIED,
		TIME_CONFLICT,
		INVALID
	}

	public static RequestCopyResult copied(Long copiedRequestId) {
		return new RequestCopyResult(Status.COPIED, copiedRequestId, null, "コピーしました");
	}

	public static RequestCopyResult timeConflict(ScheduleRequestForm form, String message) {
		return new RequestCopyResult(Status.TIME_CONFLICT, null, form, message);
	}

	public static RequestCopyResult invalid(String message) {
		return new RequestCopyResult(Status.INVALID, null, null, message);
	}
}
