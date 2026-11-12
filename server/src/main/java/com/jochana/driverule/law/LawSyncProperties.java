package com.jochana.driverule.law;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 법령 개정 자동 수집 설정 (결정 011). 값은 application.yml → 환경변수.
 *
 * @param oc           국가법령정보 Open API 인증값. 비어 있으면 수집하지 않는다
 * @param lawIds       감시할 법령ID (도로교통법 001638 · 시행령 003395 · 시행규칙 007079)
 * @param lookbackDays 오늘부터 며칠 전 공포분까지 다시 볼지
 * @param since        이보다 이른 공포분은 보지 않는다 (처음 켤 때 연혁이 한꺼번에 들어오지 않게)
 */
@ConfigurationProperties("driverule.law-sync")
public record LawSyncProperties(String oc, List<String> lawIds, int lookbackDays, LocalDate since) {

	public LawSyncProperties {
		oc = oc == null ? "" : oc.trim();
		lawIds = lawIds == null ? List.of()
				: lawIds.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	public boolean configured() {
		return !oc.isEmpty();
	}

}
