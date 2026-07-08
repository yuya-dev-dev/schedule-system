package com.yuyadev.schedulesystem.request;

import java.util.List;

public record AutosaveResult(
		Status status,
		Long requestId,
		long version,
		EntryState entryState,
		String message,
		List<String> missingFields) {

	public enum Status {
		SAVED,
		TIME_CONFLICT,
		STALE,
		INVALID
	}

	public static AutosaveResult saved(ScheduleRequest request) {
		return from(Status.SAVED, request, "保存しました");
	}

	public static AutosaveResult timeConflict(ScheduleRequest request) {
		return from(Status.TIME_CONFLICT, request, "その時間はすでに埋まっています");
	}

	public static AutosaveResult stale(ScheduleRequest request) {
		return from(Status.STALE, request, "ほかの利用者が先に変更しました。画面を再読み込みしてください");
	}

	public static AutosaveResult invalid(String message) {
		return new AutosaveResult(Status.INVALID, null, 0, null, message, List.of());
	}

	public static AutosaveResult invalidPublishedEdit(
			ScheduleRequest request, List<String> missingFields) {
		return new AutosaveResult(
				Status.INVALID,
				request.getId(),
				request.getVersion(),
				request.getEntryState(),
				"未入力のため変更されませんでした。元の予定を維持しています",
				List.copyOf(missingFields));
	}

	private static AutosaveResult from(Status status, ScheduleRequest request, String message) {
		return new AutosaveResult(
				status,
				request.getId(),
				request.getVersion(),
				request.getEntryState(),
				message,
				request.missingRequiredFields());
	}
}
