package com.yuyadev.schedulesystem.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "schedule.holidays.sync-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigurationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void permitsRequestsWhenAccessGateIsDisabled() throws Exception {
		mockMvc.perform(get("/schedule"))
				.andExpect(status().isOk());
	}

	@Test
	void protectsBusinessPostsWithCsrf() throws Exception {
		mockMvc.perform(post("/requests/autosave"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/requests/autosave").with(csrf()))
				.andExpect(status().isOk());
	}
}
