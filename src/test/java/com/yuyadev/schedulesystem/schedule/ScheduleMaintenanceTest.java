package com.yuyadev.schedulesystem.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuyadev.schedulesystem.holiday.HolidayCalendarService;
import com.yuyadev.schedulesystem.holiday.HolidaySyncService;
import com.yuyadev.schedulesystem.request.DraftManagementService;
import com.yuyadev.schedulesystem.request.RecurringFixedRequestService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class ScheduleMaintenanceTest {

	@Mock
	private HolidaySyncService holidaySyncService;

	@Mock
	private HolidayCalendarService holidayCalendarService;

	@Mock
	private RecurringFixedRequestService recurringFixedRequestService;

	@Mock
	private DraftManagementService draftManagementService;

	@Test
	void refreshesHolidaysBeforeCreatingFixedRequestsAndDeletingExpiredDrafts() {
		when(holidaySyncService.syncIfStale())
				.thenReturn(HolidaySyncService.SyncResult.UPDATED);
		ScheduleMaintenance maintenance = maintenance(true, true);

		maintenance.runMaintenance();

		var ordered = org.mockito.Mockito.inOrder(
				holidaySyncService, recurringFixedRequestService, draftManagementService);
		ordered.verify(holidaySyncService).syncIfStale();
		ordered.verify(recurringFixedRequestService).ensureCurrentAndNextMonth();
		ordered.verify(draftManagementService).deleteExpiredDrafts();
	}

	@Test
	void skipsFixedRequestsWhenHolidaySyncFailsWithoutCache() {
		when(holidaySyncService.syncIfStale())
				.thenReturn(HolidaySyncService.SyncResult.FAILED_USING_CACHE);
		when(holidayCalendarService.hasCachedHolidays()).thenReturn(false);
		ScheduleMaintenance maintenance = maintenance(true, true);

		maintenance.runMaintenance();

		verify(recurringFixedRequestService, never()).ensureCurrentAndNextMonth();
		verify(draftManagementService).deleteExpiredDrafts();
	}

	@Test
	void usesExistingHolidayCacheWhenRefreshFails() {
		when(holidaySyncService.syncIfStale())
				.thenReturn(HolidaySyncService.SyncResult.FAILED_USING_CACHE);
		when(holidayCalendarService.hasCachedHolidays()).thenReturn(true);
		ScheduleMaintenance maintenance = maintenance(true, true);

		maintenance.runMaintenance();

		verify(recurringFixedRequestService).ensureCurrentAndNextMonth();
	}

	@Test
	void doesNothingWhenMaintenanceIsDisabled() {
		ScheduleMaintenance maintenance = maintenance(false, true);

		maintenance.runMaintenance();

		verify(holidaySyncService, never()).syncIfStale();
		verify(recurringFixedRequestService, never()).ensureCurrentAndNextMonth();
		verify(draftManagementService, never()).deleteExpiredDrafts();
	}

	@Test
	void schedulesDailyMaintenanceInJapanTime() throws Exception {
		Method method = ScheduleMaintenance.class.getDeclaredMethod("runDaily");
		Scheduled scheduled = method.getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo(ScheduleMaintenance.DAILY_CRON);
		assertThat(scheduled.zone()).isEqualTo(ScheduleMaintenance.TIME_ZONE);
	}

	private ScheduleMaintenance maintenance(boolean enabled, boolean holidaySyncEnabled) {
		return new ScheduleMaintenance(
				holidaySyncService,
				holidayCalendarService,
				recurringFixedRequestService,
				draftManagementService,
				enabled,
				holidaySyncEnabled);
	}
}
