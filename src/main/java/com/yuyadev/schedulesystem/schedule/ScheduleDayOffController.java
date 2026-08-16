package com.yuyadev.schedulesystem.schedule;

import static com.yuyadev.schedulesystem.schedule.SchedulePageSupport.dateTitle;
import static com.yuyadev.schedulesystem.schedule.SchedulePageSupport.scheduleUrl;

import com.yuyadev.schedulesystem.request.RecurringFixedRequestService;
import java.time.LocalDate;
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

	private final DayOffService dayOffService;
	private final RecurringFixedRequestService recurringFixedRequestService;

	public ScheduleDayOffController(
			DayOffService dayOffService,
			RecurringFixedRequestService recurringFixedRequestService) {
		this.dayOffService = dayOffService;
		this.recurringFixedRequestService = recurringFixedRequestService;
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
		try {
			DayOffService.DayOffResult result = dayOffService.setDayOff(date);
			redirectAttributes.addFlashAttribute(
					"notice", "休みにしました。削除件数: " + result.deletedCount() + "件");
			return "redirect:" + scheduleUrl(result.workDate());
		} catch (IllegalArgumentException exception) {
			throw badRequest(exception);
		}
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
		try {
			dayOffService.unsetDayOff(date);
			recurringFixedRequestService.ensureDateAfterDayOffUnset(date);
			redirectAttributes.addFlashAttribute("notice", "休みを解除しました");
			return "redirect:" + scheduleUrl(date);
		} catch (IllegalArgumentException exception) {
			throw badRequest(exception);
		}
	}

	private String renderSetConfirmation(LocalDate date, boolean secondStep, Model model) {
		try {
			DayOffConfirmation confirmation =
					dayOffService.confirmation(date, dateTitle(date));
			model.addAttribute("mode", "SET");
			model.addAttribute("confirmation", confirmation);
			model.addAttribute("date", date);
			model.addAttribute("dateTitle", confirmation.dateTitle());
			model.addAttribute("secondStep", secondStep);
			model.addAttribute("scheduleUrl", scheduleUrl(date));
			return "schedule/day-off-confirmation";
		} catch (IllegalArgumentException exception) {
			throw badRequest(exception);
		}
	}

	private ResponseStatusException badRequest(IllegalArgumentException exception) {
		return new ResponseStatusException(
				HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
	}
}
