package com.yuyadev.schedulesystem.retention;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScheduleDataRetentionProperties.class)
public class ScheduleDataRetentionConfiguration {

	@Bean
	ApplicationRunner scheduleDataRetentionRunner(
			ScheduleDataRetentionProperties retentionProperties,
			ScheduleDataRetentionService retentionService) {
		return args -> deleteExpiredDataWhenEnabled(retentionProperties, retentionService);
	}

	private void deleteExpiredDataWhenEnabled(
			ScheduleDataRetentionProperties retentionProperties,
			ScheduleDataRetentionService retentionService) {
		if (retentionProperties.enabled()) {
			retentionService.deleteExpiredScheduleData();
		}
	}
}
