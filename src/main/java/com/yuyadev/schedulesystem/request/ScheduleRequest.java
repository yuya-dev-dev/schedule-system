package com.yuyadev.schedulesystem.request;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "schedule_requests")
public class ScheduleRequest {
	private static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
	private static final LocalTime CLOSING_TIME = LocalTime.of(17, 30);
	private static final LocalTime LATEST_START_TIME = CLOSING_TIME.minusMinutes(30);
	private static final LocalTime EARLIEST_END_TIME = OPENING_TIME.plusMinutes(30);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private LocalDate workDate;

	private LocalTime startTime;

	private LocalTime endTime;

	private String requesterName;

	@Enumerated(EnumType.STRING)
	private WorkType workType;

	@Column(columnDefinition = "TEXT")
	private String requestDetail;

	private String address;

	private String desiredArrivalTime;

	private boolean companionRequired;

	private String meetingPlace;

	private LocalTime departureTime;

	private String vehicleName;

	@Enumerated(EnumType.STRING)
	private DispatchStatus dispatchStatus = DispatchStatus.UNANSWERED;

	@Column(columnDefinition = "TEXT")
	private String note;

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
		validatePublishedTime(startTime, endTime);
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
		validateDraftTime(startTime, endTime);
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

	public static ScheduleRequest draft(ScheduleRequestInput input) {
		ScheduleRequest request = draft(
				input.workDate(), input.startTime(), input.endTime(), input.requesterName(),
				input.workType(), DraftReason.INCOMPLETE, "入力不足");
		request.applyInput(input);
		return request;
	}

	public static boolean requiresRequester(WorkType workType) {
		return workType != WorkType.RECEIVING && workType != WorkType.PRODUCT_MANAGEMENT;
	}

	public static boolean isInternalWork(WorkType workType) {
		return workType == WorkType.RECEIVING || workType == WorkType.PRODUCT_MANAGEMENT;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String normalize(String value) {
		return value == null ? null : value.trim();
	}

	private static void validatePublishedTime(LocalTime startTime, LocalTime endTime) {
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("終了時間は開始時間より後にしてください");
		}
		if (!isScheduleSlot(startTime) || !isScheduleSlot(endTime)
				|| startTime.isBefore(OPENING_TIME) || endTime.isAfter(CLOSING_TIME)) {
			throw new IllegalArgumentException("時間は8:30から17:30の範囲で30分単位で入力してください");
		}
	}

	private static void validateDraftTime(LocalTime startTime, LocalTime endTime) {
		if (startTime != null && (!isScheduleSlot(startTime)
				|| startTime.isBefore(OPENING_TIME) || startTime.isAfter(LATEST_START_TIME))) {
			throw new IllegalArgumentException("開始時間は8:30から17:00の範囲で30分単位で入力してください");
		}
		if (endTime != null && (!isScheduleSlot(endTime)
				|| endTime.isBefore(EARLIEST_END_TIME) || endTime.isAfter(CLOSING_TIME))) {
			throw new IllegalArgumentException("終了時間は9:00から17:30の範囲で30分単位で入力してください");
		}
		if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("終了時間は開始時間より後にしてください");
		}
	}

	private static boolean isScheduleSlot(LocalTime time) {
		return time.getMinute() % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0;
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

	public String getRequestDetail() { return requestDetail; }

	public String getAddress() { return address; }

	public String getDesiredArrivalTime() { return desiredArrivalTime; }

	public boolean isCompanionRequired() { return companionRequired; }

	public String getMeetingPlace() { return meetingPlace; }

	public LocalTime getDepartureTime() { return departureTime; }

	public String getVehicleName() { return vehicleName; }

	public DispatchStatus getDispatchStatus() { return dispatchStatus; }

	public String getNote() { return note; }

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

	public void updatePublished(
			LocalDate workDate,
			LocalTime startTime,
			LocalTime endTime,
			String requesterName,
			WorkType workType) {
		ScheduleRequest validated = published(
				workDate, startTime, endTime, requesterName, workType);
		this.workDate = validated.workDate;
		this.startTime = validated.startTime;
		this.endTime = validated.endTime;
		this.requesterName = validated.requesterName;
		this.workType = validated.workType;
		this.entryState = EntryState.PUBLISHED;
		this.draftReason = null;
		this.draftErrorDetail = null;
	}

	public void applyInput(ScheduleRequestInput input) {
		Objects.requireNonNull(input);
		Objects.requireNonNull(input.workDate());
		validateDraftTime(input.startTime(), input.endTime());
		this.workDate = input.workDate();
		this.startTime = input.startTime();
		this.endTime = input.endTime();
		this.workType = input.workType();
		this.requesterName = normalize(input.requesterName());
		if (isInternalWork(input.workType())) {
			clearNormalWorkDetails();
			markIncompleteDraft();
			return;
		}
		this.requestDetail = normalize(input.requestDetail());
		this.address = normalize(input.address());
		this.desiredArrivalTime = normalize(input.desiredArrivalTime());
		this.companionRequired = input.companionRequired();
		this.meetingPlace = input.companionRequired() ? normalize(input.meetingPlace()) : null;
		this.departureTime = input.companionRequired() ? input.departureTime() : null;
		this.vehicleName = input.companionRequired() ? normalize(input.vehicleName()) : null;
		this.dispatchStatus = input.dispatchStatus() == null
				? DispatchStatus.UNANSWERED : input.dispatchStatus();
		this.note = normalize(input.note());
		markIncompleteDraft();
	}

	private void clearNormalWorkDetails() {
		this.requestDetail = null;
		this.address = null;
		this.desiredArrivalTime = null;
		this.companionRequired = false;
		this.meetingPlace = null;
		this.departureTime = null;
		this.vehicleName = null;
		this.dispatchStatus = DispatchStatus.UNANSWERED;
		this.note = null;
	}

	private void markIncompleteDraft() {
		this.entryState = EntryState.DRAFT;
		this.draftReason = DraftReason.INCOMPLETE;
		this.draftErrorDetail = "入力不足";
	}

	public boolean canAppearOnSchedule() {
		if (startTime == null || endTime == null) {
			return false;
		}
		return isInternalWork(workType) || !isBlank(requesterName);
	}

	public List<String> missingRequiredFields() {
		List<String> missing = new ArrayList<>();
		if (startTime == null) missing.add("開始時間");
		if (endTime == null) missing.add("終了時間");
		if (workType == null) missing.add("作業種別");
		if (workType == null || isInternalWork(workType)) {
			return List.copyOf(missing);
		}
		if (isBlank(requesterName)) missing.add("依頼者名");
		if (isBlank(requestDetail)) missing.add("依頼内容");
		if (!companionRequired && isBlank(address)) missing.add("現場住所もしくは会社名");
		if (isBlank(desiredArrivalTime)) missing.add("顧客先到着希望時間");
		if (companionRequired) {
			if (isBlank(meetingPlace)) missing.add("集合場所");
			if (departureTime == null) missing.add("出発時間");
		}
		return List.copyOf(missing);
	}

	public boolean hasMissingRequiredFields() {
		return !missingRequiredFields().isEmpty();
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
