package com.yuyadev.schedulesystem.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schedule.retention")
public record ScheduleDataRetentionProperties(boolean enabled) {
}
