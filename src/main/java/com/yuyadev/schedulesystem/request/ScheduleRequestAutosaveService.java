package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import com.yuyadev.schedulesystem.schedule.ScheduleDateTransactionLock;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScheduleRequestAutosaveService {

	private final ScheduleRequestRepository repository;
	private final ScheduleDatePolicy datePolicy;
	private final ScheduleDateTransactionLock dateTransactionLock;
	private final TransactionTemplate transactionTemplate;

	public ScheduleRequestAutosaveService(
			ScheduleRequestRepository repository,
			ScheduleDatePolicy datePolicy,
			ScheduleDateTransactionLock dateTransactionLock,
			PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.datePolicy = datePolicy;
		this.dateTransactionLock = dateTransactionLock;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public AutosaveResult save(Long id, long expectedVersion, ScheduleRequestInput input) {
		try {
			SaveResult saved = transactionTemplate.execute(
					status -> saveInTransaction(id, expectedVersion, input));
			return toAutosaveResult(saved);
		} catch (DataIntegrityViolationException exception) {
			if (!PostgreSqlConflictDetector.isPublishedTimeOverlap(exception)) {
				throw exception;
			}
			SaveResult conflict = transactionTemplate.execute(
					status -> saveRaceConflict(id, expectedVersion, input));
			return toAutosaveResult(conflict);
		} catch (CannotAcquireLockException exception) {
			if (!isPublishedTimeDeadlock(exception)) {
				throw exception;
			}
			SaveResult conflict = transactionTemplate.execute(
					status -> saveRaceConflict(id, expectedVersion, input));
			return toAutosaveResult(conflict);
		} catch (IllegalArgumentException exception) {
			ScheduleRequest current = id == null ? null : repository.findById(id).orElse(null);
			return current == null
					? AutosaveResult.invalid(exception.getMessage())
					: AutosaveResult.invalid(current, exception.getMessage());
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
			dateTransactionLock.lock(input.workDate());
			datePolicy.requireRegistrable(input.workDate());
			request = ScheduleRequest.draft(input);
		} else {
			request = find(id);
			requireSameWorkDate(request, input);
			dateTransactionLock.lock(request.getWorkDate());
			datePolicy.requireRegistrable(request.getWorkDate());
			if (request.getVersion() != expectedVersion) {
				return SaveResult.stale(request.getId());
			}
			boolean canAppearOnSchedule = canAppearOnSchedule(input);
			if (request.getEntryState() == EntryState.PUBLISHED
					&& !canAppearOnSchedule) {
				return SaveResult.invalidInput(
						request.getId(), ScheduleRequest.missingRequiredFields(input));
			}
			if (request.getEntryState() == EntryState.PUBLISHED
					&& canAppearOnSchedule
					&& findConflict(id, input).isPresent()) {
				return SaveResult.conflict(request.getId());
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
			dateTransactionLock.lock(input.workDate());
			datePolicy.requireRegistrable(input.workDate());
			request = ScheduleRequest.draft(input);
		} else {
			request = find(id);
			requireSameWorkDate(request, input);
			dateTransactionLock.lock(request.getWorkDate());
			datePolicy.requireRegistrable(request.getWorkDate());
			if (request.getVersion() != expectedVersion) {
				return SaveResult.stale(request.getId());
			}
			if (request.getEntryState() == EntryState.PUBLISHED) {
				return SaveResult.conflict(request.getId());
			}
			request.applyInput(input);
		}
		request.markTimeConflict("既存案件と時間が重複しています");
		ScheduleRequest saved = repository.saveAndFlush(request);
		return SaveResult.conflict(saved.getId());
	}

	private Optional<ScheduleRequest> findConflict(Long id, ScheduleRequestInput input) {
		if (id == null) {
			return repository.findPublishedOverlaps(
					input.workDate(), input.startTime(), input.endTime()).stream().findFirst();
		}
		return repository.findOtherPublishedOverlaps(
				id, input.workDate(), input.startTime(), input.endTime()).stream().findFirst();
	}

	private void requireSameWorkDate(ScheduleRequest request, ScheduleRequestInput input) {
		if (!request.getWorkDate().equals(input.workDate())) {
			throw new IllegalArgumentException("作業日は変更できません");
		}
	}

	private boolean canAppearOnSchedule(ScheduleRequestInput input) {
		if (input.startTime() == null || input.endTime() == null) {
			return false;
		}
		return ScheduleRequest.isInternalWork(input.workType())
				|| (input.requesterName() != null && !input.requesterName().isBlank());
	}

	private AutosaveResult toAutosaveResult(SaveResult result) {
		ScheduleRequest request = find(result.requestId());
		return switch (result.status()) {
			case SAVED -> AutosaveResult.saved(request);
			case STALE -> AutosaveResult.stale(request);
			case CONFLICT -> AutosaveResult.timeConflict(request);
			case INVALID_INPUT ->
					AutosaveResult.invalidPublishedEdit(request, result.missingFields());
		};
	}

	private ScheduleRequest find(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("案件は削除されています"));
	}

	private String conflictDetail(ScheduleRequest request) {
		return "既存案件 " + request.getStartTime() + "-" + request.getEndTime() + " と重複";
	}

	static boolean isPublishedTimeDeadlock(Throwable throwable) {
		return PostgreSqlConflictDetector.isDeadlock(throwable);
	}

	private enum SaveStatus {
		SAVED,
		STALE,
		CONFLICT,
		INVALID_INPUT
	}

	private record SaveResult(Long requestId, SaveStatus status, List<String> missingFields) {
		private static SaveResult saved(Long id) {
			return new SaveResult(id, SaveStatus.SAVED, List.of());
		}

		private static SaveResult stale(Long id) {
			return new SaveResult(id, SaveStatus.STALE, List.of());
		}

		private static SaveResult conflict(Long id) {
			return new SaveResult(id, SaveStatus.CONFLICT, List.of());
		}

		private static SaveResult invalidInput(Long id, List<String> missingFields) {
			return new SaveResult(id, SaveStatus.INVALID_INPUT, List.copyOf(missingFields));
		}
	}
}
