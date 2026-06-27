package com.yuyadev.schedulesystem;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfiguration {

	@Bean
	@Primary
	Clock fixedTestClock() {
		return Clock.fixed(
				Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
	}
}
