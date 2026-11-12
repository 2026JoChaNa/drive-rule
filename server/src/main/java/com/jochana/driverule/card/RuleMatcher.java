package com.jochana.driverule.card;

import com.jochana.driverule.card.DecisionTable.Row;
import com.jochana.driverule.card.DecisionTable.SubTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 조건이 일부만 주어졌을 때의 답 고르기 — 결정 009 B-3 의 <b>참조 구현</b>.
 * <p>
 * Gleo 답변은 앱이 차량 사본으로 한다(009 F). 이 클래스는 같은 규칙을 서버에서 구현해,
 * 저장소의 공용 테스트 케이스({@code spec/rule-matching/cases.json})로 앱 구현과 결과를 맞추는 데 쓴다.
 * 규칙을 바꾸면 명세({@code spec/rule-matching/README.md}) · 테스트 케이스 · 앱 구현을 함께 바꾼다.
 *
 * <ol>
 * <li>받은 조건이 하나도 걸리지 않는 소표는 건너뛴다</li>
 * <li>남은 소표에서 받은 값과 어긋나지 않는 행을 모두 후보로 모은다 (같은 문장은 하나로)</li>
 * <li>후보 ≤ {@value #MAX_ANSWERS} → ANSWER</li>
 * <li>후보가 그보다 많거나 걸린 소표가 없음 → ASK. 되물을 조건 = 소표 순서 · 조건 순서대로, 아직 받지 않았고
 * 후보 행이 실제로 갈리는 첫 조건</li>
 * </ol>
 */
public final class RuleMatcher {

	/** 음성 안내 5초 이내(기획 v6 1-1)를 고려한 한 번에 답할 문장 수 상한 [추론] */
	public static final int MAX_ANSWERS = 2;

	private RuleMatcher() {
	}

	public enum Status {
		ANSWER, ASK
	}

	/**
	 * @param answers 답할 행 (ANSWER 일 때)
	 * @param ask     되물을 조건 키 (ASK 일 때)
	 */
	public record Result(Status status, List<Row> answers, String ask) {

		static Result answer(List<Row> rows) {
			return new Result(Status.ANSWER, rows, null);
		}

		static Result ask(String condition) {
			return new Result(Status.ASK, List.of(), condition);
		}
	}

	public static Result match(DecisionTable table, Map<String, String> given) {
		List<SubTable> considered = table.tables().stream()
				.filter(t -> t.conditions().stream().anyMatch(given::containsKey))
				.toList();
		if (considered.isEmpty()) {
			return Result.ask(table.tables().get(0).conditions().get(0));
		}

		Map<String, Row> byText = new LinkedHashMap<>();
		Map<SubTable, List<Row>> candidatesByTable = new LinkedHashMap<>();
		for (SubTable t : considered) {
			List<Row> candidates = t.rows().stream().filter(r -> consistent(r, given)).toList();
			candidatesByTable.put(t, candidates);
			candidates.forEach(r -> byText.putIfAbsent(r.text(), r));
		}
		if (byText.size() <= MAX_ANSWERS) {
			return Result.answer(new ArrayList<>(byText.values()));
		}
		for (Map.Entry<SubTable, List<Row>> e : candidatesByTable.entrySet()) {
			for (String condition : e.getKey().conditions()) {
				if (!given.containsKey(condition) && splits(e.getValue(), condition)) {
					return Result.ask(condition);
				}
			}
		}
		// 검증기를 통과한 표에서는 오지 않는다: 후보가 상한보다 많으면 (소표 수 ≤ 상한이므로) 어떤 소표에 후보가 둘 이상이고,
		// UNIQUE 소표에서 받은 값과 어긋나지 않는 두 행은 아직 받지 않은 조건 하나에서 반드시 갈린다 (안 갈리면 두 행이 겹친다).
		// 명세와 다른 답을 조용히 내지 않도록 여기서 멈춘다.
		throw new IllegalStateException("검증되지 않은 결정 테이블입니다 — 갈리는 조건 없이 후보 " + byText.size() + "개");
	}

	/** 행의 조건 중 받은 값과 어긋나는 것이 없으면 후보다 (받지 않은 조건은 '모름'). */
	private static boolean consistent(Row row, Map<String, String> given) {
		Map<String, String> when = row.when() == null ? Map.of() : row.when();
		return when.entrySet().stream()
				.allMatch(e -> !given.containsKey(e.getKey()) || given.get(e.getKey()).equals(e.getValue()));
	}

	/** 후보 행들이 이 조건 값에 따라 다른 행으로 갈리면 되물을 가치가 있다. */
	private static boolean splits(List<Row> rows, String condition) {
		return rows.stream()
				.map(r -> r.when() == null ? null : r.when().get(condition))
				.distinct()
				.count() > 1;
	}

}
