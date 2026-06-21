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
class ScheduleBrowserE2ETest {

	private static final LocalDate FUTURE_WORK_DATE = LocalDate.of(2026, 6, 24);
	private static final LocalDate PAST_WORK_DATE = LocalDate.of(2026, 6, 19);

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
	void createsRequestFromBlankCellAndReopensIt() {
		page.navigate(url("/schedule?month=2026-06"));
		page.locator("a[href='/requests/new?date=2026-06-24']").first().click();

		fillCompleteRequest(page, "10:00", "11:30", "社員A");
		waitForText(page, "#save-status", "保存済み・一覧に反映中");
		page.locator(".back-submit").click();
		page.waitForURL("**/schedule?month=2026-06");

		Locator occupied = page.locator("a[aria-label='案件を開く']").filter(
				new Locator.FilterOptions().setHasText("社員A")).first();
		assertThat(occupied.textContent()).contains("社員A", "設置");
		occupied.click();
		assertThat(page.locator("#requesterName").inputValue()).isEqualTo("社員A");
		assertThat(page.locator("#requestDetail").inputValue()).isEqualTo("架空の設置作業");
	}

	@Test
	void keepsConflictingInputAsDraftAndReturnsToSchedule() {
		savePublished(FUTURE_WORK_DATE, "10:00", "12:00", "社員A", WorkType.INSTALL);
		page.navigate(url("/requests/new?date=2026-06-24"));

		page.locator("#startTime").selectOption("11:00");
		page.locator("#endTime").selectOption("13:00");
		page.locator("#workType").selectOption("DELIVERY");
		page.locator("#requesterName").fill("社員B");
		page.locator("#requesterName").blur();
		waitForText(page, "#error-summary", "その時間はすでに埋まっています");

		assertThat(page.locator("#requesterName").inputValue()).isEqualTo("社員B");
		assertThat(page.locator("#draft-delete-form").isVisible()).isTrue();
		page.locator(".back-submit").click();
		page.waitForURL("**/schedule?month=2026-06");

		assertThat(page.locator(".draft-item").textContent()).contains("社員B", "時間重複");
		assertThat(repository.countByEntryState(EntryState.PUBLISHED)).isOne();
		assertThat(repository.countByEntryState(EntryState.DRAFT)).isOne();
	}

	@Test
	void rejectsStaleEditAndReloadsLatestContent() {
		ScheduleRequest request = savePublished(
				FUTURE_WORK_DATE, "13:00", "14:00", "社員A", WorkType.INSTALL);
		page.navigate(url("/requests/" + request.getId()));

		try (BrowserContext secondContext = browser.newContext()) {
			Page secondPage = secondContext.newPage();
			secondPage.navigate(url("/requests/" + request.getId()));

			page.locator("#requesterName").fill("社員B");
			page.locator("#requesterName").blur();
			waitForText(page, "#save-status", "保存済み・一覧に反映中");

			secondPage.locator("#requesterName").fill("社員C");
			secondPage.locator("#requesterName").blur();
			waitForText(secondPage, "#save-status", "ほかの利用者が先に変更しました");
			assertThat(secondPage.locator("#retry-save").textContent()).isEqualTo("最新内容を読み込む");

			secondPage.locator("#retry-save").click();
			secondPage.waitForLoadState();
			assertThat(secondPage.locator("#requesterName").inputValue()).isEqualTo("社員B");
		}

		assertThat(repository.findById(request.getId()).orElseThrow().getRequesterName())
				.isEqualTo("社員B");
	}

