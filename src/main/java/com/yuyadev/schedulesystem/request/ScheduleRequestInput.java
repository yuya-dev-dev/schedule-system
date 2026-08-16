package com.yuyadev.schedulesystem.request;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleRequestInput(
		LocalDate workDate,
		LocalTime startTime,
		LocalTime endTime,
		WorkType workType,
		String requesterName,
		String requestDetail,
		String address,
		String desiredArrivalTime,
		boolean companionRequired,
		String meetingPlace,
		LocalTime departureTime,
		String vehicleName,
		DispatchStatus dispatchStatus,
		String note) {

	public static ScheduleRequestInput forCopy(
			ScheduleRequest source, LocalDate targetDate) {
		return new ScheduleRequestInput(
				targetDate,
				source.getStartTime(),
				source.getEndTime(),
				source.getWorkType(),
				source.getRequesterName(),
				source.getRequestDetail(),
				source.getAddress(),
				source.getDesiredArrivalTime(),
				source.isCompanionRequired(),
				source.getMeetingPlace(),
				source.getDepartureTime(),
				source.getVehicleName(),
				source.getDispatchStatus(),
				source.getNote());
	}
}
