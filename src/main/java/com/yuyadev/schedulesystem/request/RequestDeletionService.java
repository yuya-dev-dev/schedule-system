package com.yuyadev.schedulesystem.request;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestDeletionService {

	private final ScheduleRequestRepository repository;

	public RequestDeletionService(ScheduleRequestRepository repository) {
		this.repository = repository;
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
	public LocalDate cancelPublished(Long id) {
		ScheduleRequest request = findPublished(id);
		LocalDate workDate = request.getWorkDate();
		repository.delete(request);
		return workDate;
	}

	private ScheduleRequest find(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("案件が見つかりません"));
	}
}
