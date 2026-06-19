package com.yuyadev.schedulesystem.request;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScheduleRequestPublishingService {

	private static final String POSTGRES_EXCLUSION_VIOLATION = "23P01";

	private final ScheduleRequestRepository repository;
	private final TransactionTemplate transactionTemplate;

	public ScheduleRequestPublishingService(
			ScheduleRequestRepository repository, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public PublishResult publish(PublishCommand command) {
		try {
			Long requestId = transactionTemplate.execute(status -> save(command));
			return PublishResult.published(requestId);
		} catch (DataIntegrityViolationException exception) {
			if (hasSqlState(exception, POSTGRES_EXCLUSION_VIOLATION)) {
				return PublishResult.timeConflict();
			}
			throw exception;
		}
	}

	private Long save(PublishCommand command) {
		ScheduleRequest request = ScheduleRequest.published(
				command.workDate(),
				command.startTime(),
				command.endTime(),
				command.requesterName(),
				command.workType());
		return repository.saveAndFlush(request).getId();
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
