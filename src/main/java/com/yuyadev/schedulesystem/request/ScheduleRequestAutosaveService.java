package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScheduleRequestAutosaveService {

	private static final String POSTGRES_EXCLUSION_VIOLATION = "23P01";

	private final ScheduleRequestRepository repository;
	private final ScheduleDatePolicy datePolicy;
	private final TransactionTemplate transactionTemplate;

	public ScheduleRequestAutosaveService(
			ScheduleRequestRepository repository,
			ScheduleDatePolicy datePolicy,
			PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.datePolicy = datePolicy;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public AutosaveResult save(Long id, long expectedVersion, ScheduleRequestInput input) {
		try {
			datePolicy.requireRegistrable(input.workDate());
			SaveResult saved = transactionTemplate.execute(
					status -> saveInTransaction(id, expectedVersion, input));
			return toAutosaveResult(saved);
		} catch (DataIntegrityViolationException exception) {
			if (!hasSqlState(exception, POSTGRES_EXCLUSION_VIOLATION)) {
				throw exception;
			}
			SaveResult conflict = transactionTemplate.execute(
					status -> saveRaceConflict(id, expectedVersion, input));
			return toAutosaveResult(conflict);
		} catch (IllegalArgumentException exception) {
			return AutosaveResult.invalid(exception.getMessage());
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
			ScheduleRequest current = id == null ? null : repository.findById(id).orElse(null);
			return current == null
					? AutosaveResult.invalid("保存対象が見つかりません")
					: AutosaveResult.stale(current);
		}
	}

	private SaveResult saveInTransaction(
			Long id, long expectedVersion, ScheduleRequestInput input) {
		ScheduleRequest request;
		if (id == null) {
			request = ScheduleRequest.draft(input);
		} else {
			request = find(id);
			if (request.getVersion() != expectedVersion) {
				return SaveResult.stale(request.getId());
			}
			request.applyInput(input);
		}

		if (request.canAppearOnSchedule()) {
			Optional<ScheduleRequest> conflict = findConflict(id, input);
			if (conflict.isPresent()) {
				request.markTimeConflict(conflictDetail(conflict.get()));
				ScheduleRequest saved = repository.saveAndFlush(request);
				return SaveResult.conflict(saved.getId());
			}
			request.publish();
		}

		ScheduleRequest saved = repository.saveAndFlush(request);
		return SaveResult.saved(saved.getId());
	}

	private SaveResult saveRaceConflict(
			Long id, long expectedVersion, ScheduleRequestInput input) {
		ScheduleRequest request;
		if (id == null) {
			request = ScheduleRequest.draft(input);
		} else {
			request = find(id);
			if (request.getVersion() != expectedVersion) {
				return SaveResult.stale(request.getId());
			}
			request.applyInput(input);
		}
		request.markTimeConflict("既存案件と時間が重複しています");
		ScheduleRequest saved = repository.saveAndFlush(request);
		return SaveResult.conflict(saved.getId());
	}

	private Optional<ScheduleRequest> findConflict(Long id, ScheduleRequestInput input) {
		if (id == null) {
			return repository
					.findFirstByWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
							input.workDate(), EntryState.PUBLISHED, input.endTime(), input.startTime());
		}
		return repository
				.findFirstByIdNotAndWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
						id, input.workDate(), EntryState.PUBLISHED, input.endTime(), input.startTime());
	}

	private AutosaveResult toAutosaveResult(SaveResult result) {
		ScheduleRequest request = find(result.requestId());
		if (result.stale()) {
			return AutosaveResult.stale(request);
		}
		if (result.conflict()) {
			return AutosaveResult.timeConflict(request);
		}
		return AutosaveResult.saved(request);
	}

	private ScheduleRequest find(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("保存対象が見つかりません"));
	}

	private String conflictDetail(ScheduleRequest request) {
		return "既存案件 " + request.getStartTime() + "-" + request.getEndTime() + " と重複";
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

	private record SaveResult(Long requestId, boolean stale, boolean conflict) {
		private static SaveResult saved(Long id) {
			return new SaveResult(id, false, false);
		}

		private static SaveResult stale(Long id) {
			return new SaveResult(id, true, false);
		}

		private static SaveResult conflict(Long id) {
			return new SaveResult(id, false, true);
		}
	}
}
