package com.yuyadev.schedulesystem.schedule;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleDayOffRepository extends JpaRepository<ScheduleDayOff, LocalDate> {

	List<ScheduleDayOff> findByWorkDateBetween(LocalDate startDate, LocalDate endDate);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ScheduleDayOff d where d.workDate < :workDate")
	long deleteByWorkDateBefore(@Param("workDate") LocalDate workDate);
}
