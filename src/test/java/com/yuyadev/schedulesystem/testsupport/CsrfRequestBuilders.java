package com.yuyadev.schedulesystem.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public final class CsrfRequestBuilders {

	private CsrfRequestBuilders() {
	}

	public static MockHttpServletRequestBuilder post(
			String uriTemplate, Object... uriVariables) {
		return MockMvcRequestBuilders.post(uriTemplate, uriVariables).with(csrf());
	}
}
