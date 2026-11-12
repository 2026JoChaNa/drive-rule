package com.jochana.driverule.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

/** 수집 규칙 (결정 011 2~4번) — 범위 · 같은 공포 묶기 · 중복 건너뛰기 · 법령별 실패. */
class LawRevisionCollectorTest {

	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 6, 30);
	static final Clock CLOCK = Clock.fixed(ZonedDateTime.of(NOW, SEOUL).toInstant(), SEOUL);

	LawRevisionRepository repository;
	Map<String, List<LawVersion>> responses;
	Map<String, LawApiException> failures;
	List<String> asked;
	LawVersionSource source;

	@BeforeEach
	void setUp() {
		repository = mock(LawRevisionRepository.class);
		when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		responses = new HashMap<>();
		failures = new HashMap<>();
		asked = new ArrayList<>();
		source = (lawId, from, to) -> {
			asked.add(lawId + " " + from + "~" + to);
			if (failures.containsKey(lawId)) {
				throw failures.get(lawId);
			}
			return responses.getOrDefault(lawId, List.of());
		};
	}

	LawRevisionCollector collector(int lookbackDays, LocalDate since, String... lawIds) {
		return new LawRevisionCollector(source, repository,
				new LawSyncProperties("oc", List.of(lawIds), lookbackDays, since), CLOCK);
	}

	static List<LawVersion> roadTrafficFixture() {
		return LawSearchParser.parse(JsonMapper.builder().build(),
				LawSearchParserTest.fixture("eflaw-road-traffic.json")).versions();
	}

	@Test
	void storesNewRevisionsAsPendingAndGroupsVersionsOfOnePromulgation() {
		responses.put("001638", roadTrafficFixture());

		LawRevisionCollector.SyncReport report = collector(90, LocalDate.of(2026, 10, 1), "001638").sync();

		ArgumentCaptor<LawRevision> saved = ArgumentCaptor.forClass(LawRevision.class);
		verify(repository, org.mockito.Mockito.times(1)).saveAndFlush(saved.capture());
		LawRevision r = saved.getValue();
		// 22067 은 시행일별 두 판 → 1건. 시행일이 이른 판(291100)을 대표로
		assertThat(r.getLawNo()).isEqualTo("제22067호");
		assertThat(r.getSourceKey()).isEqualTo("LAWGO-291100");
		assertThat(r.getLawName()).isEqualTo("도로교통법 일부개정");
		assertThat(r.getPromulgatedOn()).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(r.getTriageStatus()).isEqualTo(TriageStatus.PENDING);
		assertThat(r.getCreatedAt()).isEqualTo(NOW);
		// 21990(2026-09-29) · 20864(2025-04-01)는 SINCE(2026-10-01) 이전이라 보지 않는다
		assertThat(report.from()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(report.to()).isEqualTo(LocalDate.of(2026, 11, 13));
		assertThat(report.laws()).singleElement().satisfies(l -> {
			assertThat(l.outcome()).isEqualTo(LawRevisionCollector.Outcome.OK);
			assertThat(l.lawName()).isEqualTo("도로교통법");
			assertThat(l.created()).containsExactly("제22067호");
			assertThat(l.existing()).isZero();
		});
		assertThat(asked).containsExactly("001638 2026-10-01~2026-11-13");
	}

	@Test
	void lookbackWindowWinsWhenItIsLaterThanSince() {
		responses.put("001638", roadTrafficFixture());

		LawRevisionCollector.SyncReport report = collector(30, LocalDate.of(2020, 1, 1), "001638").sync();

		assertThat(report.from()).isEqualTo(LocalDate.of(2026, 10, 14));
		assertThat(report.createdCount()).isZero(); // 22067 은 10-13 공포 — 창 밖
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void existingLawNoIsSkipped_includingManualRows() {
		responses.put("001638", roadTrafficFixture());
		when(repository.existsByLawNo("제22067호")).thenReturn(true); // MANUAL-22067 로 손 등록된 행

		LawRevisionCollector.SyncReport report = collector(90, LocalDate.of(2026, 9, 1), "001638").sync();

		ArgumentCaptor<LawRevision> saved = ArgumentCaptor.forClass(LawRevision.class);
		verify(repository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getLawNo()).isEqualTo("제21990호");
		assertThat(saved.getValue().getLawName()).isEqualTo("도로교통법 타법개정");
		assertThat(report.laws().getFirst().created()).containsExactly("제21990호");
		assertThat(report.laws().getFirst().existing()).isEqualTo(1);
	}

	@Test
	void uniqueViolationFromConcurrentRunCountsAsExisting() {
		responses.put("001638", roadTrafficFixture());
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_law_revision_law_no"));

		LawRevisionCollector.SyncReport report = collector(90, LocalDate.of(2026, 10, 1), "001638").sync();

		assertThat(report.laws().getFirst().outcome()).isEqualTo(LawRevisionCollector.Outcome.OK);
		assertThat(report.laws().getFirst().created()).isEmpty();
		assertThat(report.laws().getFirst().existing()).isEqualTo(1);
	}

	@Test
	void oneFailingLawDoesNotStopOthers() {
		failures.put("001638", new LawApiException("Open API 거절: 사용자 정보 검증에 실패하였습니다."));
		responses.put("003395", LawSearchParser.parse(JsonMapper.builder().build(),
				LawSearchParserTest.fixture("eflaw-single.json")).versions());

		LawRevisionCollector.SyncReport report = collector(90, LocalDate.of(2026, 9, 1), "001638", "003395").sync();

		assertThat(report.anyFailed()).isTrue();
		assertThat(report.laws()).hasSize(2);
		assertThat(report.laws().get(0).outcome()).isEqualTo(LawRevisionCollector.Outcome.FAILED);
		assertThat(report.laws().get(0).error()).contains("사용자 정보 검증");
		assertThat(report.laws().get(1).outcome()).isEqualTo(LawRevisionCollector.Outcome.OK);
		assertThat(report.laws().get(1).created()).containsExactly("제36728호");
		assertThat(report.createdCount()).isEqualTo(1);
	}

	@Test
	void sinceInTheFutureAsksNothing() {
		LawRevisionCollector.SyncReport report = collector(90, LocalDate.of(2027, 1, 1), "001638").sync();

		assertThat(asked).isEmpty();
		assertThat(report.laws().getFirst().outcome()).isEqualTo(LawRevisionCollector.Outcome.OK);
	}

	@Test
	void revisionNameFallsBackToLawNameAndFitsColumn() {
		LawVersion noKind = new LawVersion("1", "001638", "도로교통법", "법률", 1, LocalDate.of(2026, 10, 1), null, null);
		LawVersion longName = new LawVersion("1", "x", "가".repeat(120), "법률", 1, LocalDate.of(2026, 10, 1), null,
				"일부개정");

		assertThat(LawRevisionCollector.revisionName(noKind)).isEqualTo("도로교통법");
		assertThat(LawRevisionCollector.revisionName(longName)).hasSize(100);
	}

}
