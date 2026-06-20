package com.yuyadev.schedulesystem.schedule;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ScheduleController {

	private final MonthScheduleService monthScheduleService;

	public ScheduleController(MonthScheduleService monthScheduleService) {
		this.monthScheduleService = monthScheduleService;
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
		return "schedule/month";
	}
}
