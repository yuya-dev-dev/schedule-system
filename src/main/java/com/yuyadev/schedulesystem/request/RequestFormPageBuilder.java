package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
public class RequestFormPageBuilder {

	private static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
	private static final LocalTime CLOSING_TIME = LocalTime.of(17, 30);
	private static final DateTimeFormatter DATE_TITLE =
			DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE);

	private final ScheduleRequestRepository repository;
	private final ScheduleDatePolicy datePolicy;

	public RequestFormPageBuilder(
			ScheduleRequestRepository repository,
			ScheduleDatePolicy datePolicy) {
		this.repository = repository;
		this.datePolicy = datePolicy;
	}

	public String render(ScheduleRequestForm form, List<String> errors, Model model) {
		return render(form, errors, model, false);
	}

	public String render(
			ScheduleRequestForm form,
			List<String> errors,
			Model model,
			boolean returnOnly) {
		model.addAttribute("form", form);
		model.addAttribute("errors", errors);
		model.addAttribute("workTypes", WorkType.values());
		model.addAttribute("dispatchStatuses", DispatchStatus.values());
		model.addAttribute(
				"startTimeOptions", timeOptions(OPENING_TIME, CLOSING_TIME.minusMinutes(30)));
		model.addAttribute("endTimeOptions", timeOptions(OPENING_TIME.plusMinutes(30), CLOSING_TIME));
		model.addAttribute("dateTitle", form.getWorkDate() == null
				? "日付未指定"
				: form.getWorkDate().format(DATE_TITLE));
		model.addAttribute("editing", form.getId() != null);
		boolean draft = form.getId() != null
				&& repository.findById(form.getId())
						.map(request -> request.getEntryState() == EntryState.DRAFT)
						.orElse(false);
		model.addAttribute("draft", draft);
		model.addAttribute("requesterRequired", requiresRequester(form.getWorkType()));
		model.addAttribute("normalWork", form.getWorkType() != null
				&& !ScheduleRequest.isInternalWork(form.getWorkType()));
		boolean readOnly = datePolicy.isPast(form.getWorkDate());
		model.addAttribute("readOnly", readOnly);
		model.addAttribute("returnOnly", returnOnly || readOnly);
		model.addAttribute("copyAllowed", form.getId() != null && !draft && !readOnly);
		model.addAttribute("scheduleUrl", form.getWorkDate() == null
				? "/schedule"
				: scheduleUrl(form.getWorkDate()));
		return "request/form";
	}

	private String scheduleUrl(LocalDate date) {
		return "/schedule?month=" + date.getYear()
				+ "-" + String.format("%02d", date.getMonthValue());
	}

	private boolean requiresRequester(WorkType workType) {
		return workType != WorkType.RECEIVING
				&& workType != WorkType.PRODUCT_MANAGEMENT;
	}

	private List<LocalTime> timeOptions(LocalTime first, LocalTime last) {
		List<LocalTime> options = new ArrayList<>();
		for (LocalTime time = first; !time.isAfter(last); time = time.plusMinutes(30)) {
			options.add(time);
		}
		return List.copyOf(options);
	}
}
