package com.yuyadev.schedulesystem.schedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class ScheduleTimeSlots {

	public static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
	public static final LocalTime CLOSING_TIME = LocalTime.of(17, 30);
	public static final int SLOT_MINUTES = 30;

	private ScheduleTimeSlots() {}

	public static List<LocalTime> startTimes() {
		return timesBetween(OPENING_TIME, CLOSING_TIME.minusMinutes(SLOT_MINUTES));
	}

	public static List<LocalTime> endTimes() {
		return timesBetween(OPENING_TIME.plusMinutes(SLOT_MINUTES), CLOSING_TIME);
	}

	public static boolean isAligned(LocalTime time) {
		return time != null
				&& time.getMinute() % SLOT_MINUTES == 0
				&& time.getSecond() == 0
				&& time.getNano() == 0;
	}

	private static List<LocalTime> timesBetween(LocalTime first, LocalTime last) {
		List<LocalTime> times = new ArrayList<>();
		for (LocalTime time = first;
			!time.isAfter(last);
			time = time.plusMinutes(SLOT_MINUTES)) {
			times.add(time);
		}
		return List.copyOf(times);
	}
}
