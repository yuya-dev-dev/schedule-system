package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScheduleRequestEditingService {

	private static final String POSTGRES_EXCLUSION_VIOLATION = "23P01";

	private final ScheduleRequestRepository repository;
	private final TransactionTemplate transactionTemplate;
	private final ScheduleDatePolicy datePolicy;

	public ScheduleRequestEditingService(
			ScheduleRequestRepository repository,
			PlatformTransactionManager transactionManager,
			ScheduleDatePolicy datePolicy) {
		this.repository = repository;
		this.datePolicy = datePolicy;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public EditResult update(Long id, long expectedVersion, PublishCommand command) {
		datePolicy.requireRegistrable(command.workDate());
		if (hasConflict(id, command)) {
			return EditResult.timeConflict();
		}
		try {
			return transactionTemplate.execute(status -> updateInsideTransaction(id, expectedVersion, command));
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
			return EditResult.stale();
		} catch (DataIntegrityViolationException exception) {
			if (hasSqlState(exception, POSTGRES_EXCLUSION_VIOLATION)) {
				return EditResult.timeConflict();
			}
			throw exception;
		}
	}

	private EditResult updateInsideTransaction(
			Long id, long expectedVersion, PublishCommand command) {
		ScheduleRequest request = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Request not found: " + id));
		if (request.getEntryState() != EntryState.PUBLISHED) {
			throw new IllegalStateException("Request is not published: " + id);
		}
		if (request.getVersion() != expectedVersion) {
			return EditResult.stale();
		}
		request.updatePublished(
				command.workDate(),
				command.startTime(),
				command.endTime(),
				command.requesterName(),
				command.workType());
		repository.flush();
		return EditResult.updated();
	}

	private boolean hasConflict(Long id, PublishCommand command) {
		return transactionTemplate.execute(status -> repository
				.findFirstByIdNotAndWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
						id,
						command.workDate(),
						EntryState.PUBLISHED,
						command.endTime(),
						command.startTime())
				.isPresent());
	}

	private boolean hasSqlState(Throwable throwable, String expectedState) {
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
