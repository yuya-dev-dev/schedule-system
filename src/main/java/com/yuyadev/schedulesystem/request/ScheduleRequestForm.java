package com.yuyadev.schedulesystem.request;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

public class ScheduleRequestForm {

	private Long id;
	private long version;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate workDate;

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime startTime;

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime endTime;

	private WorkType workType;
	private String requesterName;
	private String requestDetail;
	private String address;
	private String desiredArrivalTime;
	private boolean companionRequired;
	private String meetingPlace;
	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime departureTime;
	private String vehicleName;
	private DispatchStatus dispatchStatus = DispatchStatus.UNANSWERED;
	private String note;

	public static ScheduleRequestForm newFor(LocalDate workDate) {
		ScheduleRequestForm form = new ScheduleRequestForm();
		form.setWorkDate(workDate);
		return form;
	}

	public static ScheduleRequestForm from(ScheduleRequest request) {
		ScheduleRequestForm form = new ScheduleRequestForm();
		form.setId(request.getId());
		form.setVersion(request.getVersion());
		form.setWorkDate(request.getWorkDate());
		form.setStartTime(request.getStartTime());
		form.setEndTime(request.getEndTime());
		form.setWorkType(request.getWorkType());
		form.setRequesterName(request.getRequesterName());
		form.setRequestDetail(request.getRequestDetail());
		form.setAddress(request.getAddress());
		form.setDesiredArrivalTime(request.getDesiredArrivalTime());
		form.setCompanionRequired(request.isCompanionRequired());
		form.setMeetingPlace(request.getMeetingPlace());
		form.setDepartureTime(request.getDepartureTime());
		form.setVehicleName(request.getVehicleName());
		form.setDispatchStatus(request.getDispatchStatus());
		form.setNote(request.getNote());
		return form;
	}

	public PublishCommand toCommand() {
		return new PublishCommand(workDate, startTime, endTime, requesterName, workType);
	}

	public ScheduleRequestInput toInput() {
		return new ScheduleRequestInput(
				workDate, startTime, endTime, workType, requesterName, requestDetail,
				address, desiredArrivalTime, companionRequired, meetingPlace,
				departureTime, vehicleName, dispatchStatus, note);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public LocalDate getWorkDate() {
		return workDate;
	}

	public void setWorkDate(LocalDate workDate) {
		this.workDate = workDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public WorkType getWorkType() {
		return workType;
	}

	public void setWorkType(WorkType workType) {
		this.workType = workType;
	}

	public String getRequesterName() {
		return requesterName;
	}

	public void setRequesterName(String requesterName) {
		this.requesterName = requesterName;
	}

	public String getRequestDetail() { return requestDetail; }
	public void setRequestDetail(String requestDetail) { this.requestDetail = requestDetail; }
	public String getAddress() { return address; }
	public void setAddress(String address) { this.address = address; }
	public String getDesiredArrivalTime() { return desiredArrivalTime; }
	public void setDesiredArrivalTime(String desiredArrivalTime) { this.desiredArrivalTime = desiredArrivalTime; }
	public boolean isCompanionRequired() { return companionRequired; }
	public void setCompanionRequired(boolean companionRequired) { this.companionRequired = companionRequired; }
	public String getMeetingPlace() { return meetingPlace; }
	public void setMeetingPlace(String meetingPlace) { this.meetingPlace = meetingPlace; }
	public LocalTime getDepartureTime() { return departureTime; }
	public void setDepartureTime(LocalTime departureTime) { this.departureTime = departureTime; }
	public String getVehicleName() { return vehicleName; }
	public void setVehicleName(String vehicleName) { this.vehicleName = vehicleName; }
	public DispatchStatus getDispatchStatus() { return dispatchStatus; }
	public void setDispatchStatus(DispatchStatus dispatchStatus) { this.dispatchStatus = dispatchStatus; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
}
