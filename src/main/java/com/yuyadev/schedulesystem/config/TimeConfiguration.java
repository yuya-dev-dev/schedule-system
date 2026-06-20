package com.yuyadev.schedulesystem.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

	@Bean
	Clock applicationClock() {
		return Clock.system(ZoneId.of("Asia/Tokyo"));
	}
}
