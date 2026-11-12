package com.jochana.driverule.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 서비스는 시각을 이 Clock 으로만 읽는다 — 테스트에서 고정 시각으로 바꿀 수 있게. */
@Configuration
public class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

}
