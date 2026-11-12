package com.jochana.driverule.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.jochana.driverule.card.DecisionTable.Row;
import com.jochana.driverule.card.DecisionTable.SubTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionTableValidatorTest {

	static SubTable approach() {
		return new SubTable("approach", "교차로 진입 전", List.of("turn_light", "front_signal"), List.of(
				new Row(Map.of("turn_light", "yes"), "녹색 화살표일 때만", "t1", "right_turn_light"),
				new Row(Map.of("turn_light", "no", "front_signal", "red"), "정지선에서 멈춘 뒤", "t2", null),
				new Row(Map.of("turn_light", "no", "front_signal", "green"), "천천히", "t3", null)));
	}

	static SubTable crosswalk() {
		return new SubTable("crosswalk", "돌아선 뒤 횡단보도", List.of("ped_signal", "pedestrian"), List.of(
				new Row(Map.of("ped_signal", "green", "pedestrian", "yes"), "멈춥니다", "t4", null),
				new Row(Map.of("ped_signal", "green", "pedestrian", "no"), "천천히 지나갑니다", "t5", null),
				new Row(Map.of("ped_signal", "red"), "보행 신호 적색", "t6", null)));
	}

	/** 결정 009 B-1 의 우회전 표 — 소표 두 개, 소표마다 완전 · 겹침 없음. */
	static DecisionTable rightTurn() {
		return new DecisionTable(2, List.of(approach(), crosswalk()));
	}

	@Test
	void acceptsSubTablesThatAreEachCompleteAndNonOverlapping() {
		assertThat(DecisionTableValidator.validate(rightTurn())).isEmpty();
	}

	@Test
	void findsTheMissingRedPedestrianRowFromPlanV6() {
		// 기획 v6 2-4 표에는 보행 신호 적색 행이 없다 → 완전성 검사가 찾아낸다
		SubTable v6 = new SubTable("crosswalk", "돌아선 뒤 횡단보도", List.of("ped_signal", "pedestrian"),
				crosswalk().rows().subList(0, 2));

		assertThat(DecisionTableValidator.validate(new DecisionTable(2, List.of(approach(), v6))))
				.hasSize(2)
				.allSatisfy(e -> assertThat(e).contains("tables[1]: 어느 행에도 걸리지 않는 조합").contains("ped_signal=red"));
	}

	@Test
	void findsTheConflictBetweenRow1AndRow3FromPlanV6() {
		// v6 3행은 '우회전 신호등 없음'이 빠져 1행(신호등 있음)과 겹친다
		List<Row> rows = new ArrayList<>(approach().rows());
		rows.set(2, new Row(Map.of("front_signal", "green"), "천천히", "t3", null));
		SubTable v6 = new SubTable("approach", "교차로 진입 전", List.of("turn_light", "front_signal"), rows);

		assertThat(DecisionTableValidator.validate(new DecisionTable(2, List.of(v6))))
				.singleElement().asString()
				.contains("두 행 이상에 걸리는 조합").contains("turn_light=yes").contains("front_signal=green");
	}

	@Test
	void rejectsConditionsOutsideTheSharedListAndForeignWhenKeys() {
		SubTable t = new SubTable("x", "x", List.of("weather", "pedestrian"), List.of(
				new Row(Map.of("pedestrian", "maybe"), "a", "a", null),
				new Row(Map.of("turn_light", "yes"), "b", "b", null)));

		assertThat(DecisionTableValidator.validate(new DecisionTable(2, List.of(t))))
				.anySatisfy(e -> assertThat(e).contains("공통 조건 목록에 없는 조건").contains("weather"))
				.anySatisfy(e -> assertThat(e).contains("없는 선택지").contains("maybe"))
				.anySatisfy(e -> assertThat(e).contains("이 소표의 조건이 아닙니다").contains("turn_light"))
				// 형식 오류가 있으면 조합 검사는 건너뛴다
				.noneSatisfy(e -> assertThat(e).contains("걸리"));
	}

	@Test
	void enforcesLimitsAndKeys() {
		SubTable four = new SubTable("a", "a", List.of("turn_light", "front_signal", "ped_signal", "pedestrian"),
				List.of(new Row(Map.of(), "x", "x", null)));
		DecisionTable t = new DecisionTable(1, List.of(four, approach(), approach(), crosswalk()));

		assertThat(DecisionTableValidator.validate(t))
				.anySatisfy(e -> assertThat(e).startsWith("decision.schema"))
				.anySatisfy(e -> assertThat(e).contains("소표는 2개 이하"))
				.anySatisfy(e -> assertThat(e).contains("소표당 조건은 3개 이하"))
				.anySatisfy(e -> assertThat(e).contains("16가지"))
				.anySatisfy(e -> assertThat(e).contains("tables[2].key: 중복"));
	}

	@Test
	void rejectsBlankTexts() {
		SubTable t = new SubTable("a", " ", List.of("pedestrian"), List.of(new Row(Map.of(), " ", null, null)));

		assertThat(DecisionTableValidator.validate(new DecisionTable(2, List.of(t))))
				.contains("decision.tables[0].title: 비어 있습니다",
						"decision.tables[0].rows[0].text: 비어 있습니다",
						"decision.tables[0].rows[0].tts: 비어 있습니다");
	}

}