	@Test
	void refusesCancellationWhenAnotherPageChangedTheRequest() {
		ScheduleRequest request = savePublished(
				FUTURE_WORK_DATE, "14:00", "15:00", "社員A", WorkType.COLLECT);
		page.navigate(url("/requests/" + request.getId() + "/cancel"));

		try (BrowserContext secondContext = browser.newContext()) {
			Page secondPage = secondContext.newPage();
			secondPage.navigate(url("/requests/" + request.getId()));
			secondPage.locator("#requesterName").fill("社員B");
			secondPage.locator("#requesterName").blur();
			waitForText(secondPage, "#save-status", "保存済み・一覧に反映中");

			page.getByText("キャンセルを実行", new Page.GetByTextOptions().setExact(true)).click();
			waitForText(page, "[role='alert']", "内容が変更されました");
		}

		ScheduleRequest retained = repository.findById(request.getId()).orElseThrow();
		assertThat(retained.getRequesterName()).isEqualTo("社員B");
	}

	@Test
	void opensPastRequestAsReadOnly() {
		ScheduleRequest past = savePublished(
				PAST_WORK_DATE, "10:00", "11:00", "社員A", WorkType.INSTALL);
		page.navigate(url("/schedule?month=2026-06"));
		page.locator("a[href='/requests/" + past.getId() + "']").first().click();

		waitForText(page, ".readonly-notice", "閲覧専用");
		assertThat(page.locator("#requesterName").isDisabled()).isTrue();
		assertThat(page.locator("#destructive-actions").isHidden()).isTrue();
		assertThat(page.locator("#save-status").textContent()).isEqualTo("閲覧専用");
		page.locator(".back-submit").click();
		page.waitForURL("**/schedule?month=2026-06");
	}

	@Test
	void retainsInputAndRetriesAfterOneAutosaveFailure() {
		page.navigate(url("/requests/new?date=2026-06-24"));
		AtomicBoolean failNextAutosave = new AtomicBoolean(true);
		page.route("**/requests/autosave", route -> handleOneFailure(route, failNextAutosave));
		page.evaluate("""
				() => {
				  document.querySelector('#startTime').value = '15:00';
				  document.querySelector('#endTime').value = '16:00';
				  document.querySelector('#workType').value = 'INSTALL';
				  document.querySelector('#requesterName').value = '社員A';
				  document.querySelector('#requestDetail').value = '架空の設置作業';
				  document.querySelector('#address').value = '愛知県名古屋市架空町';
				  document.querySelector('#desiredArrivalTime').value = '午後';
				}
				""");

		page.locator("#requesterName").focus();
		page.locator("#requestDetail").focus();
		waitForText(page, "#error-summary", "通信エラーのため保存できませんでした");
		assertThat(page.locator("#requesterName").inputValue()).isEqualTo("社員A");
		assertThat(repository.count()).isZero();

		page.locator("#retry-save").click();
		waitForText(page, "#save-status", "保存済み・一覧に反映中");
		assertThat(repository.count()).isOne();
		assertThat(repository.findAll().getFirst().getRequesterName()).isEqualTo("社員A");
	}

	private void fillCompleteRequest(Page target, String start, String end, String requester) {
		target.locator("#startTime").selectOption(start);
		target.locator("#endTime").selectOption(end);
		target.locator("#workType").selectOption("INSTALL");
		target.locator("#requesterName").fill(requester);
		target.locator("#requestDetail").fill("架空の設置作業");
		target.locator("#address").fill("愛知県名古屋市架空町");
		target.locator("#desiredArrivalTime").fill("午後");
		target.locator("#desiredArrivalTime").blur();
	}

	private ScheduleRequest savePublished(
			LocalDate workDate, String start, String end, String requester, WorkType workType) {
		return repository.saveAndFlush(ScheduleRequest.published(
				workDate, LocalTime.parse(start), LocalTime.parse(end), requester, workType));
	}

	private void handleOneFailure(Route route, AtomicBoolean failNextAutosave) {
		if (failNextAutosave.compareAndSet(true, false)) {
			route.abort();
			return;
		}
		route.resume();
	}

	private void waitForText(Page target, String selector, String expected) {
		target.waitForCondition(() -> {
			String text = target.locator(selector).textContent();
			return text != null && text.contains(expected);
		});
	}

	private String url(String path) {
		return "http://127.0.0.1:" + port + path;
	}
}
