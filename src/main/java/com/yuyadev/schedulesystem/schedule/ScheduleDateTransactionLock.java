package com.yuyadev.schedulesystem.schedule;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ScheduleDateTransactionLock {

	private static final int LOCK_NAMESPACE = 1_931_452_013;
	private final JdbcTemplate jdbcTemplate;

	public ScheduleDateTransactionLock(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void lock(LocalDate date) {
		if (date == null) {
			throw new IllegalArgumentException("作業日は必須です");
		}
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
				return null;
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT pg_advisory_xact_lock(?, ?)")) {
				statement.setInt(1, LOCK_NAMESPACE);
				statement.setInt(2, Math.toIntExact(date.toEpochDay()));
				statement.execute();
			}
			return null;
		});
	}
}
