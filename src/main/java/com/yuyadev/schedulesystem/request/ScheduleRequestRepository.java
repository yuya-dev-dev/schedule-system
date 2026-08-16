package com.yuyadev.schedulesystem.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRequestRepository extends JpaRepository<ScheduleRequest, Long> {

	@Query("""
			select request from ScheduleRequest request
			where request.workDate = :workDate
			  and request.entryState = com.yuyadev.schedulesystem.request.EntryState.PUBLISHED
			  and request.startTime < :requestedEndTime
			  and request.endTime > :requestedStartTime
			order by request.startTime
			""")
	List<ScheduleRequest> findPublishedOverlaps(
			@Param("workDate") LocalDate workDate,
			@Param("requestedStartTime") LocalTime requestedStartTime,
			@Param("requestedEndTime") LocalTime requestedEndTime);

	@Query("""
			select request from ScheduleRequest request
			where request.id <> :excludedId
			  and request.workDate = :workDate
			  and request.entryState = com.yuyadev.schedulesystem.request.EntryState.PUBLISHED
			  and request.startTime < :requestedEndTime
			  and request.endTime > :requestedStartTime
			order by request.startTime
			""")
	List<ScheduleRequest> findOtherPublishedOverlaps(
			@Param("excludedId") Long excludedId,
			@Param("workDate") LocalDate workDate,
			@Param("requestedStartTime") LocalTime requestedStartTime,
			@Param("requestedEndTime") LocalTime requestedEndTime);

	long countByEntryState(EntryState entryState);

	long countByWorkDateAndEntryState(LocalDate workDate, EntryState entryState);

	List<ScheduleRequest> findByWorkDateBetweenAndEntryStateOrderByWorkDateAscStartTimeAsc(
			LocalDate startDate, LocalDate endDate, EntryState entryState);

	List<ScheduleRequest> findByEntryStateAndWorkDateGreaterThanEqualOrderByUpdatedAtDesc(
			EntryState entryState, LocalDate workDate);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ScheduleRequest r where r.entryState = :entryState and r.workDate < :workDate")
	long deleteByEntryStateAndWorkDateBefore(
			@Param("entryState") EntryState entryState,
			@Param("workDate") LocalDate workDate);

	long deleteByWorkDate(LocalDate workDate);
}
