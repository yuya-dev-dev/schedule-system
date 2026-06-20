package com.yuyadev.schedulesystem.request;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftManagementService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日");
	private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("M月d日 H:mm");

	private final ScheduleRequestRepository repository;
	private final Clock clock;

	public DraftManagementService(ScheduleRequestRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public List<DraftListItem> activeDrafts() {
		LocalDate today = LocalDate.now(clock);
		repository.deleteByEntryStateAndWorkDateBefore(EntryState.DRAFT, today);
		return repository
				.findByEntryStateAndWorkDateGreaterThanEqualOrderByUpdatedAtDesc(
						EntryState.DRAFT, today)
				.stream()
				.map(this::toListItem)
				.toList();
	}

	@Transactional(readOnly = true)
	public ScheduleRequest findDraft(Long id) {
		ScheduleRequest request = find(id);
		if (request.getEntryState() != EntryState.DRAFT) {
			throw new IllegalArgumentException("下書きではありません");
		}
		return request;
	}

	@Transactional
	public LocalDate deleteDraft(Long id) {
		ScheduleRequest request = findDraft(id);
		LocalDate workDate = request.getWorkDate();
		repository.delete(request);
		return workDate;
	}

	private ScheduleRequest find(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("下書きが見つかりません"));
	}

	private DraftListItem toListItem(ScheduleRequest request) {
		String requester = request.getRequesterName() == null
				? "依頼者名未入力" : request.getRequesterName();
		String reason = request.getDraftReason() == DraftReason.TIME_CONFLICT
				? "時間重複" : "入力不足";
		String detail = request.getDraftReason() == DraftReason.TIME_CONFLICT
				? request.getDraftErrorDetail() : missingDetail(request);
		return new DraftListItem(
				request.getId(),
				request.getWorkDate().format(DATE_FORMAT),
				requester,
				request.getUpdatedAt().format(UPDATED_AT_FORMAT),
				reason,
				detail);
	}

	private String missingDetail(ScheduleRequest request) {
		List<String> missing = request.missingRequiredFields();
		return missing.isEmpty() ? "詳細項目が未入力" : String.join("、", missing) + "が未入力";
	}
}
