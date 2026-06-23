package com.yuyadev.schedulesystem.holiday;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class HolidayStartupSync implements ApplicationRunner {

	private final HolidaySyncService syncService;
	private final boolean enabled;

	public HolidayStartupSync(
			HolidaySyncService syncService,
			@Value("${schedule.holidays.sync-enabled:true}") boolean enabled) {
		this.syncService = syncService;
		this.enabled = enabled;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (enabled) {
			syncService.syncIfStale();
		}
	}
}
