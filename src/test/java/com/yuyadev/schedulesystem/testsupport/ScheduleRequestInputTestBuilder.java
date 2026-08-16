package com.yuyadev.schedulesystem.testsupport;

import com.yuyadev.schedulesystem.request.DispatchStatus;
import com.yuyadev.schedulesystem.request.ScheduleRequestInput;
import com.yuyadev.schedulesystem.request.WorkType;
import java.time.LocalDate;
import java.time.LocalTime;

public final class ScheduleRequestInputTestBuilder {

	private LocalDate workDate = LocalDate.of(2026, 6, 24);
	private LocalTime startTime = LocalTime.of(9, 0);
	private LocalTime endTime = LocalTime.of(10, 0);
	private WorkType workType = WorkType.INSTALL;
	private String requesterName = "架空社員A";
	private String requestDetail = "架空の作業内容";
	private String address = "愛知県名古屋市中区架空町1-1";
	private String desiredArrivalTime = "午後";
	private boolean companionRequired;
	private String meetingPlace;
	private LocalTime departureTime;
	private String vehicleName;
	private DispatchStatus dispatchStatus = DispatchStatus.UNANSWERED;
	private String note;

	private ScheduleRequestInputTestBuilder() {
	}

	public static ScheduleRequestInputTestBuilder requestInput() {
		return new ScheduleRequestInputTestBuilder();
	}

	public ScheduleRequestInputTestBuilder workDate(LocalDate value) {
		workDate = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder startTime(LocalTime value) {
		startTime = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder endTime(LocalTime value) {
		endTime = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder workType(WorkType value) {
		workType = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder requesterName(String value) {
		requesterName = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder requestDetail(String value) {
		requestDetail = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder address(String value) {
		address = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder desiredArrivalTime(String value) {
		desiredArrivalTime = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder companionRequired(boolean value) {
		companionRequired = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder meetingPlace(String value) {
		meetingPlace = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder departureTime(LocalTime value) {
		departureTime = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder vehicleName(String value) {
		vehicleName = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder dispatchStatus(DispatchStatus value) {
		dispatchStatus = value;
		return this;
	}

	public ScheduleRequestInputTestBuilder note(String value) {
		note = value;
		return this;
	}

	public ScheduleRequestInput build() {
		return new ScheduleRequestInput(
				workDate,
				startTime,
				endTime,
				workType,
				requesterName,
				requestDetail,
				address,
				desiredArrivalTime,
				companionRequired,
				meetingPlace,
				departureTime,
				vehicleName,
				dispatchStatus,
				note);
	}
}
