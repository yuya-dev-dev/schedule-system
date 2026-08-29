package com.yuyadev.schedulesystem.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

	@Test
	void blocksTheSameClientAfterFiveFailures() {
		LoginAttemptService service = new LoginAttemptService(Clock.fixed(
				Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC));

		for (int attempt = 0; attempt < 5; attempt++) {
			service.recordFailure("client");
		}

		assertThat(service.isBlocked("client")).isTrue();
	}

	@Test
	void evictsAnOldEntryInsteadOfBlockingUnrelatedClientsAtTheTrackingLimit() {
		LoginAttemptService service = new LoginAttemptService(Clock.fixed(
				Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC));
		for (int index = 0; index < 1_000; index++) {
			service.recordFailure("client-" + index);
		}

		assertThat(service.isBlocked("overflow-client")).isFalse();

		service.recordFailure("overflow-client");

		assertThat(service.isBlocked("overflow-client")).isFalse();
		assertThat(service.isBlocked("another-untracked-client")).isFalse();
		assertThat(service.trackedClientCount()).isEqualTo(1_000);
	}

	@Test
	void keepsAnExistingClientBlockWhenTheTrackingLimitIsReached() {
		LoginAttemptService service = new LoginAttemptService(Clock.fixed(
				Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC));
		for (int attempt = 0; attempt < 5; attempt++) {
			service.recordFailure("blocked-client");
		}
		for (int index = 0; index < 999; index++) {
			service.recordFailure("client-" + index);
		}

		service.recordFailure("overflow-client");

		assertThat(service.isBlocked("blocked-client")).isTrue();
		assertThat(service.trackedClientCount()).isEqualTo(1_000);
	}

	@Test
	void clearsFailuresAfterSuccessfulAuthentication() {
		LoginAttemptService service = new LoginAttemptService(Clock.fixed(
				Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC));
		for (int attempt = 0; attempt < 4; attempt++) {
			service.recordFailure("client");
		}

		service.clear("client");
		service.recordFailure("client");

		assertThat(service.isBlocked("client")).isFalse();
	}

	@Test
	void unblocksClientAfterBlockDuration() {
		MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
		LoginAttemptService service = new LoginAttemptService(clock);
		for (int attempt = 0; attempt < 5; attempt++) {
			service.recordFailure("client");
		}
		assertThat(service.isBlocked("client")).isTrue();

		clock.advance(Duration.ofMinutes(15));

		assertThat(service.isBlocked("client")).isFalse();
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
