package com.yuyadev.schedulesystem.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_holidays")
public class CalendarHoliday {

	@Id
	@Column(name = "holiday_date", nullable = false)
	private LocalDate holidayDate;

	@Column(name = "holiday_name", nullable = false, length = 100)
	private String holidayName;

	@Column(nullable = false, length = 100)
	private String source;

	@Column(name = "synced_at", nullable = false)
	private LocalDateTime syncedAt;

	protected CalendarHoliday() {
	}

	public CalendarHoliday(
			LocalDate holidayDate, String holidayName, String source, LocalDateTime syncedAt) {
		this.holidayDate = holidayDate;
		this.holidayName = holidayName;
		this.source = source;
		this.syncedAt = syncedAt;
	}

	public LocalDate getHolidayDate() {
		return holidayDate;
	}

	public String getHolidayName() {
		return holidayName;
	}

	public String getSource() {
		return source;
	}

	public LocalDateTime getSyncedAt() {
		return syncedAt;
	}
}
