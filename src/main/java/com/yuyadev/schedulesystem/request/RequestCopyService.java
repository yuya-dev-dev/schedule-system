package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class RequestCopyService {

	private final ScheduleRequestRepository repository;
	private final ScheduleDatePolicy datePolicy;

	public RequestCopyService(
			ScheduleRequestRepository repository,
			ScheduleDatePolicy datePolicy) {
		this.repository = repository;
		this.datePolicy = datePolicy;
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
			datePolicy.requireRegistrable(targetDate);
		} catch (IllegalArgumentException exception) {
			return RequestCopyResult.invalid(exception.getMessage());
		}

		ScheduleRequestInput input = copyInput(source, targetDate);
		Optional<ScheduleRequest> conflict = findConflict(input);
		if (conflict.isPresent()) {
			return RequestCopyResult.timeConflict(
					copiedForm(source, targetDate), "その時間はすでに埋まっています");
		}

		try {
			ScheduleRequest copied = ScheduleRequest.draft(input);
			copied.publish();
			ScheduleRequest saved = repository.saveAndFlush(copied);
			return RequestCopyResult.copied(saved.getId());
		} catch (DataIntegrityViolationException exception) {
			return RequestCopyResult.timeConflict(
					copiedForm(source, targetDate), "その時間はすでに埋まっています");
		}
	}

	private Optional<ScheduleRequest> findConflict(ScheduleRequestInput input) {
		return repository
				.findFirstByWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
						input.workDate(), EntryState.PUBLISHED, input.endTime(), input.startTime());
	}

	private ScheduleRequestInput copyInput(ScheduleRequest source, LocalDate targetDate) {
		return new ScheduleRequestInput(
				targetDate,
				source.getStartTime(),
				source.getEndTime(),
				source.getWorkType(),
				source.getRequesterName(),
				source.getRequestDetail(),
				source.getAddress(),
				source.getDesiredArrivalTime(),
				source.isCompanionRequired(),
				source.getMeetingPlace(),
				source.getDepartureTime(),
				source.getVehicleName(),
				source.getDispatchStatus(),
				source.getNote());
	}

	private ScheduleRequestForm copiedForm(ScheduleRequest source, LocalDate targetDate) {
		ScheduleRequestForm form = ScheduleRequestForm.from(source);
		form.setId(null);
		form.setVersion(0);
		form.setWorkDate(targetDate);
		return form;
	}
}
