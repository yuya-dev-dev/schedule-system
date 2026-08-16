package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RequestCopyServiceTest {

	private static final long SOURCE_ID = 1L;
	private static final LocalDate TARGET_DATE = LocalDate.of(2026, 6, 26);

	@Mock
	private ScheduleRequestRepository repository;

	@Mock
	private ScheduleDatePolicy datePolicy;

	private RequestCopyService service;

	@BeforeEach
	void setUp() {
		service = new RequestCopyService(repository, datePolicy);
		ScheduleRequest source = ScheduleRequest.published(
				LocalDate.of(2026, 6, 24),
				LocalTime.of(10, 0),
				LocalTime.of(11, 0),
				"社員A",
				WorkType.INSTALL);
		when(repository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
		when(repository.findPublishedOverlaps(any(), any(), any())).thenReturn(List.of());
	}

	@Test
	void convertsOnlyPostgresqlOverlapViolationToTimeConflict() {
		when(repository.saveAndFlush(any()))
				.thenThrow(databaseFailure("23P01"));

		RequestCopyResult result = service.copy(SOURCE_ID, TARGET_DATE);

		assertThat(result.status()).isEqualTo(RequestCopyResult.Status.TIME_CONFLICT);
	}

	@Test
	void rethrowsUnrelatedDatabaseIntegrityViolation() {
		DataIntegrityViolationException failure = databaseFailure("23502");
		when(repository.saveAndFlush(any())).thenThrow(failure);

		assertThatThrownBy(() -> service.copy(SOURCE_ID, TARGET_DATE))
				.isSameAs(failure);
	}

	private DataIntegrityViolationException databaseFailure(String sqlState) {
		return new DataIntegrityViolationException(
				"database failure", new SQLException("database failure", sqlState));
	}
}
