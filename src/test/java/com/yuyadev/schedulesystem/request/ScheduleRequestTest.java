package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ScheduleRequestTest {

	private static final LocalDate WORK_DATE = LocalDate.of(2026, 6, 24);

	@Test
	void createsPublishedRequestForRegularWork() {
		ScheduleRequest request = ScheduleRequest.published(
				WORK_DATE,
				LocalTime.of(9, 0),
				LocalTime.of(11, 0),
				" 社員A ",
				WorkType.INSTALL);

		assertThat(request.getRequesterName()).isEqualTo("社員A");
		assertThat(request.getEntryState()).isEqualTo(EntryState.PUBLISHED);
	}

	@Test
	void allowsReceivingWithoutRequesterName() {
		ScheduleRequest request = ScheduleRequest.published(
				WORK_DATE,
				LocalTime.of(8, 30),
				LocalTime.of(9, 0),
				null,
				WorkType.RECEIVING);

		assertThat(request.getRequesterName()).isNull();
	}

	@Test
	void rejectsRegularWorkWithoutRequesterName() {
		assertThatThrownBy(() -> ScheduleRequest.published(
					WORK_DATE,
					LocalTime.of(9, 0),
					LocalTime.of(10, 0),
					" ",
					WorkType.DELIVERY))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Requester name");
	}

	@Test
	void rejectsEndTimeThatIsNotAfterStartTime() {
		assertThatThrownBy(() -> ScheduleRequest.published(
					WORK_DATE,
					LocalTime.of(10, 0),
					LocalTime.of(10, 0),
					"社員A",
					WorkType.COLLECT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("End time");
	}

	@Test
	void publishesWithoutWorkTypeWhenRequesterAndTimeRangeArePresent() {
		ScheduleRequest request = ScheduleRequest.published(
				WORK_DATE,
				LocalTime.of(10, 0),
				LocalTime.of(11, 0),
				"社員A",
				null);

		assertThat(request.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(request.getWorkType()).isNull();
	}

	@Test
	void rejectsTimesOutsideThirtyMinuteSlots() {
		assertThatThrownBy(() -> ScheduleRequest.published(
					WORK_DATE,
					LocalTime.of(14, 1),
					LocalTime.of(15, 0),
					"社員A",
					WorkType.INSTALL))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("30-minute");
	}

	@Test
	void publishesACompleteDraftAndClearsDraftReason() {
		ScheduleRequest request = ScheduleRequest.draft(
				WORK_DATE,
				LocalTime.of(15, 0),
				LocalTime.of(16, 0),
				"社員A",
				WorkType.EXCHANGE,
				DraftReason.INCOMPLETE,
				"入力不足");

		request.publish();

		assertThat(request.getEntryState()).isEqualTo(EntryState.PUBLISHED);
		assertThat(request.getDraftReason()).isNull();
		assertThat(request.getDraftErrorDetail()).isNull();
	}
}
