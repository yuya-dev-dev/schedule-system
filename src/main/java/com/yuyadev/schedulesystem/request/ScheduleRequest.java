package com.yuyadev.schedulesystem.request;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(name = "schedule_requests")
public class ScheduleRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private LocalDate workDate;

	private LocalTime startTime;

	private LocalTime endTime;

	private String requesterName;

	@Enumerated(EnumType.STRING)
	private WorkType workType;

	@Enumerated(EnumType.STRING)
	private EntryState entryState;

	@Enumerated(EnumType.STRING)
	private DraftReason draftReason;

	private String draftErrorDetail;

	@Version
	private long version;

	protected ScheduleRequest() {}

	private ScheduleRequest(
			LocalDate workDate,
			LocalTime startTime,
			LocalTime endTime,
			String requesterName,
			WorkType workType,
			EntryState entryState,
			DraftReason draftReason,
			String draftErrorDetail) {
		this.workDate = Objects.requireNonNull(workDate);
		this.startTime = startTime;
		this.endTime = endTime;
		this.requesterName = requesterName;
		this.workType = workType;
		this.entryState = Objects.requireNonNull(entryState);
		this.draftReason = draftReason;
		this.draftErrorDetail = draftErrorDetail;
	}

	public static ScheduleRequest published(
			LocalDate workDate,
			LocalTime startTime,
			LocalTime endTime,
			String requesterName,
			WorkType workType) {
		Objects.requireNonNull(workDate);
		Objects.requireNonNull(startTime);
		Objects.requireNonNull(endTime);
		Objects.requireNonNull(workType);
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("End time must be after start time");
		}
		if (requiresRequester(workType) && isBlank(requesterName)) {
			throw new IllegalArgumentException("Requester name is required for this work type");
		}
		return new ScheduleRequest(
				workDate,
				startTime,
				endTime,
				normalize(requesterName),
				workType,
				EntryState.PUBLISHED,
				null,
				null);
	}

	public static ScheduleRequest draft(
			LocalDate workDate,
			LocalTime startTime,
			LocalTime endTime,
			String requesterName,
			WorkType workType,
			DraftReason draftReason,
			String draftErrorDetail) {
		return new ScheduleRequest(
				workDate,
				startTime,
				endTime,
				normalize(requesterName),
				workType,
				EntryState.DRAFT,
				Objects.requireNonNull(draftReason),
				draftErrorDetail);
	}

	private static boolean requiresRequester(WorkType workType) {
		return workType != WorkType.RECEIVING && workType != WorkType.PRODUCT_MANAGEMENT;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String normalize(String value) {
		return value == null ? null : value.trim();
	}

	public Long getId() {
		return id;
	}

	public LocalDate getWorkDate() {
		return workDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public String getRequesterName() {
		return requesterName;
	}

	public WorkType getWorkType() {
		return workType;
	}

	public EntryState getEntryState() {
		return entryState;
	}

	public DraftReason getDraftReason() {
		return draftReason;
	}

	public String getDraftErrorDetail() {
		return draftErrorDetail;
	}

	public long getVersion() {
		return version;
	}

	public void changeRequesterName(String requesterName) {
		if (requiresRequester(workType) && isBlank(requesterName)) {
			throw new IllegalArgumentException("Requester name is required for this work type");
		}
		this.requesterName = normalize(requesterName);
	}

	public void publish() {
		ScheduleRequest.published(workDate, startTime, endTime, requesterName, workType);
		this.entryState = EntryState.PUBLISHED;
		this.draftReason = null;
		this.draftErrorDetail = null;
	}

	public void markTimeConflict(String detail) {
		this.entryState = EntryState.DRAFT;
		this.draftReason = DraftReason.TIME_CONFLICT;
		this.draftErrorDetail = detail;
	}
}
