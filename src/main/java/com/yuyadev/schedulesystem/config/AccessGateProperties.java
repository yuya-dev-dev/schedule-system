package com.yuyadev.schedulesystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schedule.security.access-gate")
public record AccessGateProperties(
		boolean enabled,
		String password) {

	public void validate(boolean cloudProfile) {
		if (cloudProfile && !enabled) {
			throw new IllegalStateException(
					"Access gate must be enabled for the cloud profile.");
		}
		if (enabled && !hasText(password)) {
			throw new IllegalStateException(
					"Access gate is enabled, but password is missing.");
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
