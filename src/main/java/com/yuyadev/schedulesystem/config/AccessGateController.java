package com.yuyadev.schedulesystem.config;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AccessGateController {

	private final AccessGateProperties accessGateProperties;

	public AccessGateController(AccessGateProperties accessGateProperties) {
		this.accessGateProperties = accessGateProperties;
	}

	@GetMapping("/login")
	String login(@RequestParam(required = false) String error, Model model) {
		if (!accessGateProperties.enabled()) {
			return "redirect:/schedule";
		}
		model.addAttribute("loginError", error != null);
		model.addAttribute("accessUsername", SecurityConfiguration.SHARED_USERNAME);
		return "auth/login";
	}
}
