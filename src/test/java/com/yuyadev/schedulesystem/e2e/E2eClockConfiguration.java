package com.yuyadev.schedulesystem.e2e;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class E2eClockConfiguration {

	@Bean
	@Primary
	Clock fixedE2eClock() {
		return Clock.fixed(
				Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
	}
}
