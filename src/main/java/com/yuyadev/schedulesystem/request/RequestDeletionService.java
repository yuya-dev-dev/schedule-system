package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestDeletionService {

	public enum CancellationStatus {
		DELETED,
		CHANGED,
		READ_ONLY,
		NOT_FOUND
	}

	public record CancellationResult(CancellationStatus status, LocalDate workDate) {
	}

	private final ScheduleRequestRepository repository;
	private final ScheduleDatePolicy datePolicy;

	public RequestDeletionService(
			ScheduleRequestRepository repository, ScheduleDatePolicy datePolicy) {
		this.repository = repository;
		this.datePolicy = datePolicy;
	}

	@Transactional(readOnly = true)
	public ScheduleRequest findPublished(Long id) {
		ScheduleRequest request = find(id);
		if (request.getEntryState() != EntryState.PUBLISHED) {
			throw new IllegalArgumentException("一覧へ反映済みの案件ではありません");
		}
		return request;
	}

	@Transactional
	public CancellationResult cancelPublished(Long id, long expectedVersion) {
		ScheduleRequest request = repository.findById(id).orElse(null);
		if (request == null || request.getEntryState() != EntryState.PUBLISHED) {
			return new CancellationResult(CancellationStatus.NOT_FOUND, null);
		}
		LocalDate workDate = request.getWorkDate();
		if (datePolicy.isPast(workDate)) {
			return new CancellationResult(CancellationStatus.READ_ONLY, workDate);
		}
		if (request.getVersion() != expectedVersion) {
			return new CancellationResult(CancellationStatus.CHANGED, workDate);
		}
		repository.delete(request);
		return new CancellationResult(CancellationStatus.DELETED, workDate);
	}

	private ScheduleRequest find(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("案件が見つかりません"));
	}
}
