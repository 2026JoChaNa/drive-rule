package com.jochana.driverule.card;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jochana.driverule.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DB 없이 웹 계층만 확인한다 — 요청 JSON → CardContent(중첩 record) 바인딩, 검증 오류 형식, 관리자 키 필터.
 * 저장·상태 전이는 CardApiFlowTests(MySQL) 가 맡는다.
 */
@WebMvcTest(controllers = CardAdminController.class, properties = "driverule.admin.api-key=k")
@Import(DecisionTableJson.class)
class CardAdminControllerWebTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	CardService service;

	static final String BODY = """
			{ "cardType": "CONDITIONAL", "changeKind": "LAW", "audience": "GENERAL",
			  "title": "우회전", "ttsText": "t", "legalBasis": "제27조",
			  "lawExcerpt": "원문", "effectiveOn": "2027-06-03",
			  "decision": { "schema": 2, "tables": [{ "key": "approach", "title": "진입 전",
			    "conditions": ["front_traffic_light"],
			    "rows": [{ "when": { "front_traffic_light": "red" }, "text": "멈춥니다", "tts": "멈춥니다" },
			             { "when": { "front_traffic_light": "green" }, "text": "천천히", "tts": "천천히" },
			             { "when": { "front_traffic_light": "yellow" }, "text": "멈춥니다", "tts": "멈춥니다" }] }] } }
			""";

	@Test
	void bindsNestedDecisionTableAndPassesItToService() throws Exception {
		given(service.create(any())).willThrow(new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CARD_INVALID",
				"카드 내용이 카드 유형 규칙에 맞지 않습니다", List.of("e1")));

		mvc.perform(post("/api/v1/admin/cards").header("X-Admin-Key", "k")
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CARD_INVALID"))
				.andExpect(jsonPath("$.errors[0]").value("e1"));

		org.mockito.Mockito.verify(service).create(org.mockito.ArgumentMatchers.argThat(c ->
				c.decision().tables().get(0).rows().get(0).when().get("front_traffic_light").equals("red")
						&& c.effectiveOn().toString().equals("2027-06-03")
						&& c.cardType() == CardType.CONDITIONAL));
	}

	@Test
	void beanValidationErrorsAreListed() throws Exception {
		mvc.perform(post("/api/v1/admin/cards").header("X-Admin-Key", "k")
				.contentType(MediaType.APPLICATION_JSON).content("{ \"cardType\": \"SHORT\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.errors", hasItem("title: must not be blank")));
	}

	@Test
	void unknownEnumIsBadRequest() throws Exception {
		mvc.perform(post("/api/v1/admin/cards").header("X-Admin-Key", "k")
				.contentType(MediaType.APPLICATION_JSON).content("{ \"cardType\": \"LONG\" }"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void missingKeyIsRejectedBeforeController() throws Exception {
		mvc.perform(post("/api/v1/admin/cards/1/retire"))
				.andExpect(status().isUnauthorized());
		org.mockito.Mockito.verifyNoInteractions(service);
	}

	/** 라우팅은 경로 파라미터(;)를 떼고 %-인코딩을 풀어 맞춘다 — 키 검사도 같은 기준이어야 한다. */
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "/api/v1;x/admin/cards/1/retire",
			"/api/v1/%61dmin/cards/1/retire", "/api/v1/admin;x/cards/1/retire" })
	void spellingVariantsOfAdminPathAlsoNeedKey(String uri) throws Exception {
		mvc.perform(post(java.net.URI.create(uri)))
				.andExpect(status().isUnauthorized());
		org.mockito.Mockito.verifyNoInteractions(service);
	}

	@Test
	void badQueryOrPathValueUsesErrorFormat() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/admin/cards?status=BOGUS").header("X-Admin-Key", "k"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("status:")));
		mvc.perform(post("/api/v1/admin/cards/abc/retire").header("X-Admin-Key", "k"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void stateConflictUsesProblemDetail() throws Exception {
		given(service.retire(eq(1L))).willThrow(ApiException.conflict("INVALID_CARD_STATE", "회수된 카드"));

		mvc.perform(post("/api/v1/admin/cards/1/retire").header("X-Admin-Key", "k"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("INVALID_CARD_STATE"));
	}

}
