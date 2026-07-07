package com.yuyadev.schedulesystem.retention;

import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.schedule.ScheduleDayOffRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleDataRetentionService {

	private static final Logger log = LoggerFactory.getLogger(ScheduleDataRetentionService.class);

	private final ScheduleRequestRepository requestRepository;
	private final ScheduleDayOffRepository dayOffRepository;
	private final Clock clock;

	public ScheduleDataRetentionService(
			ScheduleRequestRepository requestRepository,
			ScheduleDayOffRepository dayOffRepository,
			Clock clock) {
		this.requestRepository = requestRepository;
		this.dayOffRepository = dayOffRepository;
		this.clock = clock;
	}

	@Transactional
	public RetentionCleanupResult deleteExpiredScheduleData() {
		LocalDate cutoffDate = LocalDate.now(clock).minusMonths(1);
		long publishedRequests = requestRepository.deleteByEntryStateAndWorkDateBefore(
				EntryState.PUBLISHED, cutoffDate);
		long drafts = requestRepository.deleteByEntryStateAndWorkDateBefore(
				EntryState.DRAFT, cutoffDate);
		long dayOffs = dayOffRepository.deleteByWorkDateBefore(cutoffDate);
		RetentionCleanupResult result = new RetentionCleanupResult(
				publishedRequests, drafts, dayOffs);
		log.info(
				"Expired schedule data cleanup completed: publishedRequests={}, drafts={}, dayOffs={}",
				result.publishedRequests(), result.drafts(), result.dayOffs());
		return result;
	}

	public record RetentionCleanupResult(
			long publishedRequests,
			long drafts,
			long dayOffs) {

		public long totalDeleted() {
			return publishedRequests + drafts + dayOffs;
		}
	}
}
