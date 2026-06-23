package com.yuyadev.schedulesystem.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRequestRepository extends JpaRepository<ScheduleRequest, Long> {

	Optional<ScheduleRequest>
			findFirstByWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
					LocalDate workDate,
					EntryState entryState,
					LocalTime requestedEndTime,
					LocalTime requestedStartTime);

	Optional<ScheduleRequest>
			findFirstByIdNotAndWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
					Long id,
					LocalDate workDate,
					EntryState entryState,
					LocalTime requestedEndTime,
					LocalTime requestedStartTime);

	long countByEntryState(EntryState entryState);

	long countByWorkDateAndEntryState(LocalDate workDate, EntryState entryState);

	List<ScheduleRequest> findByWorkDateBetweenAndEntryStateOrderByWorkDateAscStartTimeAsc(
			LocalDate startDate, LocalDate endDate, EntryState entryState);

	List<ScheduleRequest> findByEntryStateAndWorkDateGreaterThanEqualOrderByUpdatedAtDesc(
			EntryState entryState, LocalDate workDate);

	long deleteByEntryStateAndWorkDateBefore(EntryState entryState, LocalDate workDate);

	long deleteByWorkDate(LocalDate workDate);
}
