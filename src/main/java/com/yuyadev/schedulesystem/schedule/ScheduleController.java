package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.request.DraftManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ScheduleController {

	private final MonthScheduleService monthScheduleService;
	private final DraftManagementService draftManagementService;

	public ScheduleController(
			MonthScheduleService monthScheduleService,
			DraftManagementService draftManagementService) {
		this.monthScheduleService = monthScheduleService;
		this.draftManagementService = draftManagementService;
	}

	@GetMapping("/")
	public String home() {
		return "redirect:/schedule";
	}

	@GetMapping("/schedule")
	public String month(
			@RequestParam(required = false) String month,
			Model model) {
		model.addAttribute("schedule", monthScheduleService.getMonth(month));
		model.addAttribute("drafts", draftManagementService.activeDrafts());
		return "schedule/month";
	}
}
