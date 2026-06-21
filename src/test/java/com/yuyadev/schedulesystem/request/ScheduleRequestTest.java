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
				.hasMessageContaining("終了時間");
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
				.hasMessageContaining("30分単位");
	}

	@Test
	void rejectsTimesOutsideBusinessHours() {
		assertThatThrownBy(() -> ScheduleRequest.published(
					WORK_DATE,
					LocalTime.of(8, 0),
					LocalTime.of(9, 0),
					"社員A",
					WorkType.INSTALL))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("8:30から17:30");

		assertThatThrownBy(() -> ScheduleRequest.published(
					WORK_DATE,
					LocalTime.of(17, 0),
					LocalTime.of(18, 0),
					"社員A",
					WorkType.INSTALL))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("8:30から17:30");
	}

	@Test
	void allowsTheFullBusinessDay() {
		ScheduleRequest request = ScheduleRequest.published(
				WORK_DATE,
				LocalTime.of(8, 30),
				LocalTime.of(17, 30),
				"社員A",
				WorkType.INSTALL);

		assertThat(request.getStartTime()).isEqualTo(LocalTime.of(8, 30));
		assertThat(request.getEndTime()).isEqualTo(LocalTime.of(17, 30));
	}

	@Test
	void trimsTextAndNormalizesBlankTextToNull() {
		ScheduleRequest request = ScheduleRequest.draft(new ScheduleRequestInput(
				WORK_DATE, LocalTime.of(9, 0), LocalTime.of(10, 0), WorkType.INSTALL,
				" 社員A ", " 作業内容 ", "   ", " 午後 ", false,
				" 使用しない ", LocalTime.of(8, 30), " 使用しない ",
				DispatchStatus.UNANSWERED, "   "));

		assertThat(request.getRequesterName()).isEqualTo("社員A");
		assertThat(request.getRequestDetail()).isEqualTo("作業内容");
		assertThat(request.getAddress()).isNull();
		assertThat(request.getDesiredArrivalTime()).isEqualTo("午後");
		assertThat(request.getMeetingPlace()).isNull();
		assertThat(request.getVehicleName()).isNull();
		assertThat(request.getNote()).isNull();
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

	@Test
	void reportsRequiredDetailsForNormalWork() {
		ScheduleRequest request = ScheduleRequest.draft(input(
				WorkType.INSTALL, "社員A", null, null, null, false, null, null));

		assertThat(request.canAppearOnSchedule()).isTrue();
		assertThat(request.missingRequiredFields())
				.containsExactly("依頼内容", "現場住所もしくは会社名", "顧客先到着希望時間");
	}

	@Test
	void reportsTimeRangeAndRequesterOnAnEmptyForm() {
		ScheduleRequest request = ScheduleRequest.draft(
				WORK_DATE, null, null, null, null,
				DraftReason.INCOMPLETE, "入力不足");

		assertThat(request.missingRequiredFields())
				.containsExactly("開始時間", "終了時間", "依頼者名");
		assertThat(request.hasMissingRequiredFields()).isTrue();
	}

	@Test
	void doesNotReportIncompleteWhenOnlyWorkTypeIsMissing() {
		ScheduleRequest request = ScheduleRequest.published(
				WORK_DATE, LocalTime.of(9, 0), LocalTime.of(10, 0), "社員A", null);

		assertThat(request.missingRequiredFields()).isEmpty();
		assertThat(request.hasMissingRequiredFields()).isFalse();
	}

	@Test
	void requiresMeetingPlaceAndDepartureTimeOnlyWithCompanion() {
		ScheduleRequest request = ScheduleRequest.draft(input(
				WorkType.DELIVERY, "社員A", "備品を配達", null, "午後",
				true, null, null));

		assertThat(request.missingRequiredFields()).containsExactly("集合場所", "出発時間");
	}

	@Test
	void clearsCompanionValuesWhenCompanionIsUnchecked() {
		ScheduleRequest request = ScheduleRequest.draft(input(
				WorkType.DELIVERY, "社員A", "備品を配達", "愛知県名古屋市中区", "午後",
				true, "名古屋支店", LocalTime.of(9, 0)));

		request.applyInput(input(
				WorkType.DELIVERY, "社員A", "備品を配達", "愛知県名古屋市中区", "午後",
				false, "残してはいけない", LocalTime.of(10, 0)));

		assertThat(request.getMeetingPlace()).isNull();
		assertThat(request.getDepartureTime()).isNull();
		assertThat(request.getVehicleName()).isNull();
	}

	@Test
	void internalWorkDoesNotRequireNormalDetails() {
		ScheduleRequest request = ScheduleRequest.draft(input(
				WorkType.RECEIVING, null, null, null, null, false, null, null));

		assertThat(request.canAppearOnSchedule()).isTrue();
		assertThat(request.missingRequiredFields()).isEmpty();
		assertThat(request.getRequestDetail()).isNull();
		assertThat(request.getNote()).isNull();
		assertThat(request.getDispatchStatus()).isEqualTo(DispatchStatus.UNANSWERED);
	}

	@Test
	void changingNormalWorkToInternalWorkClearsNormalDetails() {
		ScheduleRequest request = ScheduleRequest.draft(new ScheduleRequestInput(
				WORK_DATE, LocalTime.of(9, 0), LocalTime.of(10, 0), WorkType.INSTALL,
				"社員A", "設置作業", "愛知県名古屋市", "午後", true,
				"名古屋支店", LocalTime.of(8, 30), "車両A",
				DispatchStatus.DISPATCHED, "連絡事項"));

		request.applyInput(new ScheduleRequestInput(
				WORK_DATE, LocalTime.of(9, 0), LocalTime.of(10, 0), WorkType.RECEIVING,
				null, "残してはいけない", "残してはいけない", "残してはいけない", true,
				"残してはいけない", LocalTime.of(8, 30), "残してはいけない",
				DispatchStatus.REQUIRED, "残してはいけない"));

		assertThat(request.getRequestDetail()).isNull();
		assertThat(request.getAddress()).isNull();
		assertThat(request.getDesiredArrivalTime()).isNull();
		assertThat(request.isCompanionRequired()).isFalse();
		assertThat(request.getMeetingPlace()).isNull();
		assertThat(request.getDepartureTime()).isNull();
		assertThat(request.getVehicleName()).isNull();
		assertThat(request.getDispatchStatus()).isEqualTo(DispatchStatus.UNANSWERED);
		assertThat(request.getNote()).isNull();
	}

	private ScheduleRequestInput input(
			WorkType workType,
			String requester,
			String detail,
			String address,
			String arrival,
			boolean companion,
			String meetingPlace,
			LocalTime departureTime) {
		return new ScheduleRequestInput(
				WORK_DATE, LocalTime.of(9, 0), LocalTime.of(10, 0), workType,
				requester, detail, address, arrival, companion, meetingPlace,
				departureTime, "車両A", DispatchStatus.UNANSWERED, "備考");
	}
}
