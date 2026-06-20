package com.yuyadev.schedulesystem.request;

public enum WorkType {
	INSTALL("設置"),
	COLLECT("回収"),
	EXCHANGE("交換"),
	DELIVERY("配達"),
	RECEIVING("入庫"),
	PRODUCT_MANAGEMENT("商品管理");

	private final String displayName;

	WorkType(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
