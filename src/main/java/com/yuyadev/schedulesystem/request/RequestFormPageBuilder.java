package com.yuyadev.schedulesystem.request;

import static com.yuyadev.schedulesystem.schedule.SchedulePageSupport.dateTitle;
import static com.yuyadev.schedulesystem.schedule.SchedulePageSupport.scheduleUrl;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import com.yuyadev.schedulesystem.schedule.ScheduleTimeSlots;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
public class RequestFormPageBuilder {

	private final ScheduleDatePolicy datePolicy;

	public RequestFormPageBuilder(ScheduleDatePolicy datePolicy) {
		this.datePolicy = datePolicy;
	}

	public String render(
			ScheduleRequestForm form,
			EntryState entryState,
			List<String> errors,
			Model model) {
		return render(form, entryState, errors, model, false);
	}

	public String render(
			ScheduleRequestForm form,
			EntryState entryState,
			List<String> errors,
			Model model,
			boolean returnOnly) {
		addSelectionOptions(model);
		addFormState(form, entryState, errors, returnOnly, model);
		return "request/form";
	}

	private void addSelectionOptions(Model model) {
		model.addAttribute("workTypes", WorkType.values());
		model.addAttribute("dispatchStatuses", DispatchStatus.values());
		model.addAttribute("startTimeOptions", ScheduleTimeSlots.startTimes());
		model.addAttribute("endTimeOptions", ScheduleTimeSlots.endTimes());
	}

	private void addFormState(
			ScheduleRequestForm form,
			EntryState entryState,
			List<String> errors,
			boolean returnOnly,
			Model model) {
		model.addAttribute("form", form);
		model.addAttribute("errors", errors);
		model.addAttribute("dateTitle", dateTitle(form.getWorkDate()));
		model.addAttribute("editing", form.getId() != null);
		boolean draft = entryState == EntryState.DRAFT;
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

}
