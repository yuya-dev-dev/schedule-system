package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.request.DraftManagementService;
import com.yuyadev.schedulesystem.request.RecurringFixedRequestService;
import java.time.DateTimeException;
import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ScheduleController {

	private final MonthScheduleService monthScheduleService;
	private final DraftManagementService draftManagementService;
	private final RecurringFixedRequestService recurringFixedRequestService;

	public ScheduleController(
			MonthScheduleService monthScheduleService,
			DraftManagementService draftManagementService,
			RecurringFixedRequestService recurringFixedRequestService) {
		this.monthScheduleService = monthScheduleService;
		this.draftManagementService = draftManagementService;
		this.recurringFixedRequestService = recurringFixedRequestService;
	}

	@GetMapping("/")
	public String home() {
		return "redirect:/schedule";
	}

	@GetMapping("/schedule")
	public String month(
			@RequestParam(required = false) String month,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String monthNumber,
			Model model) {
		String requestedMonth = resolveRequestedMonth(month, year, monthNumber, model);
		recurringFixedRequestService.ensureCurrentAndNextMonth();
		model.addAttribute("schedule", monthScheduleService.getMonth(requestedMonth));
		model.addAttribute("drafts", draftManagementService.activeDrafts());
		return "schedule/month";
	}

	private String resolveRequestedMonth(
			String month, String year, String monthNumber, Model model) {
		if (!StringUtils.hasText(year) && !StringUtils.hasText(monthNumber)) {
			return month;
		}
		try {
			int selectedYear = Integer.parseInt(year);
			int selectedMonth = Integer.parseInt(monthNumber);
			if (selectedYear < 1) {
				throw new DateTimeException("Year must be positive");
			}
			return YearMonth.of(selectedYear, selectedMonth).toString();
		} catch (DateTimeException | NumberFormatException exception) {
			model.addAttribute("monthSelectionError", "正しい年と月を入力してください");
			return month;
		}
	}
}
