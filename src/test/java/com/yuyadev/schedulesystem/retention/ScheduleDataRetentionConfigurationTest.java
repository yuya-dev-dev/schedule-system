package com.yuyadev.schedulesystem.retention;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;

class ScheduleDataRetentionConfigurationTest {

	private final ScheduleDataRetentionConfiguration configuration =
			new ScheduleDataRetentionConfiguration();

	@Test
	void runnerDeletesExpiredDataWhenEnabled() throws Exception {
		ScheduleDataRetentionService service = mock(ScheduleDataRetentionService.class);
		ApplicationRunner runner = configuration.scheduleDataRetentionRunner(
				new ScheduleDataRetentionProperties(true), service);

		runner.run(null);

		verify(service).deleteExpiredScheduleData();
	}

	@Test
	void runnerDoesNothingWhenDisabled() throws Exception {
		ScheduleDataRetentionService service = mock(ScheduleDataRetentionService.class);
		ApplicationRunner runner = configuration.scheduleDataRetentionRunner(
				new ScheduleDataRetentionProperties(false), service);

		runner.run(null);

		verify(service, never()).deleteExpiredScheduleData();
	}
}
