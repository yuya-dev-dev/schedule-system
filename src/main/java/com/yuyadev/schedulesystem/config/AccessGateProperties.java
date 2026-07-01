package com.yuyadev.schedulesystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schedule.security.access-gate")
public record AccessGateProperties(
		boolean enabled,
		String password) {

	public void validateIfEnabled() {
		if (enabled && !hasText(password)) {
			throw new IllegalStateException(
					"Access gate is enabled, but password is missing.");
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
