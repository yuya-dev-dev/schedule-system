package com.yuyadev.schedulesystem.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"schedule.security.access-gate.enabled=true",
		"schedule.security.access-gate.password=test-pass",
		"schedule.holidays.sync-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigurationAccessGateTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void redirectsRequestsWithoutSessionToLoginPage() throws Exception {
		mockMvc.perform(get("/schedule"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	void showsPasswordOnlyLoginPage() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("共有パスワードを入力してください。")))
				.andExpect(content().string(containsString("name=\"accessPassword\"")))
				.andExpect(content().string(containsString("type=\"hidden\" name=\"accessUser\"")));
	}

	@Test
	void rejectsWrongPassword() throws Exception {
		mockMvc.perform(post("/login")
						.param("accessUser", SecurityConfiguration.SHARED_USERNAME)
						.param("accessPassword", "wrong-pass"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void permitsRequestsAfterCorrectPasswordLogin() throws Exception {
		MvcResult result = mockMvc.perform(post("/login")
						.param("accessUser", SecurityConfiguration.SHARED_USERNAME)
						.param("accessPassword", "test-pass"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule"))
				.andReturn();
		HttpSession session = result.getRequest().getSession(false);

		mockMvc.perform(get("/schedule").session((org.springframework.mock.web.MockHttpSession) session))
				.andExpect(status().isOk());
	}
}
