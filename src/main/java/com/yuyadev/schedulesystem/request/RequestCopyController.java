package com.yuyadev.schedulesystem.request;

import static com.yuyadev.schedulesystem.schedule.SchedulePageSupport.dateTitle;

import com.yuyadev.schedulesystem.schedule.MonthScheduleService;
import com.yuyadev.schedulesystem.schedule.MonthScheduleView;
import com.yuyadev.schedulesystem.schedule.SchedulePageSupport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/requests/{sourceId}/copy")
public class RequestCopyController {

	private final RequestCopyService copyService;
	private final MonthScheduleService monthScheduleService;
	private final RequestFormPageBuilder formPageBuilder;

	public RequestCopyController(
			RequestCopyService copyService,
			MonthScheduleService monthScheduleService,
			RequestFormPageBuilder formPageBuilder) {
		this.copyService = copyService;
		this.monthScheduleService = monthScheduleService;
		this.formPageBuilder = formPageBuilder;
	}

	@GetMapping
	public String selectDestination(
			@PathVariable Long sourceId,
			@RequestParam(required = false) String month,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String monthNumber,
			Model model) {
		ScheduleRequest source = source(sourceId);
		String requestedMonth = resolveRequestedMonth(
				month, year, monthNumber, source.getWorkDate(), model);
		return renderSelection(source, requestedMonth, null, model);
	}

	@PostMapping
	public String copy(
			@PathVariable Long sourceId,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate targetDate,
			Model model) {
		RequestCopyResult result = copyService.copy(sourceId, targetDate);
		if (result.status() == RequestCopyResult.Status.COPIED) {
			return "redirect:/requests/" + result.copiedRequestId();
		}
		if (result.status() == RequestCopyResult.Status.TIME_CONFLICT) {
			return formPageBuilder.render(
					result.form(), List.of(result.message()), model, true);
		}
		ScheduleRequest source = source(sourceId);
		String requestedMonth = targetDate == null
				? YearMonth.from(source.getWorkDate()).toString()
				: YearMonth.from(targetDate).toString();
		return renderSelection(source, requestedMonth, result.message(), model);
	}

	private String renderSelection(
			ScheduleRequest source,
			String requestedMonth,
			String targetDateError,
			Model model) {
		MonthScheduleView schedule = monthScheduleService.getMonth(requestedMonth);
		model.addAttribute("source", source);
		model.addAttribute("sourceDateTitle", dateTitle(source.getWorkDate()));
		model.addAttribute("schedule", schedule);
		model.addAttribute("targetDateError", targetDateError);
		return "request/copy-destination";
	}

	private String resolveRequestedMonth(
			String month,
			String year,
			String monthNumber,
			LocalDate sourceDate,
			Model model) {
		SchedulePageSupport.MonthSelection selection = SchedulePageSupport.resolveMonth(
				month, year, monthNumber, YearMonth.from(sourceDate).toString());
		if (selection.error() != null) {
			model.addAttribute("monthSelectionError", selection.error());
		}
		return selection.requestedMonth();
	}

	private ScheduleRequest source(Long sourceId) {
		try {
			return copyService.copyableSource(sourceId);
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}
}
