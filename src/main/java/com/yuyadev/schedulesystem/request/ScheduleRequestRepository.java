package com.yuyadev.schedulesystem.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRequestRepository extends JpaRepository<ScheduleRequest, Long> {

	Optional<ScheduleRequest>
			findFirstByWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
					LocalDate workDate,
					EntryState entryState,
					LocalTime requestedEndTime,
					LocalTime requestedStartTime);

	long countByEntryState(EntryState entryState);
}
