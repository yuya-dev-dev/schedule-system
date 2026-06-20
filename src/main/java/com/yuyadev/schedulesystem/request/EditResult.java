package com.yuyadev.schedulesystem.request;

public record EditResult(Status status, String message) {

	public enum Status {
		UPDATED,
		TIME_CONFLICT,
		STALE
	}

	public static EditResult updated() {
		return new EditResult(Status.UPDATED, null);
	}

	public static EditResult timeConflict() {
		return new EditResult(Status.TIME_CONFLICT, "その時間はすでに埋まっています");
	}

	public static EditResult stale() {
		return new EditResult(Status.STALE, "ほかの利用者が先に変更しました。最新の内容を確認してください");
	}
}
