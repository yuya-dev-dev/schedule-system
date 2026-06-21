package com.yuyadev.schedulesystem.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.yuyadev.schedulesystem.request.EntryState;
import com.yuyadev.schedulesystem.request.ScheduleRequest;
import com.yuyadev.schedulesystem.request.ScheduleRequestRepository;
import com.yuyadev.schedulesystem.request.WorkType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Import(E2eClockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleBrowserRecoveryE2ETest {

	private static final LocalDate FUTURE_WORK_DATE = LocalDate.of(2026, 6, 24);

	@LocalServerPort
	private int port;

	@Autowired
	private ScheduleRequestRepository repository;

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;

	@BeforeAll
	void launchBrowser() {
		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
	}

	@BeforeEach
	void prepareFixture() {
		repository.deleteAll();
		context = browser.newContext(new Browser.NewContextOptions()
				.setViewportSize(1280, 900));
		page = context.newPage();
	}

	@AfterEach
	void closeContext() {
		context.close();
		repository.deleteAll();
	}

	@AfterAll
	void closeBrowser() {
		browser.close();
		playwright.close();
	}

	@Test
	void leavesPristineFormWithoutCreatingDraft() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		page.locator(".back-submit").click();
		page.waitForURL("**/schedule?month=2026-06");

		assertThat(repository.count()).isZero();
		assertThat(page.locator(".draft-count").textContent()).isEqualTo("0");
	}

	@Test
	void reopensAndDeletesTimeOnlyDraft() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		page.locator("#startTime").selectOption("09:00");
		page.locator("#endTime").selectOption("10:00");
		waitForText(page, "#save-status", "下書き保存済み");
		page.locator(".back-submit").click();
		page.waitForURL("**/schedule?month=2026-06");

		assertThat(repository.count()).isOne();
		page.locator(".draft-item a").click();
		assertThat(page.locator("#startTime").inputValue()).isEqualTo("09:00");
		assertThat(page.locator("#endTime").inputValue()).isEqualTo("10:00");

		page.onceDialog(dialog -> dialog.accept());
		page.locator("#draft-delete-form button").click();
		page.waitForURL("**/schedule?month=2026-06");
		assertThat(repository.count()).isZero();
	}

	@Test
	void publishesRequestWithoutWorkTypeAndProtectsItsSlot() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		setListFieldsWithoutEvents(page, "10:00", "11:00", "社員A");
		page.locator("#requesterName").focus();
		page.locator("#requestDetail").focus();
		waitForText(page, "#save-status", "保存済み・一覧に反映中");
		page.locator(".back-submit").click();
		page.waitForURL("**/schedule?month=2026-06");

		Locator occupied = page.locator("a[aria-label='案件を開く']")
				.filter(new Locator.FilterOptions().setHasText("社員A")).first();
		assertThat(occupied.textContent()).doesNotContain("＊未入力");

		page.locator("a[href='/requests/new?date=2026-06-24']").first().click();
		setListFieldsWithoutEvents(page, "10:00", "11:00", "社員B");
		page.locator("#requesterName").focus();
		page.locator("#requestDetail").focus();
		waitForText(page, "#error-summary", "その時間はすでに埋まっています");

		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
	}

	@Test
	void togglesCompanionFieldsAndClearsTheirValues() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		page.locator("#workType").selectOption("INSTALL");
		setCompleteFormWithoutEvents(page, "11:00", "12:00", "社員A");
		page.locator("#requestDetail").fill("架空の設置作業");
		page.locator("#requestDetail").blur();
		waitForText(page, "#save-status", "保存済み・一覧に反映中");

		Locator companion = page.locator("input[name='companionRequired']");
		companion.check();
		assertThat(page.locator("#companion-fields").isVisible()).isTrue();
		assertThat(page.locator("#address").getAttribute("required")).isNull();
		assertThat(page.locator("#meetingPlace").getAttribute("required")).isNotNull();
		page.locator("#meetingPlace").fill("名古屋支店");
		page.locator("#departureTime").selectOption("10:30");
		page.locator("#vehicleName").fill("車両A");
		page.locator("#vehicleName").blur();
		waitForRequest(page, request -> request.isCompanionRequired()
				&& "名古屋支店".equals(request.getMeetingPlace()));

		companion.uncheck();
		assertThat(page.locator("#companion-fields").isHidden()).isTrue();
		assertThat(page.locator("#meetingPlace").inputValue()).isEmpty();
		assertThat(page.locator("#departureTime").inputValue()).isEmpty();
		assertThat(page.locator("#vehicleName").inputValue()).isEmpty();
		assertThat(page.locator("#address").getAttribute("required")).isNotNull();
		waitForRequest(page, request -> !request.isCompanionRequired()
				&& request.getMeetingPlace() == null
				&& request.getDepartureTime() == null
				&& request.getVehicleName() == null);
	}

	@Test
	void cancelsThenAcceptsSwitchToInternalWork() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		page.locator("#workType").selectOption("INSTALL");
		setCompleteFormWithoutEvents(page, "12:00", "13:00", "社員A");
		page.locator("#requestDetail").fill("架空の設置作業");
		page.locator("#requestDetail").blur();
		waitForText(page, "#save-status", "保存済み・一覧に反映中");

		page.onceDialog(dialog -> dialog.dismiss());
		page.locator("#workType").selectOption("RECEIVING");
		assertThat(page.locator("#workType").inputValue()).isEqualTo("INSTALL");
		assertThat(page.locator("#requestDetail").inputValue()).isEqualTo("架空の設置作業");

		page.onceDialog(dialog -> dialog.accept());
		page.locator("#workType").selectOption("RECEIVING");
		waitForText(page, "#save-status", "保存済み・一覧に反映中");
		assertThat(page.locator("#normal-work-fields").isHidden()).isTrue();
		assertThat(page.locator("#requestDetail").inputValue()).isEmpty();

		ScheduleRequest saved = repository.findAll().getFirst();
		assertThat(saved.getWorkType()).isEqualTo(WorkType.RECEIVING);
		assertThat(saved.getRequestDetail()).isNull();
	}

	@Test
	void cancelsPublishedRequestAndReleasesItsCells() {
		ScheduleRequest request = savePublished(
				FUTURE_WORK_DATE, "13:00", "14:00", "社員A", WorkType.DELIVERY);
		page.navigate(url("/requests/" + request.getId()));
		page.locator("#cancel-request-link").click();
		waitForText(page, ".cancel-warning", "元に戻せません");
		page.getByText("キャンセルを実行", new Page.GetByTextOptions().setExact(true)).click();
		page.waitForURL("**/schedule?month=2026-06");

		assertThat(repository.count()).isZero();
		assertThat(page.locator("a[href='/requests/new?date=2026-06-24']").count()).isPositive();
	}

	@Test
	void focusesCurrentMonthAndLeavesNextMonthAtStartOnMobile() {
		page.setViewportSize(390, 844);
		page.navigate(url("/schedule?month=2026-06"));
		page.waitForCondition(() -> scrollLeft(page) > 0);

		double currentScroll = scrollLeft(page);
		assertThat(currentScroll).isPositive();
		Locator focusHeading = page.locator("[data-work-date='2026-06-24']");
		assertThat(focusHeading.boundingBox().x).isGreaterThanOrEqualTo(0);
		assertThat(focusHeading.boundingBox().x).isLessThan(390);

		page.getByText("2026年7月", new Page.GetByTextOptions().setExact(true)).click();
		page.waitForURL("**/schedule?month=2026-07");
		assertThat(scrollLeft(page)).isZero();
	}

	@Test
	void savesFocusedInputOnceBeforeReturning() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		AtomicInteger autosaveCount = countAutosaves(page);
		setCompleteFormWithoutEvents(page, "14:00", "15:00", "社員A");
		page.locator("#requestDetail").fill("一覧へ戻る直前の入力");

		page.locator(".back-submit").click();
		page.waitForURL("**/schedule?month=2026-06");

		assertThat(autosaveCount).hasValue(1);
		assertThat(repository.findAll().getFirst().getRequestDetail())
				.isEqualTo("一覧へ戻る直前の入力");
	}

	@Test
	void waitsForPendingAutosaveAndPersistsLatestChangeBeforeReturning() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		AtomicInteger autosaveCount = countAutosaves(page);
		installHeldFirstFetch(page);
		setCompleteFormWithoutEvents(page, "15:00", "16:00", "");
		page.locator("#requesterName").fill("社員A");
		page.locator("#requesterName").blur();
		page.waitForCondition(() -> Boolean.TRUE.equals(page.evaluate(
				"() => typeof window.__releaseAutosave === 'function'")));

		page.locator("#note").fill("応答待ち中の最新メモ");
		page.locator(".back-submit").evaluate("element => element.click()");
		assertThat(page.url()).contains("/requests/new");
		page.evaluate("() => window.__releaseAutosave()");
		page.waitForURL("**/schedule?month=2026-06");

		assertThat(autosaveCount).hasValue(2);
		assertThat(repository.findAll().getFirst().getNote())
				.isEqualTo("応答待ち中の最新メモ");
	}

	@Test
	void returnsAfterConflictWithoutDuplicateAutosave() {
		savePublished(FUTURE_WORK_DATE, "10:00", "12:00", "社員A", WorkType.INSTALL);
		page.navigate(url("/requests/new?date=2026-06-24"));
		AtomicInteger autosaveCount = countAutosaves(page);
		setListFieldsWithoutEvents(page, "11:00", "13:00", "社員B");
		page.locator("#requesterName").focus();
		page.locator("#requestDetail").focus();
		waitForText(page, "#error-summary", "その時間はすでに埋まっています");

		page.locator(".back-submit").click();
		page.waitForURL("**/schedule?month=2026-06");

		assertThat(autosaveCount).hasValue(1);
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
		assertThat(page.locator(".draft-item").textContent()).contains("時間重複");
	}

	@Test
	void retainsInputAndRetriesAfterServerError() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		AtomicBoolean failNextAutosave = new AtomicBoolean(true);
		page.route("**/requests/autosave", route -> fulfillOneServerError(route, failNextAutosave));
		setCompleteFormWithoutEvents(page, "16:00", "17:00", "社員A");
		page.locator("#requestDetail").fill("500応答でも保持する入力");
		page.locator("#requestDetail").blur();
		waitForText(page, "#error-summary", "通信エラーのため保存できませんでした");

		assertThat(page.locator("#requestDetail").inputValue()).isEqualTo("500応答でも保持する入力");
		assertThat(repository.count()).isZero();
		page.locator("#retry-save").click();
		waitForText(page, "#save-status", "保存済み・一覧に反映中");
		assertThat(repository.findAll().getFirst().getRequestDetail())
				.isEqualTo("500応答でも保持する入力");
	}

	@Test
	void reportsDeletionDuringEditWithoutCreatingReplacement() {
		ScheduleRequest request = savePublished(
				FUTURE_WORK_DATE, "09:00", "10:00", "社員A", WorkType.EXCHANGE);
		page.navigate(url("/requests/" + request.getId()));
		repository.deleteById(request.getId());
		repository.flush();

		page.locator("#requesterName").fill("社員B");
		page.locator("#requesterName").blur();
		waitForText(page, "#error-summary", "案件は削除されています");

		assertThat(repository.count()).isZero();
		assertThat(page.locator("#requesterName").inputValue()).isEqualTo("社員B");
	}

	@Test
	void reloadsValuesImmediatelyAfterSuccessfulAutosave() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		setCompleteFormWithoutEvents(page, "16:00", "17:00", "社員A");
		page.locator("#requestDetail").fill("更新後も残る入力");
		page.locator("#requestDetail").blur();
		waitForText(page, "#save-status", "保存済み・一覧に反映中");
		Long requestId = repository.findAll().getFirst().getId();

		page.reload();
		assertThat(page.locator("#id").inputValue()).isEqualTo(requestId.toString());
		assertThat(page.locator("#requestDetail").inputValue()).isEqualTo("更新後も残る入力");
	}

	private AtomicInteger countAutosaves(Page target) {
		AtomicInteger count = new AtomicInteger();
		target.route("**/requests/autosave", route -> {
			count.incrementAndGet();
			route.resume();
		});
		return count;
	}

	private void fulfillOneServerError(Route route, AtomicBoolean failNextAutosave) {
		if (failNextAutosave.compareAndSet(true, false)) {
			route.fulfill(new Route.FulfillOptions().setStatus(500).setBody("server error"));
			return;
		}
		route.resume();
	}

	private void installHeldFirstFetch(Page target) {
		target.evaluate("""
				() => {
				  const originalFetch = window.fetch.bind(window);
				  window.__releaseAutosave = null;
				  let holdFirst = true;
				  window.fetch = (...args) => {
				    if (String(args[0]).includes('/requests/autosave')) {
				      if (holdFirst) {
				        holdFirst = false;
				        return new Promise((resolve, reject) => {
				          window.__releaseAutosave = () => originalFetch(...args).then(resolve, reject);
				        });
				      }
				    }
				    return originalFetch(...args);
				  };
				}
				""");
	}

	private void setListFieldsWithoutEvents(
			Page target, String start, String end, String requester) {
		target.evaluate("""
				values => {
				  document.querySelector('#startTime').value = values.start;
				  document.querySelector('#endTime').value = values.end;
				  document.querySelector('#requesterName').value = values.requester;
				}
				""", java.util.Map.of("start", start, "end", end, "requester", requester));
	}

	private void setCompleteFormWithoutEvents(
			Page target, String start, String end, String requester) {
		target.evaluate("""
				values => {
				  document.querySelector('#startTime').value = values.start;
				  document.querySelector('#endTime').value = values.end;
				  document.querySelector('#workType').value = 'INSTALL';
				  document.querySelector('#requesterName').value = values.requester;
				  document.querySelector('#requestDetail').value = '架空の設置作業';
				  document.querySelector('#address').value = '愛知県名古屋市架空町';
				  document.querySelector('#desiredArrivalTime').value = '午後';
				}
				""", java.util.Map.of("start", start, "end", end, "requester", requester));
	}

	private ScheduleRequest savePublished(
			LocalDate workDate, String start, String end, String requester, WorkType workType) {
		return repository.saveAndFlush(ScheduleRequest.published(
				workDate, LocalTime.parse(start), LocalTime.parse(end), requester, workType));
	}

	private void waitForRequest(Page target, java.util.function.Predicate<ScheduleRequest> predicate) {
		target.waitForCondition(() -> repository.findAll().stream().anyMatch(predicate));
	}

	private void waitForText(Page target, String selector, String expected) {
		target.waitForCondition(() -> {
			String text = target.locator(selector).textContent();
			return text != null && text.contains(expected);
		});
	}

	private double scrollLeft(Page target) {
		return ((Number) target.locator(".schedule-scroll")
				.evaluate("element => element.scrollLeft")).doubleValue();
	}

	private String url(String path) {
		return "http://127.0.0.1:" + port + path;
	}
}
