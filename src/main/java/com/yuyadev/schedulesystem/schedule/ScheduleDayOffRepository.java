package com.yuyadev.schedulesystem.schedule;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleDayOffRepository extends JpaRepository<ScheduleDayOff, LocalDate> {

	List<ScheduleDayOff> findByWorkDateBetween(LocalDate startDate, LocalDate endDate);
}
