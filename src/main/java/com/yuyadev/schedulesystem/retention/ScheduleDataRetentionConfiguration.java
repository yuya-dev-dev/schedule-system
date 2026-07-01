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
			ScheduleDataRetentionProperties properties,
			ScheduleDataRetentionService service) {
		return args -> {
			if (properties.enabled()) {
				service.deleteExpiredScheduleData();
			}
		};
	}
}
