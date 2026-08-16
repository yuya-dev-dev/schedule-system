package com.yuyadev.schedulesystem.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

final class PlaywrightBrowserSession implements AutoCloseable {

	private final Playwright playwright;
	private final Browser browser;

	private PlaywrightBrowserSession(Playwright playwright, Browser browser) {
		this.playwright = playwright;
		this.browser = browser;
	}

	static PlaywrightBrowserSession launch() {
		Playwright playwright = Playwright.create();
		try {
			Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(true));
			return new PlaywrightBrowserSession(playwright, browser);
		} catch (RuntimeException exception) {
			playwright.close();
			throw exception;
		}
	}

	Browser browser() {
		return browser;
	}

	@Override
	public void close() {
		try {
			browser.close();
		} finally {
			playwright.close();
		}
	}
}
