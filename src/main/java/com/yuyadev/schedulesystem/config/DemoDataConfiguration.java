package com.yuyadev.schedulesystem.config;

import com.yuyadev.schedulesystem.request.DispatchStatus;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestInput;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoDataConfiguration {

	@Bean
	ApplicationRunner demoDataLoader(
			ScheduleRequestRepository repository,
			Clock clock) {
		return args -> {
			if (repository.count() > 0) {
				return;
			}
			List<LocalDate> workDates = nextWorkDates(LocalDate.now(clock));
			repository.saveAll(List.of(
					complete(normalInput(
							workDates.get(0), LocalTime.of(9, 0), WorkType.INSTALL,
							"架空社員A", "架空のコーヒーサーバー1台を設置",
							"愛知県名古屋市中区架空町1-1", "10時頃")),
					complete(normalInput(
							workDates.get(0), LocalTime.of(11, 0), WorkType.COLLECT,
							"架空社員B", "架空のウォーターサーバー1台を回収",
							"愛知県豊田市架空町2-2", "午前中")),
					complete(normalInput(
							workDates.get(0), LocalTime.of(14, 0), WorkType.EXCHANGE,
							"架空社員C", "架空機器の交換作業",
							"愛知県岡崎市架空町3-3", "午後かつ16時まで")),
					complete(normalInput(
							workDates.get(1), LocalTime.of(9, 30), WorkType.DELIVERY,
							"架空社員D", "架空の商品を3箱配達",
							"愛知県刈谷市架空町4-4", "10時以降")),
					complete(internalInput(
							workDates.get(1), LocalTime.of(12, 0), WorkType.RECEIVING)),
					complete(internalInput(
							workDates.get(1), LocalTime.of(15, 0), WorkType.PRODUCT_MANAGEMENT))));
		};
	}

	private List<LocalDate> nextWorkDates(LocalDate from) {
		return Stream.iterate(from, date -> date.plusDays(1))
				.filter(date -> date.getDayOfWeek() == DayOfWeek.WEDNESDAY
						|| date.getDayOfWeek() == DayOfWeek.FRIDAY)
				.limit(2)
				.toList();
	}

	private ScheduleRequestInput normalInput(
			LocalDate workDate,
			LocalTime startTime,
			WorkType workType,
			String requesterName,
			String detail,
			String address,
			String arrivalTime) {
		return new ScheduleRequestInput(
				workDate, startTime, startTime.plusHours(1), workType, requesterName,
				detail, address, arrivalTime, false, null, null, null,
				DispatchStatus.UNANSWERED, "ポートフォリオ用の架空データ");
	}

	private ScheduleRequestInput internalInput(
			LocalDate workDate,
			LocalTime startTime,
			WorkType workType) {
		return new ScheduleRequestInput(
				workDate, startTime, startTime.plusHours(1), workType, null,
				null, null, null, false, null, null, null,
				DispatchStatus.UNANSWERED, null);
	}

	private ScheduleRequest complete(ScheduleRequestInput input) {
		ScheduleRequest request = ScheduleRequest.draft(input);
		request.publish();
		return request;
	}
}
