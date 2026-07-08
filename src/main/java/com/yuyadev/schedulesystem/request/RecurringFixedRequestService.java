package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecurringFixedRequestService {

	private static final LocalTime FIXED_START_TIME = LocalTime.of(8, 30);
	private static final LocalTime FIXED_END_TIME = LocalTime.of(10, 0);
	private static final String PUBLISHED_TIME_CONSTRAINT =
			"ex_schedule_requests_published_time";

	private final ScheduleRequestRepository requestRepository;
	private final RecurringFixedRequestSkipRepository skipRepository;
	private final ScheduleDatePolicy datePolicy;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;

	public RecurringFixedRequestService(
			ScheduleRequestRepository requestRepository,
			RecurringFixedRequestSkipRepository skipRepository,
			ScheduleDatePolicy datePolicy,
			Clock clock,
			PlatformTransactionManager transactionManager) {
		this.requestRepository = requestRepository;
		this.skipRepository = skipRepository;
		this.datePolicy = datePolicy;
		this.clock = clock;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public void ensureCurrentAndNextMonth() {
		YearMonth currentMonth = YearMonth.now(clock);
		ensureMonth(currentMonth);
		ensureMonth(currentMonth.plusMonths(1));
	}

	public void recordSkipIfFixed(ScheduleRequest request) {
		if (isFixedRequest(request) && !skipRepository.existsById(request.getWorkDate())) {
			skipRepository.save(new RecurringFixedRequestSkip(request.getWorkDate()));
		}
	}

	private void ensureMonth(YearMonth month) {
		month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
				.filter(this::isFixedWorkday)
				.forEach(this::ensureDate);
	}

	private void ensureDate(LocalDate date) {
		try {
			transactionTemplate.executeWithoutResult(status -> ensureDateInTransaction(date));
		} catch (DataIntegrityViolationException exception) {
			if (!isPublishedTimeOverlapViolation(exception)) {
				throw exception;
			}
			// Another request may have claimed the slot after the pre-check.
		}
	}

	private void ensureDateInTransaction(LocalDate date) {
		if (!datePolicy.isRegistrable(date) || skipRepository.existsById(date)) {
			return;
		}
		if (hasPublishedConflict(date)) {
			return;
		}
		requestRepository.saveAndFlush(ScheduleRequest.published(
				date, FIXED_START_TIME, FIXED_END_TIME, null, fixedWorkType(date)));
	}

	private boolean isPublishedTimeOverlapViolation(DataIntegrityViolationException exception) {
		Throwable current = exception;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && message.contains(PUBLISHED_TIME_CONSTRAINT)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private boolean hasPublishedConflict(LocalDate date) {
		return requestRepository
				.findFirstByWorkDateAndEntryStateAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTime(
						date, EntryState.PUBLISHED, FIXED_END_TIME, FIXED_START_TIME)
				.isPresent();
	}

	private boolean isFixedRequest(ScheduleRequest request) {
		return request.getEntryState() == EntryState.PUBLISHED
				&& request.getStartTime().equals(FIXED_START_TIME)
				&& request.getEndTime().equals(FIXED_END_TIME)
				&& isFixedWorkday(request.getWorkDate())
				&& request.getWorkType() == fixedWorkType(request.getWorkDate());
	}

	private boolean isFixedWorkday(LocalDate date) {
		return date.getDayOfWeek() == DayOfWeek.WEDNESDAY
				|| date.getDayOfWeek() == DayOfWeek.FRIDAY;
	}

	private WorkType fixedWorkType(LocalDate date) {
		return date.getDayOfWeek() == DayOfWeek.WEDNESDAY
				? WorkType.RECEIVING : WorkType.PRODUCT_MANAGEMENT;
	}
}
