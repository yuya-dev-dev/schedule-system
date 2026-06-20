package com.yuyadev.schedulesystem.request;

public record DraftListItem(
		Long id,
		String workDate,
		String requesterName,
		String updatedAt,
		String reason,
		String detail) {}
