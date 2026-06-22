package com.yuyadev.schedulesystem.request;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ScheduleRequestNotFoundException extends RuntimeException {

	public ScheduleRequestNotFoundException(String message) {
		super(message);
	}
}
