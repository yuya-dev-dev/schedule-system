package com.yuyadev.schedulesystem.schedule;

import com.yuyadev.schedulesystem.config.AccessGateProperties;
import com.yuyadev.schedulesystem.request.DraftManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ScheduleController {

	private final MonthScheduleService monthScheduleService;
	private final DraftManagementService draftManagementService;
	private final AccessGateProperties accessGateProperties;

	public ScheduleController(
			MonthScheduleService monthScheduleService,
			DraftManagementService draftManagementService,
			AccessGateProperties accessGateProperties) {
		this.monthScheduleService = monthScheduleService;
		this.draftManagementService = draftManagementService;
		this.accessGateProperties = accessGateProperties;
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
		SchedulePageSupport.MonthSelection selection =
				SchedulePageSupport.resolveMonth(month, year, monthNumber, null);
		if (selection.error() != null) {
			model.addAttribute("monthSelectionError", selection.error());
		}
		model.addAttribute("schedule", monthScheduleService.getMonth(selection.requestedMonth()));
		model.addAttribute(
				"drafts", draftManagementService.findActiveDrafts());
		model.addAttribute("accessGateEnabled", accessGateProperties.enabled());
		return "schedule/month";
	}
}
