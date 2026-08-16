package com.yuyadev.schedulesystem.request;

import java.sql.SQLException;

final class PostgreSqlConflictDetector {

	private static final String EXCLUSION_VIOLATION = "23P01";
	private static final String DEADLOCK_DETECTED = "40P01";

	private PostgreSqlConflictDetector() {
	}

	static boolean isPublishedTimeOverlap(Throwable throwable) {
		return hasSqlState(throwable, EXCLUSION_VIOLATION);
	}

	static boolean isDeadlock(Throwable throwable) {
		return hasSqlState(throwable, DEADLOCK_DETECTED);
	}

	private static boolean hasSqlState(Throwable throwable, String expectedState) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof SQLException sqlException
					&& expectedState.equals(sqlException.getSQLState())) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
