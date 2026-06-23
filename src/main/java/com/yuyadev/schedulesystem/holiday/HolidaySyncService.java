package com.yuyadev.schedulesystem.holiday;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HolidaySyncService {

	private static final Logger logger = LoggerFactory.getLogger(HolidaySyncService.class);

	private final HolidayDataSource dataSource;
	private final HolidayCalendarService calendarService;

	public HolidaySyncService(
			HolidayDataSource dataSource, HolidayCalendarService calendarService) {
		this.dataSource = dataSource;
		this.calendarService = calendarService;
	}

	public SyncResult syncIfStale() {
		if (calendarService.cacheIsFresh()) {
			return SyncResult.FRESH_CACHE;
		}
		try {
			calendarService.replaceAll(dataSource.fetch());
			return SyncResult.UPDATED;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			logger.warn("祝日データの同期が中断されました。保存済みキャッシュを使用します");
			return SyncResult.FAILED_USING_CACHE;
		} catch (IOException | RuntimeException exception) {
			logger.warn("祝日データを同期できません。保存済みキャッシュを使用します: {}",
					exception.getMessage());
			return SyncResult.FAILED_USING_CACHE;
		}
	}

	public enum SyncResult {
		UPDATED,
		FRESH_CACHE,
		FAILED_USING_CACHE
	}
}
