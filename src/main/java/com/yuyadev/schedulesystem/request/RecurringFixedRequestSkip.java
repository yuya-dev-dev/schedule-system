package com.yuyadev.schedulesystem.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "recurring_fixed_request_skips")
public class RecurringFixedRequestSkip {

	@Id
	private LocalDate workDate;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected RecurringFixedRequestSkip() {}

	public RecurringFixedRequestSkip(LocalDate workDate) {
		this.workDate = workDate;
	}

	public LocalDate getWorkDate() {
		return workDate;
	}
}
