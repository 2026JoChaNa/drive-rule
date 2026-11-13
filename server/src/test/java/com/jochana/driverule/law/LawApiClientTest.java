package com.jochana.driverule.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 요청 주소 · 쪽 넘김 · 인증값 가리기 (결정 011 4번). */
class LawApiClientTest {

	static final LocalDate FROM = LocalDate.of(2026, 10, 1);
	static final LocalDate TO = LocalDate.of(2026, 11, 13);
	static final String OC = "secret-oc";

	MockRestServiceServer server;
	LawApiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new LawApiClient(builder, new LawSyncProperties(OC, List.of("001638"), 90, FROM),
				JsonMapper.builder().build());
	}

	static String page(int total, int from, int count, LocalDate promulgated) {
		List<String> rows = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			rows.add("{\"법령일련번호\":\"" + (from + i) + "\",\"법령명한글\":\"도로교통법\",\"공포번호\":\"" + (from + i)
					+ "\",\"공포일자\":\"" + promulgated.toString().replace("-", "") + "\"}");
		}
		return "{\"LawSearch\":{\"totalCnt\":\"" + total + "\",\"law\":[" + String.join(",", rows) + "]}}";
	}

	@Test
	void asksEflawWithAllVersionsAndPromulgationRange() {
		server.expect(requestTo(startsWith(LawApiClient.BASE_URL + "/DRF/lawSearch.do?")))
				.andExpect(method(HttpMethod.GET))
				.andExpect(queryParam("OC", OC))
				.andExpect(queryParam("target", "eflaw"))
				.andExpect(queryParam("type", "JSON"))
				.andExpect(queryParam("LID", "001638"))
				.andExpect(queryParam("nw", "1,2,3"))
				.andExpect(queryParam("ancYd", "20261001~20261113"))
				.andExpect(queryParam("sort", "ddes"))
				.andExpect(queryParam("display", "100"))
				.andExpect(queryParam("page", "1"))
				// charset 없는 Content-Type 이어도 UTF-8 로 읽는다
				.andRespond(withSuccess(LawSearchParserTest.fixture("eflaw-single.json")
						.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON));

		List<LawVersion> versions = client.versions("001638", FROM, TO);

		assertThat(versions).singleElement()
				.satisfies(v -> assertThat(v.lawName()).isEqualTo("도로교통법 시행령"));
		server.verify();
	}

	@Test
	void followsPagesUntilTotalCount() {
		server.expect(queryParam("page", "1"))
				.andRespond(withSuccess(page(150, 1, 100, TO), MediaType.APPLICATION_JSON));
		server.expect(queryParam("page", "2"))
				.andRespond(withSuccess(page(150, 101, 50, TO), MediaType.APPLICATION_JSON));

		assertThat(client.versions("001638", FROM, TO)).hasSize(150);
		server.verify();
	}

	@Test
	void stopsWhenRowsAreOlderThanRangeEvenIfServerIgnoredAncYd() {
		server.expect(ExpectedCount.once(), queryParam("page", "1"))
				.andRespond(withSuccess(page(5000, 1, 100, FROM.minusDays(1)), MediaType.APPLICATION_JSON));

		assertThat(client.versions("001638", FROM, TO)).hasSize(100);
		server.verify();
	}

	@Test
	void serverErrorMessageNeverContainsOc() {
		server.expect(queryParam("page", "1")).andRespond(withServerError());

		assertThatThrownBy(() -> client.versions("001638", FROM, TO))
				.isInstanceOf(LawApiException.class)
				.hasMessageNotContaining(OC);
	}

	@Test
	void parseErrorMessageNeverContainsOc() {
		server.expect(queryParam("page", "1"))
				.andRespond(withSuccess("<html>OC=" + OC + " 승인 전</html>", MediaType.TEXT_HTML));

		assertThatThrownBy(() -> client.versions("001638", FROM, TO))
				.isInstanceOf(LawApiException.class)
				.hasMessageContaining("JSON 이 아닌 응답")
				.hasMessageNotContaining(OC);
	}

	@Test
	void maskHidesOcInUrlsAndBareValue() {
		assertThat(client.mask("I/O error on GET request for \"https://www.law.go.kr/DRF/lawSearch.do?OC="
				+ OC + "&target=eflaw\": timeout"))
				.isEqualTo("I/O error on GET request for \"https://www.law.go.kr/DRF/lawSearch.do?OC=***&target=eflaw\": timeout");
		assertThat(client.mask("key " + OC + " rejected")).isEqualTo("key *** rejected");
	}

	@Test
	void refusesWithoutOc() {
		LawApiClient unconfigured = new LawApiClient(RestClient.builder(),
				new LawSyncProperties(" ", List.of("001638"), 90, FROM), JsonMapper.builder().build());

		assertThatThrownBy(() -> unconfigured.versions("001638", FROM, TO))
				.isInstanceOf(LawApiException.class)
				.hasMessageContaining("LAW_API_OC");
	}

}
