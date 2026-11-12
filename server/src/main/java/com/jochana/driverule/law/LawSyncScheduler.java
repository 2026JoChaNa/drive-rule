package com.jochana.driverule.law;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 하루 한 번 수집 (결정 011). 인증값이 없으면 아무것도 하지 않는다. */
@Component
class LawSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(LawSyncScheduler.class);

	private final LawRevisionCollector collector;
	private final LawSyncProperties props;

	LawSyncScheduler(LawRevisionCollector collector, LawSyncProperties props) {
		this.collector = collector;
		this.props = props;
	}

	@Scheduled(cron = "${driverule.law-sync.cron}", zone = "Asia/Seoul")
	void run() {
		if (!props.configured()) {
			log.debug("LAW_API_OC 가 비어 있어 법령 개정 수집을 건너뜀");
			return;
		}
		try {
			LawRevisionCollector.SyncReport report = collector.sync();
			if (report.anyFailed()) {
				log.warn("법령 개정 수집: 실패한 법령이 있음 {}", report.laws());
			}
		}
		catch (RuntimeException e) {
			log.error("법령 개정 수집 중 예외", e);
		}
	}

}
