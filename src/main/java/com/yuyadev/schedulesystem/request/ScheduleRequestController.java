package com.yuyadev.schedulesystem.request;

import com.yuyadev.schedulesystem.schedule.ScheduleDatePolicy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
			DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE);

	private final ScheduleRequestRepository repository;
	private final ScheduleRequestPublishingService publishingService;
	private final ScheduleRequestEditingService editingService;
	private final ScheduleDatePolicy datePolicy;

	public ScheduleRequestController(
			ScheduleRequestRepository repository,
			ScheduleRequestPublishingService publishingService,
			ScheduleRequestEditingService editingService,
			ScheduleDatePolicy datePolicy) {
		this.repository = repository;
		this.publishingService = publishingService;
		this.editingService = editingService;
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
		model.addAttribute(
				"startTimeOptions", timeOptions(OPENING_TIME, CLOSING_TIME.minusMinutes(30)));
		model.addAttribute("endTimeOptions", timeOptions(OPENING_TIME.plusMinutes(30), CLOSING_TIME));
		model.addAttribute("dateTitle", form.getWorkDate() == null
				? "日付未指定"
				: form.getWorkDate().format(DATE_TITLE));
		model.addAttribute("editing", form.getId() != null);
		model.addAttribute("requesterRequired", requiresRequester(form.getWorkType()));
		return "request/form";
	}

	private List<String> validate(ScheduleRequestForm form) {
		List<String> errors = new ArrayList<>();
		if (!datePolicy.isRegistrable(form.getWorkDate())) {
			errors.add("対象日は前月・当月・翌月に表示される水曜日または金曜日を指定してください");
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
			if (!isThirtyMinuteSlot(form.getStartTime())
					|| !isThirtyMinuteSlot(form.getEndTime())) {
				errors.add("開始時間と終了時間は30分単位で入力してください");
			}
		}
		if (requiresRequester(form.getWorkType())
				&& (form.getRequesterName() == null || form.getRequesterName().isBlank())) {
			errors.add("依頼者名は必須です");
		}
		return errors;
	}

	private boolean requiresRequester(WorkType workType) {
		return workType != WorkType.RECEIVING
				&& workType != WorkType.PRODUCT_MANAGEMENT;
	}

	private boolean isThirtyMinuteSlot(LocalTime time) {
		return time.getMinute() % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0;
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
