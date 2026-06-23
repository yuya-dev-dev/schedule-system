package com.yuyadev.schedulesystem.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "schedule_day_offs")
public class ScheduleDayOff {

	@Id
	@Column(name = "work_date", nullable = false)
	private LocalDate workDate;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected ScheduleDayOff() {
	}

	public ScheduleDayOff(LocalDate workDate) {
		this.workDate = Objects.requireNonNull(workDate);
	}

	public LocalDate getWorkDate() {
		return workDate;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
