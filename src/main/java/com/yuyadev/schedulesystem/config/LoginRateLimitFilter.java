package com.yuyadev.schedulesystem.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

class LoginRateLimitFilter extends OncePerRequestFilter {

	private final LoginAttemptService loginAttemptService;

	LoginRateLimitFilter(LoginAttemptService loginAttemptService) {
		this.loginAttemptService = loginAttemptService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String loginPath = request.getContextPath() + "/login";
		return !"POST".equals(request.getMethod()) || !loginPath.equals(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (loginAttemptService.isBlocked(request.getRemoteAddr())) {
			response.sendRedirect(request.getContextPath() + "/login?blocked=true");
			return;
		}
		filterChain.doFilter(request, response);
	}
}
