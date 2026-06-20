package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/requests")
public class ScheduleRequestController {

	private static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
	private static final LocalTime CLOSING_TIME = LocalTime.of(17, 30);
	private static final DateTimeFormatter DATE_TITLE =
			DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE);

	private final ScheduleRequestRepository repository;
	private final ScheduleRequestAutosaveService autosaveService;
	private final ScheduleDatePolicy datePolicy;

	public ScheduleRequestController(
			ScheduleRequestRepository repository,
			ScheduleRequestAutosaveService autosaveService,
			ScheduleDatePolicy datePolicy) {
		this.repository = repository;
		this.autosaveService = autosaveService;
		this.datePolicy = datePolicy;
	}

	@GetMapping("/new")
	public String newRequest(@RequestParam LocalDate date, Model model) {
		if (!datePolicy.isRegistrable(date)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "表示対象月の水曜日または金曜日を指定してください");
		}
		return renderForm(ScheduleRequestForm.newFor(date), List.of(), model);
	}

	@GetMapping("/{id}")
	public String existingRequest(@PathVariable Long id, Model model) {
		ScheduleRequest request = publishedRequest(id);
		return renderForm(ScheduleRequestForm.from(request), List.of(), model);
	}

	@PostMapping("/save")
	public String save(@ModelAttribute("form") ScheduleRequestForm form, Model model) {
		AutosaveResult result = autosaveService.save(form.getId(), form.getVersion(), form.toInput());
		if (result.requestId() != null) {
			form.setId(result.requestId());
			form.setVersion(result.version());
		}
		if (result.status() != AutosaveResult.Status.SAVED) {
			return renderForm(form, List.of(result.message()), model, true);
		}
		return "redirect:" + scheduleUrl(form.getWorkDate());
	}

	@PostMapping(value = "/autosave", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public AutosaveResult autosave(@ModelAttribute ScheduleRequestForm form) {
		return autosaveService.save(form.getId(), form.getVersion(), form.toInput());
	}

	private String renderForm(
			ScheduleRequestForm form, List<String> errors, Model model) {
		return renderForm(form, errors, model, false);
	}

	private String renderForm(
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
		model.addAttribute("requesterRequired", requiresRequester(form.getWorkType()));
		model.addAttribute("normalWork", form.getWorkType() != null
				&& !ScheduleRequest.isInternalWork(form.getWorkType()));
		model.addAttribute("returnOnly", returnOnly);
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

	private ScheduleRequest publishedRequest(Long id) {
		ScheduleRequest request = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (request.getEntryState() != EntryState.PUBLISHED) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return request;
	}
}
