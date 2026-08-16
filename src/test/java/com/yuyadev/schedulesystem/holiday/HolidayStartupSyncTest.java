package com.yuyadev.schedulesystem.holiday;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class HolidayStartupSyncTest {

	@Test
	void syncsHolidaysAtStartupWhenEnabled() {
		HolidaySyncService holidaySyncService = mock(HolidaySyncService.class);
		HolidayStartupSync startupSync = new HolidayStartupSync(holidaySyncService, true);

		startupSync.run(null);

		verify(holidaySyncService).syncIfStale();
	}

	@Test
	void skipsHolidaySyncAtStartupWhenDisabled() {
		HolidaySyncService holidaySyncService = mock(HolidaySyncService.class);
		HolidayStartupSync startupSync = new HolidayStartupSync(holidaySyncService, false);

		startupSync.run(null);

		verify(holidaySyncService, never()).syncIfStale();
	}
}
