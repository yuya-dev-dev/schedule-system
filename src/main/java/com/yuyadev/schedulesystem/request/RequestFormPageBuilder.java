package com.yuyadev.schedulesystem.request;

import static com.yuyadev.schedulesystem.schedule.SchedulePageSupport.dateTitle;
import static com.yuyadev.schedulesystem.schedule.SchedulePageSupport.scheduleUrl;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
public class RequestFormPageBuilder {

	private static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
	private static final LocalTime CLOSING_TIME = LocalTime.of(17, 30);
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
		addSelectionOptions(model);
		addFormState(form, errors, returnOnly, model);
		return "request/form";
	}

	private void addSelectionOptions(Model model) {
		model.addAttribute("workTypes", WorkType.values());
		model.addAttribute("dispatchStatuses", DispatchStatus.values());
		model.addAttribute(
				"startTimeOptions", timeOptions(OPENING_TIME, CLOSING_TIME.minusMinutes(30)));
		model.addAttribute("endTimeOptions", timeOptions(OPENING_TIME.plusMinutes(30), CLOSING_TIME));
	}

	private void addFormState(
			ScheduleRequestForm form,
			List<String> errors,
			boolean returnOnly,
			Model model) {
		model.addAttribute("form", form);
		model.addAttribute("errors", errors);
		model.addAttribute("dateTitle", dateTitle(form.getWorkDate()));
		model.addAttribute("editing", form.getId() != null);
		boolean draft = isDraft(form);
		model.addAttribute("draft", draft);
		model.addAttribute("requesterRequired", ScheduleRequest.requiresRequester(form.getWorkType()));
		model.addAttribute("normalWork", form.getWorkType() != null
				&& !ScheduleRequest.isInternalWork(form.getWorkType()));
		boolean readOnly = datePolicy.isPast(form.getWorkDate());
		model.addAttribute("readOnly", readOnly);
		model.addAttribute("returnOnly", returnOnly || readOnly);
		model.addAttribute("copyAllowed", form.getId() != null && !draft && !readOnly);
		model.addAttribute("scheduleUrl", scheduleUrl(form.getWorkDate()));
	}

	private boolean isDraft(ScheduleRequestForm form) {
		return form.getId() != null
				&& repository.findById(form.getId())
						.map(request -> request.getEntryState() == EntryState.DRAFT)
						.orElse(false);
	}

	private List<LocalTime> timeOptions(LocalTime first, LocalTime last) {
		List<LocalTime> options = new ArrayList<>();
		for (LocalTime time = first; !time.isAfter(last); time = time.plusMinutes(30)) {
			options.add(time);
		}
		return List.copyOf(options);
	}
}
