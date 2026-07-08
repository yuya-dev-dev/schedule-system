package com.yuyadev.schedulesystem.request;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringFixedRequestSkipRepository
		extends JpaRepository<RecurringFixedRequestSkip, LocalDate> {
}
