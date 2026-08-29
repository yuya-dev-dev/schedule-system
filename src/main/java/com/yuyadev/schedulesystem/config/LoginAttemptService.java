package com.yuyadev.schedulesystem.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class LoginAttemptService {

	private static final int MAX_FAILURES = 5;
	private static final int MAX_TRACKED_CLIENTS = 1_000;
	private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
	private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

	private final Clock clock;
	private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

	LoginAttemptService(Clock clock) {
		this.clock = clock;
	}

	boolean isBlocked(String clientAddress) {
		String key = normalize(clientAddress);
		AttemptState state = attempts.get(key);
		if (state == null) {
			return false;
		}

		Instant now = clock.instant();
		if (state.blockedUntil() != null && now.isBefore(state.blockedUntil())) {
			return true;
		}
		if (!now.isBefore(state.windowStarted().plus(ATTEMPT_WINDOW))) {
			attempts.remove(key, state);
		}
		return false;
	}

	synchronized void recordFailure(String clientAddress) {
		String key = normalize(clientAddress);
		Instant now = clock.instant();
		if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_CLIENTS) {
			purgeExpired(now);
			if (attempts.size() >= MAX_TRACKED_CLIENTS) {
				evictOldestAttempt();
			}
		}

		attempts.compute(key, (ignored, current) -> nextFailureState(current, now));
	}

	void clear(String clientAddress) {
		attempts.remove(normalize(clientAddress));
	}

	int trackedClientCount() {
		return attempts.size();
	}

	private AttemptState nextFailureState(AttemptState current, Instant now) {
		if (current == null || !now.isBefore(current.windowStarted().plus(ATTEMPT_WINDOW))) {
			return new AttemptState(1, now, null);
		}
		if (current.blockedUntil() != null && now.isBefore(current.blockedUntil())) {
			return current;
		}

		int failures = current.failures() + 1;
		Instant blockedUntil = failures >= MAX_FAILURES ? now.plus(BLOCK_DURATION) : null;
		return new AttemptState(failures, current.windowStarted(), blockedUntil);
	}

	private void purgeExpired(Instant now) {
		attempts.entrySet().removeIf(entry ->
				!now.isBefore(entry.getValue().windowStarted().plus(ATTEMPT_WINDOW))
						&& (entry.getValue().blockedUntil() == null
								|| !now.isBefore(entry.getValue().blockedUntil())));
	}

	private void evictOldestAttempt() {
		attempts.entrySet().stream()
				.min(Comparator
						.comparing((Map.Entry<String, AttemptState> entry) ->
								entry.getValue().blockedUntil() != null)
						.thenComparing(entry -> entry.getValue().windowStarted()))
				.ifPresent(entry -> attempts.remove(entry.getKey(), entry.getValue()));
	}

	private String normalize(String clientAddress) {
		return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
	}

	private record AttemptState(int failures, Instant windowStarted, Instant blockedUntil) {
	}
}
