package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScheduleRequestAutosaveService {

	private final ScheduleRequestRepository repository;
	private final ScheduleRequestPublishingService publishingService;
	private final ScheduleDatePolicy datePolicy;
	private final TransactionTemplate transactionTemplate;

	public ScheduleRequestAutosaveService(
			ScheduleRequestRepository repository,
			ScheduleRequestPublishingService publishingService,
			ScheduleDatePolicy datePolicy,
			PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.publishingService = publishingService;
		this.datePolicy = datePolicy;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public AutosaveResult save(Long id, long expectedVersion, ScheduleRequestInput input) {
		try {
			datePolicy.requireRegistrable(input.workDate());
			SaveDraftResult saved = transactionTemplate.execute(
					status -> saveAsDraft(id, expectedVersion, input));
			if (saved.stale()) {
				return AutosaveResult.stale(find(saved.requestId()));
			}

			ScheduleRequest draft = find(saved.requestId());
			if (!draft.canAppearOnSchedule()) {
				return AutosaveResult.saved(draft);
			}

			PublishResult publishResult = publishingService.publishDraft(draft.getId());
			ScheduleRequest current = find(draft.getId());
			if (publishResult.status() == PublishResult.Status.TIME_CONFLICT) {
				return AutosaveResult.timeConflict(current);
			}
			return AutosaveResult.saved(current);
		} catch (IllegalArgumentException exception) {
			return AutosaveResult.invalid(exception.getMessage());
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
			ScheduleRequest current = id == null ? null : repository.findById(id).orElse(null);
			return current == null
					? AutosaveResult.invalid("保存対象が見つかりません")
					: AutosaveResult.stale(current);
		}
	}

	private SaveDraftResult saveAsDraft(
			Long id, long expectedVersion, ScheduleRequestInput input) {
		if (id == null) {
			ScheduleRequest created = repository.saveAndFlush(ScheduleRequest.draft(input));
			return new SaveDraftResult(created.getId(), false);
		}
		ScheduleRequest request = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("保存対象が見つかりません"));
		if (request.getVersion() != expectedVersion) {
			return new SaveDraftResult(request.getId(), true);
		}
		request.applyInput(input);
		repository.flush();
		return new SaveDraftResult(request.getId(), false);
	}

	private ScheduleRequest find(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("保存対象が見つかりません"));
	}

	private record SaveDraftResult(Long requestId, boolean stale) {}
}
