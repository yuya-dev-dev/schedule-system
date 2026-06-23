package com.yuyadev.schedulesystem.schedule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/schedule/day-offs")
public class ScheduleDayOffController {

	private static final DateTimeFormatter DATE_TITLE =
			DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE);

	private final DayOffService dayOffService;

	public ScheduleDayOffController(DayOffService dayOffService) {
		this.dayOffService = dayOffService;
	}

	@GetMapping("/new")
	public String newDayOff(@RequestParam LocalDate date, Model model) {
		return renderSetConfirmation(date, false, model);
	}

	@PostMapping("/confirm")
	public String confirmDayOff(@RequestParam LocalDate date, Model model) {
		return renderSetConfirmation(date, true, model);
	}

	@PostMapping
	public String setDayOff(
			@RequestParam LocalDate date,
			RedirectAttributes redirectAttributes) {
		DayOffService.DayOffResult result = execute(() -> dayOffService.setDayOff(date));
		redirectAttributes.addFlashAttribute(
				"notice", "休みにしました。削除件数: " + result.deletedCount() + "件");
		return "redirect:" + scheduleUrl(result.workDate());
	}

	@GetMapping("/{date}/delete")
	public String confirmUnset(@PathVariable LocalDate date, Model model) {
		model.addAttribute("mode", "UNSET");
		model.addAttribute("date", date);
		model.addAttribute("dateTitle", dateTitle(date));
		model.addAttribute("scheduleUrl", scheduleUrl(date));
		return "schedule/day-off-confirmation";
	}

	@PostMapping("/{date}/delete")
	public String unset(
			@PathVariable LocalDate date,
			RedirectAttributes redirectAttributes) {
		execute(() -> {
			dayOffService.unsetDayOff(date);
			return null;
		});
		redirectAttributes.addFlashAttribute("notice", "休みを解除しました");
		return "redirect:" + scheduleUrl(date);
	}

	private String renderSetConfirmation(LocalDate date, boolean secondStep, Model model) {
		DayOffConfirmation confirmation = execute(() ->
				dayOffService.confirmation(date, dateTitle(date)));
		model.addAttribute("mode", "SET");
		model.addAttribute("confirmation", confirmation);
		model.addAttribute("date", date);
		model.addAttribute("dateTitle", confirmation.dateTitle());
		model.addAttribute("secondStep", secondStep);
		model.addAttribute("scheduleUrl", scheduleUrl(date));
		return "schedule/day-off-confirmation";
	}

	private <T> T execute(Operation<T> operation) {
		try {
			return operation.run();
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
		}
	}

	private String dateTitle(LocalDate date) {
		return date == null ? "日付未指定" : date.format(DATE_TITLE);
	}

	private String scheduleUrl(LocalDate date) {
		if (date == null) {
			return "/schedule";
		}
		return "/schedule?month=" + date.getYear()
				+ "-" + String.format("%02d", date.getMonthValue());
	}

	@FunctionalInterface
	private interface Operation<T> {
		T run();
	}
}
