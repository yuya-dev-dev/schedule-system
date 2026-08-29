package com.yuyadev.schedulesystem.request;

import java.sql.PreparedStatement;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DraftCapacityTransactionLock {

	private static final long LOCK_KEY = 1_931_452_014L;
	private final JdbcTemplate jdbcTemplate;

	DraftCapacityTransactionLock(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	void lock() {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
				return null;
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT pg_advisory_xact_lock(?)")) {
				statement.setLong(1, LOCK_KEY);
				statement.execute();
			}
			return null;
		});
	}
}
