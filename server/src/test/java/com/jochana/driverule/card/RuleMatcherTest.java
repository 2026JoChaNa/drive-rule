package com.jochana.driverule.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jochana.driverule.card.DecisionTable.Row;
import com.jochana.driverule.card.DecisionTable.SubTable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 공용 테스트 케이스(RuleMatcherSpecTest) 밖의, 서버 구현만의 안전장치. */
class RuleMatcherTest {

	@Test
	void refusesTableThatTheValidatorWouldReject() {
		// 소표 3개가 각각 한 행으로 좁혀지면 되물을 조건 없이 문장 3개 — 검증기가 소표 수를 2개로 막는 이유
		SubTable a = new SubTable("a", "a", List.of("turn_light"), List.of(
				new Row(Map.of("turn_light", "yes"), "a.yes", "a.yes", null),
				new Row(Map.of("turn_light", "no"), "a.no", "a.no", null)));
		SubTable b = new SubTable("b", "b", List.of("front_signal"), List.of(
				new Row(Map.of("front_signal", "red"), "b.red", "b.red", null),
				new Row(Map.of("front_signal", "green"), "b.green", "b.green", null)));
		SubTable c = new SubTable("c", "c", List.of("pedestrian"), List.of(
				new Row(Map.of("pedestrian", "yes"), "c.yes", "c.yes", null),
				new Row(Map.of("pedestrian", "no"), "c.no", "c.no", null)));
		DecisionTable three = new DecisionTable(2, List.of(a, b, c));

		assertThat(DecisionTableValidator.validate(three)).contains("decision.tables: 소표는 2개 이하여야 합니다");
		assertThatThrownBy(() -> RuleMatcher.match(three,
				Map.of("turn_light", "yes", "front_signal", "red", "pedestrian", "no")))
				.isInstanceOf(IllegalStateException.class);
	}

}
