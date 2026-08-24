package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import com.yuyadev.schedulesystem.schedule.ScheduleDateTransactionLock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RequestCopyService {

	private final ScheduleRequestRepository repository;
	private final ScheduleDatePolicy datePolicy;
	private final ScheduleDateTransactionLock dateTransactionLock;
	private final TransactionTemplate transactionTemplate;

	public RequestCopyService(
			ScheduleRequestRepository repository,
			ScheduleDatePolicy datePolicy,
			ScheduleDateTransactionLock dateTransactionLock,
			PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.datePolicy = datePolicy;
		this.dateTransactionLock = dateTransactionLock;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public ScheduleRequest copyableSource(Long sourceId) {
		ScheduleRequest source = repository.findById(sourceId)
				.orElseThrow(() -> new IllegalArgumentException("コピー元の案件が見つかりません"));
		if (source.getEntryState() != EntryState.PUBLISHED) {
			throw new IllegalArgumentException("公開済み案件だけコピーできます");
		}
		if (datePolicy.isPast(source.getWorkDate())) {
			throw new IllegalArgumentException("過去案件はコピーできません");
		}
		return source;
	}

	public RequestCopyResult copy(Long sourceId, LocalDate targetDate) {
		ScheduleRequest source = copyableSource(sourceId);
		if (targetDate == null) {
			return RequestCopyResult.invalid("コピー先の日付を入力してください");
		}
		if (source.getWorkDate().equals(targetDate)) {
			return RequestCopyResult.invalid("コピー元と同じ日は選択できません");
		}
		try {
			return transactionTemplate.execute(status -> copyInTransaction(source, targetDate));
		} catch (DataIntegrityViolationException exception) {
			if (!PostgreSqlConflictDetector.isPublishedTimeOverlap(exception)) {
				throw exception;
			}
			return RequestCopyResult.timeConflict(
					copiedForm(source, targetDate), "その時間はすでに埋まっています");
		}
	}

	private RequestCopyResult copyInTransaction(ScheduleRequest source, LocalDate targetDate) {
		dateTransactionLock.lock(targetDate);
		try {
			datePolicy.requireRegistrable(targetDate);
		} catch (IllegalArgumentException exception) {
			return RequestCopyResult.invalid(exception.getMessage());
		}

		ScheduleRequestInput input = ScheduleRequestInput.forCopy(source, targetDate);
		Optional<ScheduleRequest> conflict = findConflict(input);
		if (conflict.isPresent()) {
			return RequestCopyResult.timeConflict(
					copiedForm(source, targetDate), "その時間はすでに埋まっています");
		}

		ScheduleRequest copied = ScheduleRequest.draft(input);
		copied.publish();
		ScheduleRequest saved = repository.saveAndFlush(copied);
		return RequestCopyResult.copied(saved.getId());
	}

	private Optional<ScheduleRequest> findConflict(ScheduleRequestInput input) {
		return repository.findPublishedOverlaps(
				input.workDate(), input.startTime(), input.endTime()).stream().findFirst();
	}

	private ScheduleRequestForm copiedForm(ScheduleRequest source, LocalDate targetDate) {
		ScheduleRequestForm form = ScheduleRequestForm.from(source);
		form.setId(null);
		form.setVersion(0);
		form.setWorkDate(targetDate);
		return form;
	}
}
