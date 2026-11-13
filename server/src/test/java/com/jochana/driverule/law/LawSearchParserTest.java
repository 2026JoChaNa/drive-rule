package com.jochana.driverule.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Open API 목록 응답 해석 (결정 011 '조사').
 * 픽스처는 공식 안내의 칸 이름으로 만든 것이다 — 인증값을 받으면 실제 응답으로 바꾼다.
 * auth-failed.json 만 2026-11-13 실제 호출 응답이다.
 */
class LawSearchParserTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static String fixture(String name) {
		try (var in = LawSearchParserTest.class.getResourceAsStream("/law/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	void readsEveryVersionRow() {
		LawSearchParser.Page page = LawSearchParser.parse(JSON, fixture("eflaw-road-traffic.json"));

		assertThat(page.totalCount()).isEqualTo(4);
		assertThat(page.skippedRows()).isZero();
		assertThat(page.versions()).hasSize(4);
		LawVersion first = page.versions().getFirst();
		assertThat(first.serial()).isEqualTo("291133");
		assertThat(first.lawId()).isEqualTo("001638");
		assertThat(first.lawName()).isEqualTo("도로교통법");
		assertThat(first.lawKind()).isEqualTo("법률");
		assertThat(first.lawNo()).isEqualTo("제22067호");
		assertThat(first.promulgatedOn()).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(first.effectiveOn()).isEqualTo(LocalDate.of(2027, 4, 14));
		assertThat(first.revisionKind()).isEqualTo("일부개정");
	}

	@Test
	void singleResultComesAsObjectAndNumberIsZeroPadded() {
		LawSearchParser.Page page = LawSearchParser.parse(JSON, fixture("eflaw-single.json"));

		assertThat(page.versions()).singleElement().satisfies(v -> {
			assertThat(v.lawNo()).isEqualTo("제36728호");
			assertThat(v.lawName()).isEqualTo("도로교통법 시행령");
			assertThat(v.revisionKind()).isEqualTo("타법개정");
		});
	}

	@Test
	void noResultHasNoLawField() {
		LawSearchParser.Page page = LawSearchParser.parse(JSON,
				"{\"LawSearch\":{\"target\":\"eflaw\",\"totalCnt\":\"0\",\"page\":\"1\"}}");

		assertThat(page.totalCount()).isZero();
		assertThat(page.versions()).isEmpty();
	}

	@Test
	void numbersMayAlsoArriveAsJsonNumbers() {
		LawSearchParser.Page page = LawSearchParser.parse(JSON, """
				{"LawSearch":{"totalCnt":1,"law":[{"법령일련번호":290387,"법령명한글":"도로교통법 시행령",
				  "공포번호":36728,"공포일자":20260929,"시행일자":20261002}]}}
				""");

		assertThat(page.versions()).singleElement().satisfies(v -> {
			assertThat(v.serial()).isEqualTo("290387");
			assertThat(v.lawNo()).isEqualTo("제36728호");
			assertThat(v.promulgatedOn()).isEqualTo(LocalDate.of(2026, 9, 29));
			assertThat(v.revisionKind()).isNull();
		});
	}

	@Test
	void rowsMissingRequiredFieldsAreCountedNotFatal() {
		LawSearchParser.Page page = LawSearchParser.parse(JSON, """
				{"LawSearch":{"totalCnt":"3","law":[
				  {"법령일련번호":"1","법령명한글":"도로교통법","공포번호":"","공포일자":"20261013"},
				  {"법령일련번호":"2","법령명한글":"도로교통법","공포번호":"22067","공포일자":"2026-13-40"},
				  {"법령일련번호":"3","법령명한글":"도로교통법","공포번호":"22067","공포일자":"2026.10.13."}]}}
				""");

		assertThat(page.skippedRows()).isEqualTo(2);
		assertThat(page.versions()).singleElement()
				.satisfies(v -> assertThat(v.promulgatedOn()).isEqualTo(LocalDate.of(2026, 10, 13)));
	}

	@Test
	void authFailureIsReportedWithServerMessage() {
		assertThatThrownBy(() -> LawSearchParser.parse(JSON, fixture("auth-failed.json")))
				.isInstanceOf(LawApiException.class)
				.hasMessageContaining("사용자 정보 검증에 실패")
				.hasMessageContaining("IP주소");
	}

	@Test
	void htmlInsteadOfJsonIsReported() {
		assertThatThrownBy(() -> LawSearchParser.parse(JSON, "<html>\n<body>  인증 안내 </body></html>"))
				.isInstanceOf(LawApiException.class)
				.hasMessageContaining("JSON 이 아닌 응답")
				.hasMessageContaining("<html> <body> 인증 안내");
	}

	@Test
	void jsonWithoutLawSearchIsReported() {
		assertThatThrownBy(() -> LawSearchParser.parse(JSON, "{\"other\":1}"))
				.isInstanceOf(LawApiException.class)
				.hasMessageContaining("LawSearch 가 없는 응답");
	}

}
