package com.yuyadev.schedulesystem.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
		"schedule.holidays.sync-enabled=false",
		"schedule.security.access-gate.password=cloud-pass"
})
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
class CloudProfileConfigurationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17-alpine").withStartupTimeout(Duration.ofMinutes(2));

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void registerPostgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Test
	void startsCloudProfileWithPostgresqlAndPasswordAccessGate() throws Exception {
		mockMvc.perform(get("/schedule"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login"));

		MvcResult result = mockMvc.perform(post("/login")
						.param("accessUser", SecurityConfiguration.SHARED_USERNAME)
						.param("accessPassword", "cloud-pass"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/schedule"))
				.andReturn();
		HttpSession session = result.getRequest().getSession(false);

		mockMvc.perform(get("/schedule").session((MockHttpSession) session))
				.andExpect(status().isOk());
	}
}
