package com.yuyadev.schedulesystem.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/requests")
public class ScheduleRequestController {

	private static final LocalTime OPENING_TIME = LocalTime.of(8, 30);
	private static final LocalTime CLOSING_TIME = LocalTime.of(17, 30);
	private static final DateTimeFormatter DATE_TITLE =
			DateTimeFormatter.ofPattern("yyyy年M月d日（E）");

	private final ScheduleRequestRepository repository;
	private final ScheduleRequestPublishingService publishingService;
	private final ScheduleRequestEditingService editingService;

	public ScheduleRequestController(
			ScheduleRequestRepository repository,
			ScheduleRequestPublishingService publishingService,
			ScheduleRequestEditingService editingService) {
		this.repository = repository;
		this.publishingService = publishingService;
		this.editingService = editingService;
	}

	@GetMapping("/new")
	public String newRequest(@RequestParam LocalDate date, Model model) {
		if (!isWorkday(date)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "水曜日または金曜日を指定してください");
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
		List<String> errors = validate(form);
		if (!errors.isEmpty()) {
			return renderForm(form, errors, model);
		}

		if (form.getId() == null) {
			PublishResult result = publishingService.publish(form.toCommand());
			if (result.status() == PublishResult.Status.TIME_CONFLICT) {
				return renderForm(form, List.of(result.message()), model);
			}
		} else {
			EditResult result = editingService.update(form.getId(), form.getVersion(), form.toCommand());
			if (result.status() != EditResult.Status.UPDATED) {
				return renderForm(form, List.of(result.message()), model);
			}
		}
		return "redirect:/schedule?month=" + form.getWorkDate().getYear()
				+ "-" + String.format("%02d", form.getWorkDate().getMonthValue());
	}

	private String renderForm(
			ScheduleRequestForm form, List<String> errors, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("errors", errors);
		model.addAttribute("workTypes", WorkType.values());
		model.addAttribute("dateTitle", form.getWorkDate() == null
				? "日付未指定"
				: form.getWorkDate().format(DATE_TITLE));
		model.addAttribute("editing", form.getId() != null);
		return "request/form";
	}

	private List<String> validate(ScheduleRequestForm form) {
		List<String> errors = new ArrayList<>();
		if (form.getWorkDate() == null || !isWorkday(form.getWorkDate())) {
			errors.add("対象日は水曜日または金曜日を指定してください");
		}
		if (form.getStartTime() == null) {
			errors.add("開始時間は必須です");
		}
		if (form.getEndTime() == null) {
			errors.add("終了時間は必須です");
		}
		if (form.getStartTime() != null && form.getEndTime() != null) {
			if (!form.getEndTime().isAfter(form.getStartTime())) {
				errors.add("終了時間は開始時間より後にしてください");
			}
			if (form.getStartTime().isBefore(OPENING_TIME)
					|| form.getEndTime().isAfter(CLOSING_TIME)) {
				errors.add("時間は8:30から17:30の範囲で入力してください");
			}
		}
		if (form.getWorkType() == null) {
			errors.add("作業種別は必須です");
		}
		if (requiresRequester(form.getWorkType())
				&& (form.getRequesterName() == null || form.getRequesterName().isBlank())) {
			errors.add("依頼者名は必須です");
		}
		return errors;
	}

	private boolean requiresRequester(WorkType workType) {
		return workType != null
				&& workType != WorkType.RECEIVING
				&& workType != WorkType.PRODUCT_MANAGEMENT;
	}

	private boolean isWorkday(LocalDate date) {
		return date != null && (date.getDayOfWeek() == DayOfWeek.WEDNESDAY
				|| date.getDayOfWeek() == DayOfWeek.FRIDAY);
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
