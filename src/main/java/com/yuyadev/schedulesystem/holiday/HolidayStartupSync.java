package com.yuyadev.schedulesystem.holiday;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class HolidayStartupSync implements ApplicationRunner {

	private final HolidaySyncService holidaySyncService;
	private final boolean syncEnabled;

	public HolidayStartupSync(
			HolidaySyncService holidaySyncService,
			@Value("${schedule.holidays.sync-enabled:true}") boolean syncEnabled) {
		this.holidaySyncService = holidaySyncService;
		this.syncEnabled = syncEnabled;
	}

	@Override
	public void run(ApplicationArguments args) {
		syncHolidaysWhenEnabled();
	}

	private void syncHolidaysWhenEnabled() {
		if (syncEnabled) {
			holidaySyncService.syncIfStale();
		}
	}
}
