package com.yuyadev.schedulesystem.holiday;

import java.io.IOException;
import java.util.List;

public interface HolidayDataSource {

	List<HolidayDefinition> fetch() throws IOException, InterruptedException;
}
