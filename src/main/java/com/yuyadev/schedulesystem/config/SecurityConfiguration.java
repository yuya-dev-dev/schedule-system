package com.yuyadev.schedulesystem.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(AccessGateProperties.class)
public class SecurityConfiguration {

	public static final String SHARED_USERNAME = "schedule-access";

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			AccessGateProperties accessGateProperties) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable);
		if (accessGateProperties.enabled()) {
			http.authorizeHttpRequests(auth -> auth
							.requestMatchers("/login", "/css/**", "/js/**", "/error", "/favicon.ico").permitAll()
							.anyRequest().authenticated())
					.formLogin(form -> form
							.loginPage("/login")
							.loginProcessingUrl("/login")
							.usernameParameter("accessUser")
							.passwordParameter("accessPassword")
							.defaultSuccessUrl("/schedule", true)
							.failureUrl("/login?error"))
					.httpBasic(AbstractHttpConfigurer::disable)
					.logout(AbstractHttpConfigurer::disable);
		}
		else {
			http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
					.httpBasic(AbstractHttpConfigurer::disable)
					.formLogin(AbstractHttpConfigurer::disable)
					.logout(AbstractHttpConfigurer::disable);
		}
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService(
			AccessGateProperties accessGateProperties,
			PasswordEncoder passwordEncoder) {
		accessGateProperties.validateIfEnabled();
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
