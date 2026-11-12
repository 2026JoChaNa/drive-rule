package com.jochana.driverule.law;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 감시 법령의 최근 공포분을 받아 없는 개정만 law_revision(PENDING)으로 넣는다 (결정 011).
 * 카드는 만들지 않는다 — 분류(기능 ⑦)가 먼저다.
 */
@Service
public class LawRevisionCollector {

	private static final Logger log = LoggerFactory.getLogger(LawRevisionCollector.class);

	/** source_key 접두어 — 수집기가 넣은 행 */
	public static final String SOURCE_PREFIX = "LAWGO-";

	public enum Outcome {
		OK, FAILED
	}

	/**
	 * @param lawName  응답에서 본 법령명 (실패했거나 결과가 없으면 null)
	 * @param created  새로 넣은 공포 번호
	 * @param existing 이미 있어 건너뛴 개정 수
	 * @param error    실패 사유 (OK 면 null)
	 */
	public record LawResult(String lawId, Outcome outcome, String lawName, List<String> created, int existing,
			String error) {
	}

	public record SyncReport(LocalDate from, LocalDate to, List<LawResult> laws) {

		public int createdCount() {
			return laws.stream().mapToInt(l -> l.created().size()).sum();
		}

		public boolean anyFailed() {
			return laws.stream().anyMatch(l -> l.outcome() == Outcome.FAILED);
		}
	}

	private final LawVersionSource source;
	private final LawRevisionRepository repository;
	private final LawSyncProperties props;
	private final Clock clock;

	public LawRevisionCollector(LawVersionSource source, LawRevisionRepository repository, LawSyncProperties props,
			Clock clock) {
		this.source = source;
		this.repository = repository;
		this.props = props;
		this.clock = clock;
	}

	/** 감시 법령마다 한 번씩 묻는다. 한 법령이 실패해도 나머지는 계속한다. */
	public SyncReport sync() {
		LocalDate to = LocalDate.now(clock);
		LocalDate from = to.minusDays(Math.max(props.lookbackDays(), 0));
		if (props.since() != null && props.since().isAfter(from)) {
			from = props.since();
		}
		List<LawResult> results = new ArrayList<>();
		for (String lawId : props.lawIds()) {
			LawResult result;
			if (from.isAfter(to)) {
				result = new LawResult(lawId, Outcome.OK, null, List.of(), 0, null);
			}
			else {
				try {
					result = store(lawId, source.versions(lawId, from, to), from, to);
				}
				catch (LawApiException e) {
					result = new LawResult(lawId, Outcome.FAILED, null, List.of(), 0, e.getMessage());
				}
			}
			log.info("법령 개정 수집 {} {} — 새로 {}건 {} · 이미 있음 {}건{}", lawId, result.outcome(),
					result.created().size(), result.created(), result.existing(),
					result.error() == null ? "" : " · " + result.error());
			results.add(result);
		}
		return new SyncReport(from, to, List.copyOf(results));
	}

	private LawResult store(String lawId, List<LawVersion> versions, LocalDate from, LocalDate to) {
		// 같은 공포 = 같은 공포번호. 시행일이 나뉜 여러 판 중 시행일이 가장 이른 판을 대표로
		Map<String, LawVersion> byLawNo = new LinkedHashMap<>();
		Comparator<LawVersion> earlier = Comparator.comparing(LawVersion::effectiveOn,
				Comparator.nullsLast(Comparator.naturalOrder()));
		String lawName = null;
		for (LawVersion v : versions) {
			if (lawName == null) {
				lawName = v.lawName();
			}
			if (v.promulgatedOn().isBefore(from) || v.promulgatedOn().isAfter(to)) {
				continue;
			}
			byLawNo.merge(v.lawNo(), v, (a, b) -> earlier.compare(b, a) < 0 ? b : a);
		}
		List<String> created = new ArrayList<>();
		int existing = 0;
		LocalDateTime now = LocalDateTime.now(clock);
		for (LawVersion v : byLawNo.values()) {
			if (repository.existsByLawNo(v.lawNo())) {
				existing++;
				continue;
			}
			try {
				repository.saveAndFlush(new LawRevision(revisionName(v), v.lawNo(), SOURCE_PREFIX + v.serial(),
						v.promulgatedOn(), now));
				created.add(v.lawNo());
			}
			catch (DataIntegrityViolationException e) {
				existing++; // 동시에 돈 다른 실행이 먼저 넣었다
			}
		}
		return new LawResult(lawId, Outcome.OK, lawName, List.copyOf(created), existing, null);
	}

	/** 도로교통법 시행령 타법개정 — 제개정구분이 없으면 법령명만 */
	static String revisionName(LawVersion v) {
		String name = v.revisionKind() == null ? v.lawName() : v.lawName() + " " + v.revisionKind();
		return name.length() > 100 ? name.substring(0, 100) : name;
	}

}
