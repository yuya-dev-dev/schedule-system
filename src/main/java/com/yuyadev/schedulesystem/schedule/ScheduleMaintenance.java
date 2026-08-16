package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.holiday.HolidayCalendarService;
import com.yuyadev.schedulesystem.holiday.HolidaySyncService;
import com.yuyadev.schedulesystem.request.DraftManagementService;
import com.yuyadev.schedulesystem.request.RecurringFixedRequestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduleMaintenance implements ApplicationRunner {

	static final String DAILY_CRON = "0 0 3 * * *";
	static final String TIME_ZONE = "Asia/Tokyo";

	private final HolidaySyncService holidaySyncService;
	private final HolidayCalendarService holidayCalendarService;
	private final RecurringFixedRequestService recurringFixedRequestService;
	private final DraftManagementService draftManagementService;
	private final boolean enabled;
	private final boolean holidaySyncEnabled;

	public ScheduleMaintenance(
			HolidaySyncService holidaySyncService,
			HolidayCalendarService holidayCalendarService,
			RecurringFixedRequestService recurringFixedRequestService,
			DraftManagementService draftManagementService,
			@Value("${schedule.maintenance.enabled:true}") boolean enabled,
			@Value("${schedule.holidays.sync-enabled:true}") boolean holidaySyncEnabled) {
		this.holidaySyncService = holidaySyncService;
		this.holidayCalendarService = holidayCalendarService;
		this.recurringFixedRequestService = recurringFixedRequestService;
		this.draftManagementService = draftManagementService;
		this.enabled = enabled;
		this.holidaySyncEnabled = holidaySyncEnabled;
	}

	@Override
	public void run(ApplicationArguments args) {
		runMaintenance();
	}

	@Scheduled(cron = DAILY_CRON, zone = TIME_ZONE)
	void runDaily() {
		runMaintenance();
	}

	void runMaintenance() {
		if (!enabled) {
			return;
		}
		if (holidayDataIsAvailable()) {
			recurringFixedRequestService.ensureCurrentAndNextMonth();
		}
		draftManagementService.deleteExpiredDrafts();
	}

	private boolean holidayDataIsAvailable() {
		if (!holidaySyncEnabled) {
			return holidayCalendarService.hasCachedHolidays();
		}
		HolidaySyncService.SyncResult result = holidaySyncService.syncIfStale();
		return result != HolidaySyncService.SyncResult.FAILED_USING_CACHE
				|| holidayCalendarService.hasCachedHolidays();
	}
}
