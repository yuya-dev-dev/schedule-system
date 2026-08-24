package com.yuyadev.schedulesystem.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScheduleRequestFormValidationTest {

	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		validator = Validation.buildDefaultValidatorFactory().getValidator();
	}

	@ParameterizedTest(name = "{0} rejects more than {1} characters")
	@MethodSource("limitedTextFields")
	void appliesServerSideLengthLimits(
			String fieldName,
			int maximumLength,
			BiConsumer<ScheduleRequestForm, String> setter) {
		ScheduleRequestForm form = new ScheduleRequestForm();
		setter.accept(form, "a".repeat(maximumLength));
		assertThat(validator.validate(form)).isEmpty();

		setter.accept(form, "a".repeat(maximumLength + 1));

		Set<ConstraintViolation<ScheduleRequestForm>> violations = validator.validate(form);

		assertThat(violations)
				.singleElement()
				.satisfies(violation -> assertThat(violation.getPropertyPath().toString())
						.isEqualTo(fieldName));
	}

	private static Stream<Arguments> limitedTextFields() {
		return Stream.of(
				Arguments.of("requesterName", 100,
						(BiConsumer<ScheduleRequestForm, String>) ScheduleRequestForm::setRequesterName),
				Arguments.of("requestDetail", 4000,
						(BiConsumer<ScheduleRequestForm, String>) ScheduleRequestForm::setRequestDetail),
				Arguments.of("address", 500,
						(BiConsumer<ScheduleRequestForm, String>) ScheduleRequestForm::setAddress),
				Arguments.of("desiredArrivalTime", 100,
						(BiConsumer<ScheduleRequestForm, String>) ScheduleRequestForm::setDesiredArrivalTime),
				Arguments.of("meetingPlace", 300,
						(BiConsumer<ScheduleRequestForm, String>) ScheduleRequestForm::setMeetingPlace),
				Arguments.of("vehicleName", 100,
						(BiConsumer<ScheduleRequestForm, String>) ScheduleRequestForm::setVehicleName),
				Arguments.of("note", 4000,
						(BiConsumer<ScheduleRequestForm, String>) ScheduleRequestForm::setNote));
	}
}
