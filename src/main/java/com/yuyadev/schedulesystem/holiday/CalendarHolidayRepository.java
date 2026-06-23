package com.yuyadev.schedulesystem.holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarHolidayRepository extends JpaRepository<CalendarHoliday, LocalDate> {

	List<CalendarHoliday> findByHolidayDateBetween(LocalDate startDate, LocalDate endDate);

	Optional<CalendarHoliday> findTopByOrderBySyncedAtDesc();
}
