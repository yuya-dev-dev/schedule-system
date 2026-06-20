package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.sql.SQLException;
import java.util.Optional;
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
	private final ScheduleDatePolicy datePolicy;

	public ScheduleRequestPublishingService(
			ScheduleRequestRepository repository,
			PlatformTransactionManager transactionManager,
			ScheduleDatePolicy datePolicy) {
		this.repository = repository;
		this.datePolicy = datePolicy;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public PublishResult publish(PublishCommand command) {
		datePolicy.requireRegistrable(command.workDate());
		Optional<ScheduleRequest> conflict = findConflict(command);
		if (conflict.isPresent()) {
			return saveConflictDraft(command, conflict.get());
		}

		try {
			Long requestId = transactionTemplate.execute(status -> savePublished(command));
			return PublishResult.published(requestId);
		} catch (DataIntegrityViolationException exception) {
			if (hasSqlState(exception, POSTGRES_EXCLUSION_VIOLATION)) {
				return saveConflictDraft(command, findConflict(command).orElse(null));
			}
			throw exception;
		}
	}

	public Long saveDraft(PublishCommand command) {
		datePolicy.requireRegistrable(command.workDate());
		return transactionTemplate.execute(status -> {
			ScheduleRequest draft = ScheduleRequest.draft(
					command.workDate(),
					command.startTime(),
					command.endTime(),
					command.requesterName(),
					command.workType(),
					DraftReason.INCOMPLETE,
					"入力不足");
			return repository.saveAndFlush(draft).getId();
		});
	}

	public PublishResult publishDraft(Long draftId) {
		PublishCommand command = transactionTemplate.execute(status -> toCommand(findDraft(draftId)));
		datePolicy.requireRegistrable(command.workDate());
		Optional<ScheduleRequest> conflict = findConflict(command);
		if (conflict.isPresent()) {
			return markDraftAsConflict(draftId, conflict.get());
		}

		try {
			transactionTemplate.executeWithoutResult(status -> {
				ScheduleRequest draft = findDraft(draftId);
				draft.publish();
				repository.flush();
			});
			return PublishResult.published(draftId);
		} catch (DataIntegrityViolationException exception) {
			if (hasSqlState(exception, POSTGRES_EXCLUSION_VIOLATION)) {
				return markDraftAsConflict(draftId, findConflict(command).orElse(null));
			}
			throw exception;
		}
	}

	private Long savePublished(PublishCommand command) {
		ScheduleRequest request = ScheduleRequest.published(
				command.workDate(),
				command.startTime(),
				command.endTime(),
				command.requesterName(),
				command.workType());
		return repository.saveAndFlush(request).getId();
	}

	private PublishResult saveConflictDraft(
			PublishCommand command, ScheduleRequest conflictingRequest) {
		String detail = conflictDetail(conflictingRequest);
		Long draftId = transactionTemplate.execute(status -> {
			ScheduleRequest draft = ScheduleRequest.draft(
					command.workDate(),
					command.startTime(),
					command.endTime(),
					command.requesterName(),
					command.workType(),
					DraftReason.TIME_CONFLICT,
					detail);
			return repository.saveAndFlush(draft).getId();
		});
		return PublishResult.timeConflict(draftId);
	}

	private PublishResult markDraftAsConflict(
			Long draftId, ScheduleRequest conflictingRequest) {
		String detail = conflictDetail(conflictingRequest);
		transactionTemplate.executeWithoutResult(status -> {
			ScheduleRequest draft = findDraft(draftId);
			draft.markTimeConflict(detail);
			repository.flush();
		});
		return PublishResult.timeConflict(draftId);
	}

	private Optional<ScheduleRequest> findConflict(PublishCommand command) {
		if (command.startTime() == null || command.endTime() == null) {
			return Optional.empty();
		}
		return transactionTemplate.execute(status -> repository
				.findFirstByWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
						command.workDate(),
						EntryState.PUBLISHED,
						command.endTime(),
						command.startTime()));
	}

	private ScheduleRequest findDraft(Long draftId) {
		ScheduleRequest request = repository
				.findById(draftId)
				.orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));
		if (request.getEntryState() != EntryState.DRAFT) {
			throw new IllegalStateException("Request is not a draft: " + draftId);
		}
		return request;
	}

	private PublishCommand toCommand(ScheduleRequest request) {
		return new PublishCommand(
				request.getWorkDate(),
				request.getStartTime(),
				request.getEndTime(),
				request.getRequesterName(),
				request.getWorkType());
	}

	private String conflictDetail(ScheduleRequest request) {
		if (request == null) {
			return "既存案件と時間が重複しています";
		}
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
}
