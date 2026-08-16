package com.yuyadev.schedulesystem.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccessGatePropertiesTest {

	@Test
	void rejectsDisabledAccessGateForCloudProfile() {
		AccessGateProperties properties = new AccessGateProperties(false, null);

		assertThatThrownBy(() -> properties.validate(true))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cloud profile");
	}

	@Test
	void allowsDisabledAccessGateOutsideCloudProfile() {
		AccessGateProperties properties = new AccessGateProperties(false, null);

		assertThatCode(() -> properties.validate(false)).doesNotThrowAnyException();
	}

	@Test
	void rejectsMissingPasswordWhenAccessGateIsEnabled() {
		AccessGateProperties properties = new AccessGateProperties(true, " ");

		assertThatThrownBy(() -> properties.validate(false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("password is missing");
	}
}
