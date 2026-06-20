package com.yuyadev.schedulesystem.request;

public enum DispatchStatus {
	UNANSWERED("未回答"),
	REQUIRED("出庫が必要"),
	DISPATCHED("すでに出庫済み");

	private final String displayName;

	DispatchStatus(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
