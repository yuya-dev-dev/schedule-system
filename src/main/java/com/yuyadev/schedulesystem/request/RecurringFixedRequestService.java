package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import com.yuyadev.schedulesystem.schedule.ScheduleTimeSlots;
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

	private static final LocalTime FIXED_START_TIME = ScheduleTimeSlots.OPENING_TIME;
	private static final LocalTime FIXED_END_TIME = LocalTime.of(10, 0);
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

	public void ensureDateAfterDayOffUnset(LocalDate date) {
		YearMonth currentMonth = YearMonth.now(clock);
		YearMonth targetMonth = YearMonth.from(date);
		if (targetMonth.equals(currentMonth) || targetMonth.equals(currentMonth.plusMonths(1))) {
			ensureDate(date);
		}
	}

	public void recordSkipIfFixed(ScheduleRequest request) {
		if (isFixedRequest(request) && !skipRepository.existsById(request.getWorkDate())) {
			skipRepository.save(new RecurringFixedRequestSkip(request.getWorkDate()));
		}
	}

	private void ensureMonth(YearMonth month) {
		month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
				.filter(datePolicy::isScheduleWeekday)
				.forEach(this::ensureDate);
	}

	private void ensureDate(LocalDate date) {
		try {
			transactionTemplate.executeWithoutResult(status -> ensureDateInTransaction(date));
		} catch (DataIntegrityViolationException exception) {
			if (!PostgreSqlConflictDetector.isPublishedTimeOverlap(exception)) {
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

	private boolean hasPublishedConflict(LocalDate date) {
		return !requestRepository.findPublishedOverlaps(
				date, FIXED_START_TIME, FIXED_END_TIME).isEmpty();
	}

	private boolean isFixedRequest(ScheduleRequest request) {
		return request.getEntryState() == EntryState.PUBLISHED
				&& request.getStartTime().equals(FIXED_START_TIME)
				&& request.getEndTime().equals(FIXED_END_TIME)
				&& datePolicy.isScheduleWeekday(request.getWorkDate())
				&& request.getWorkType() == fixedWorkType(request.getWorkDate());
	}

	private WorkType fixedWorkType(LocalDate date) {
		return date.getDayOfWeek() == DayOfWeek.WEDNESDAY
				? WorkType.RECEIVING : WorkType.PRODUCT_MANAGEMENT;
	}
}
