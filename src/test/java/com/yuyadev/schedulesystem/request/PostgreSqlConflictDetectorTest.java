package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PostgreSqlConflictDetectorTest {

	@Test
	void identifiesPublishedTimeOverlapBySqlState() {
		DataIntegrityViolationException exception = failureWithSqlState("23P01");

		assertThat(PostgreSqlConflictDetector.isPublishedTimeOverlap(exception)).isTrue();
		assertThat(PostgreSqlConflictDetector.isDeadlock(exception)).isFalse();
	}

	@Test
	void doesNotTreatOtherIntegrityViolationsAsTimeOverlap() {
		DataIntegrityViolationException exception = failureWithSqlState("23502");

		assertThat(PostgreSqlConflictDetector.isPublishedTimeOverlap(exception)).isFalse();
	}

	@Test
	void identifiesDeadlockBySqlState() {
		DataIntegrityViolationException exception = failureWithSqlState("40P01");

		assertThat(PostgreSqlConflictDetector.isDeadlock(exception)).isTrue();
		assertThat(PostgreSqlConflictDetector.isPublishedTimeOverlap(exception)).isFalse();
	}

	@Test
	void returnsFalseWhenCauseHasNoSqlState() {
		DataIntegrityViolationException exception =
				new DataIntegrityViolationException("database failure");

		assertThat(PostgreSqlConflictDetector.isDeadlock(exception)).isFalse();
		assertThat(PostgreSqlConflictDetector.isPublishedTimeOverlap(exception)).isFalse();
	}

	private DataIntegrityViolationException failureWithSqlState(String sqlState) {
		return new DataIntegrityViolationException(
				"database failure", new SQLException("database failure", sqlState));
	}
}
