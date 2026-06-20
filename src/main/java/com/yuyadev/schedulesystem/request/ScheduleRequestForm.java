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
		return form;
	}

	public PublishCommand toCommand() {
		return new PublishCommand(workDate, startTime, endTime, requesterName, workType);
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
}
