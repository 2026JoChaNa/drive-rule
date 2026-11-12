package com.jochana.driverule.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.jochana.driverule.card.DecisionTable.Row;
import com.jochana.driverule.card.RuleMatcher.Result;
import com.jochana.driverule.card.RuleMatcher.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공용 테스트 케이스({@code spec/rule-matching/cases.json})를 서버 참조 구현으로 돌린다.
 * 차량 앱도 같은 파일을 돌린다 — 둘 중 하나라도 실패하면 Gleo 답이 통신 상태에 따라 달라질 수 있다 (결정 009 F).
 */
class RuleMatcherSpecTest {

	/** Gradle 테스트의 작업 폴더는 server/ 다. */
	static final Path CASES = Path.of("..", "spec", "rule-matching", "cases.json");

	record Spec(Map<String, DecisionTable> decisions, List<Case> cases) {
	}

	record Case(String name, String decision, Map<String, String> given, Expect expect) {
	}

	record Expect(Status status, List<String> texts, String ask) {
	}

	@TestFactory
	Stream<DynamicTest> sharedCases() throws Exception {
		JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
		Spec spec = mapper.readValue(Files.readString(CASES), Spec.class);

		Stream<DynamicTest> tablesAreValid = spec.decisions().entrySet().stream()
				.map(e -> DynamicTest.dynamicTest("결정 테이블 '" + e.getKey() + "' 는 검증기를 통과한다",
						() -> assertThat(DecisionTableValidator.validate(e.getValue())).isEmpty()));

		Stream<DynamicTest> cases = spec.cases().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
			Result result = RuleMatcher.match(spec.decisions().get(c.decision()), c.given());

			assertThat(result.status()).isEqualTo(c.expect().status());
			if (c.expect().status() == Status.ANSWER) {
				assertThat(result.answers()).extracting(Row::text).containsExactlyElementsOf(c.expect().texts());
			}
			else {
				assertThat(result.ask()).isEqualTo(c.expect().ask());
			}
		}));
		return Stream.concat(tablesAreValid, cases);
	}

}
