package com.yuyadev.schedulesystem.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AccessGateProperties.class)
public class SecurityConfiguration {

	public static final String SHARED_USERNAME = "schedule-access";

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			AccessGateProperties accessGateProperties,
			LoginAttemptService loginAttemptService) throws Exception {
		if (accessGateProperties.enabled()) {
			configurePasswordAccessGate(http, loginAttemptService);
		} else {
			configureOpenAccess(http);
		}
		return http.build();
	}

	private void configurePasswordAccessGate(
			HttpSecurity http,
			LoginAttemptService loginAttemptService) throws Exception {
		http.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/css/**", "/js/**", "/error", "/favicon.ico").permitAll()
						.anyRequest().authenticated())
				.formLogin(form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login")
						.usernameParameter("accessUser")
						.passwordParameter("accessPassword")
						.successHandler((request, response, authentication) -> {
							loginAttemptService.clear(request.getRemoteAddr());
							response.sendRedirect(request.getContextPath() + "/schedule");
						})
						.failureHandler((request, response, exception) -> {
							loginAttemptService.recordFailure(request.getRemoteAddr());
							response.sendRedirect(request.getContextPath() + "/login?error=true");
						}))
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session
						.sessionFixation(fixation -> fixation.migrateSession()))
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl("/login?logout=true")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID"))
				.addFilterBefore(
						new LoginRateLimitFilter(loginAttemptService),
						UsernamePasswordAuthenticationFilter.class);
	}

	private void configureOpenAccess(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);
	}

	@Bean
	UserDetailsService userDetailsService(
			AccessGateProperties accessGateProperties,
			PasswordEncoder passwordEncoder,
			Environment environment) {
		accessGateProperties.validate(
				environment.acceptsProfiles(Profiles.of("cloud")));
		String password = accessGateProperties.enabled()
				? accessGateProperties.password()
				: "disabled-access-gate-password";
		return new InMemoryUserDetailsManager(User.withUsername(SHARED_USERNAME)
				.password(passwordEncoder.encode(password))
				.roles("USER")
				.build());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
