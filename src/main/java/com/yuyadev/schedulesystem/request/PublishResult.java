package com.yuyadev.schedulesystem.request;

public record PublishResult(Status status, Long requestId, String message) {

	public enum Status {
		PUBLISHED,
		TIME_CONFLICT
	}

	public static PublishResult published(Long requestId) {
		return new PublishResult(Status.PUBLISHED, requestId, null);
	}

	public static PublishResult timeConflict(Long draftId) {
		return new PublishResult(Status.TIME_CONFLICT, draftId, "その時間はすでに埋まっています");
	}
}
