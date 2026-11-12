package com.jochana.driverule.card;

import com.jochana.driverule.card.DecisionTable.Row;
import com.jochana.driverule.card.DecisionTable.SubTable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 결정 테이블 저장 전 검증 (결정 002 '4. 저장 전 검증' · 009 B). 소표마다:
 * <ul>
 * <li>형식: 키 중복 없음, 공통 조건 목록({@link RuleCondition})에 있는 조건 · 선택지만 참조</li>
 * <li>완전성: 모든 선택지 조합이 어떤 행에 걸림</li>
 * <li>겹침 없음: 어떤 조합도 두 행에 동시에 걸리지 않음</li>
 * </ul>
 * 오류 목록을 돌려준다 (비어 있으면 통과).
 */
public final class DecisionTableValidator {

	/**
	 * 소표 수 상한 = 한 번에 답하는 문장 수 상한. 소표마다 한 행으로 좁혀져도 답이 {@link RuleMatcher#MAX_ANSWERS} 를
	 * 넘지 않게 한다 — 이 값이 더 크면 모든 조건을 다 받아도 문장이 3개가 되어 되물을 것도 없이 상한을 넘는다.
	 */
	static final int MAX_TABLES = RuleMatcher.MAX_ANSWERS;
	static final int MAX_CONDITIONS_PER_TABLE = 3;
	static final int MAX_COMBINATIONS_PER_TABLE = 8;

	private DecisionTableValidator() {
	}

	public static List<String> validate(DecisionTable table) {
		List<String> errors = new ArrayList<>();
		if (table == null) {
			errors.add("decision: 결정 테이블이 없습니다");
			return errors;
		}
		if (table.schema() == null || table.schema() != DecisionTable.CURRENT_SCHEMA) {
			errors.add("decision.schema: " + DecisionTable.CURRENT_SCHEMA + " 이어야 합니다");
		}
		List<SubTable> tables = table.tables() == null ? List.of() : table.tables();
		if (tables.isEmpty()) {
			errors.add("decision.tables: 소표가 1개 이상 있어야 합니다");
		}
		if (tables.size() > MAX_TABLES) {
			errors.add("decision.tables: 소표는 " + MAX_TABLES + "개 이하여야 합니다");
		}
		Set<String> tableKeys = new HashSet<>();
		for (int i = 0; i < tables.size(); i++) {
			SubTable t = tables.get(i);
			String at = "decision.tables[" + i + "]";
			if (t == null) {
				errors.add(at + ": 비어 있습니다");
				continue;
			}
			if (isBlank(t.key())) {
				errors.add(at + ".key: 비어 있습니다");
			}
			else if (!tableKeys.add(t.key())) {
				errors.add(at + ".key: 중복입니다 (" + t.key() + ")");
			}
			if (isBlank(t.title())) {
				errors.add(at + ".title: 비어 있습니다");
			}
			validateSubTable(t, at, errors);
		}
		return errors;
	}

	private static void validateSubTable(SubTable t, String at, List<String> errors) {
		int before = errors.size();
		Map<String, RuleCondition> conditions = resolveConditions(t.conditions(), at, errors);
		List<Row> rows = t.rows() == null ? List.of() : t.rows();
		if (rows.isEmpty()) {
			errors.add(at + ".rows: 행이 1개 이상 있어야 합니다");
		}
		for (int i = 0; i < rows.size(); i++) {
			validateRow(rows.get(i), at + ".rows[" + i + "]", conditions, errors);
		}
		// 형식이 맞을 때만 조합 검사를 한다 — 형식 오류 위에 조합 오류를 쌓으면 원인이 흐려진다
		if (errors.size() == before) {
			checkCoverage(conditions, rows, at, errors);
		}
	}

	private static Map<String, RuleCondition> resolveConditions(List<String> keys, String at, List<String> errors) {
		Map<String, RuleCondition> result = new LinkedHashMap<>();
		if (keys == null || keys.isEmpty()) {
			errors.add(at + ".conditions: 조건이 1개 이상 있어야 합니다");
			return result;
		}
		if (keys.size() > MAX_CONDITIONS_PER_TABLE) {
			errors.add(at + ".conditions: 소표당 조건은 " + MAX_CONDITIONS_PER_TABLE + "개 이하여야 합니다");
		}
		int combinations = 1;
		for (String key : keys) {
			Optional<RuleCondition> condition = RuleCondition.byKey(key);
			if (condition.isEmpty()) {
				errors.add(at + ".conditions: 공통 조건 목록에 없는 조건입니다 (" + key + ")");
			}
			else if (result.putIfAbsent(key, condition.get()) != null) {
				errors.add(at + ".conditions: 중복입니다 (" + key + ")");
			}
			else {
				combinations *= condition.get().options().size();
			}
		}
		if (combinations > MAX_COMBINATIONS_PER_TABLE) {
			errors.add(at + ".conditions: 선택지 조합이 " + combinations + "가지입니다 — "
					+ MAX_COMBINATIONS_PER_TABLE + "가지 이하여야 합니다 (검수 부담 상한)");
		}
		return result;
	}

	private static void validateRow(Row row, String at, Map<String, RuleCondition> conditions, List<String> errors) {
		if (row == null) {
			errors.add(at + ": 비어 있습니다");
			return;
		}
		if (isBlank(row.text())) {
			errors.add(at + ".text: 비어 있습니다");
		}
		if (isBlank(row.tts())) {
			errors.add(at + ".tts: 비어 있습니다");
		}
		Map<String, String> when = row.when() == null ? Map.of() : row.when();
		for (Map.Entry<String, String> e : when.entrySet()) {
			RuleCondition condition = conditions.get(e.getKey());
			if (condition == null) {
				errors.add(at + ".when: 이 소표의 조건이 아닙니다 (" + e.getKey() + ")");
			}
			else if (!condition.hasOption(e.getValue())) {
				errors.add(at + ".when: 조건 " + e.getKey() + " 에 없는 선택지입니다 (" + e.getValue() + ")");
			}
		}
	}

	/** 소표의 모든 조합을 펼쳐 조합마다 걸리는 행이 정확히 1개인지 본다 (조합 최대 8가지라 전수 검사). */
	private static void checkCoverage(Map<String, RuleCondition> conditions, List<Row> rows, String at,
			List<String> errors) {
		List<Map<String, String>> combos = new ArrayList<>();
		combos.add(new LinkedHashMap<>());
		for (RuleCondition c : conditions.values()) {
			List<Map<String, String>> next = new ArrayList<>();
			for (Map<String, String> partial : combos) {
				for (String option : c.optionKeys()) {
					Map<String, String> extended = new LinkedHashMap<>(partial);
					extended.put(c.key(), option);
					next.add(extended);
				}
			}
			combos = next;
		}
		for (Map<String, String> combo : combos) {
			List<Integer> hits = new ArrayList<>();
			for (int i = 0; i < rows.size(); i++) {
				if (matchesFully(rows.get(i), combo)) {
					hits.add(i);
				}
			}
			if (hits.isEmpty()) {
				errors.add(at + ": 어느 행에도 걸리지 않는 조합이 있습니다 " + combo);
			}
			else if (hits.size() > 1) {
				errors.add(at + ": 두 행 이상에 걸리는 조합이 있습니다 " + combo + " → rows " + hits);
			}
		}
	}

	private static boolean matchesFully(Row row, Map<String, String> combo) {
		Map<String, String> when = row.when() == null ? Map.of() : row.when();
		return when.entrySet().stream().allMatch(e -> e.getValue().equals(combo.get(e.getKey())));
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

}
