package com.jochana.driverule;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code docs/api/admin-api.md} 의 예시를 문서 순서대로 돌린다 — 문서가 서버 동작과 어긋나면 여기서 실패한다.
 * 장 번호는 문서의 장 번호다. 문서를 고치면 이 테스트도 함께 고친다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "driverule.admin.api-key=manual-key", "driverule.law-sync.oc=" })
@AutoConfigureMockMvc
class AdminManualTests {

	static final String CARD_H = """
			{ "revisionId": %d,
			  "cardType": "SHORT", "changeKind": "LAW", "audience": "GENERAL", "eventTag": "SCHOOL_ZONE_ENTRY",
			  "title": "%s",
			  "ttsText": "어린이보호구역입니다. 신호등 없는 횡단보도 앞에서는 사람이 없어도 먼저 멈춥니다.",
			  "legalBasis": "도로교통법 제27조제7항",
			  "lawExcerpt": "제27조(보행자의 보호) ⑦ …",
			  "penaltyText": "승용차 범칙금 6만 원 · 벌점 10점",
			  "effectiveOn": "2022-07-12" }
			""";

	@Autowired
	MockMvc mvc;

	@Test
	void manualWalkthrough() throws Exception {
		// 0. 키
		mvc.perform(get("/api/v1/admin/reviewers").header("X-Admin-Key", "wrong"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("ADMIN_KEY_REQUIRED"));

		// 1. 검수자
		long r1 = id(admin(post("/api/v1/admin/reviewers"), "{\"name\":\"검수자1\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("검수자1")));
		long r2 = id(admin(post("/api/v1/admin/reviewers"), "{\"name\":\"검수자2\"}"));
		admin(get("/api/v1/admin/reviewers"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", hasItem((int) r1)));

		// 2-1. 인증값이 없으면 수집은 503
		admin(post("/api/v1/admin/law-revisions/sync"), null)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("LAW_API_NOT_CONFIGURED"));

		// 2-2. 손 등록 · 중복
		String revision = """
				{ "lawName": "도로교통법 일부개정", "lawNo": "제98067호",
				  "sourceKey": "MANUAL-98067", "promulgatedOn": "2026-10-13" }
				""";
		long rev = id(admin(post("/api/v1/admin/law-revisions"), revision)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.triageStatus").value("PENDING"))
			.andExpect(jsonPath("$.sourceUrl").value(nullValue())));
		admin(post("/api/v1/admin/law-revisions"), revision)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LAW_NO_DUPLICATED"));

		// 2-3. 목록 · 분류 · 해당 없음에는 카드를 잇지 않음
		admin(get("/api/v1/admin/law-revisions").param("triageStatus", "PENDING"), null)
			.andExpect(jsonPath("$[*].id", hasItem((int) rev)));
		admin(patch("/api/v1/admin/law-revisions/" + rev + "/triage"), "{\"triageStatus\":\"APPLICABLE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.triageStatus").value("APPLICABLE"));
		long notApplicable = id(admin(post("/api/v1/admin/law-revisions"), """
				{ "lawName": "도로교통법 시행규칙 일부개정", "lawNo": "제98068호",
				  "sourceKey": "MANUAL-98068", "promulgatedOn": "2026-10-20" }
				"""));
		admin(patch("/api/v1/admin/law-revisions/" + notApplicable + "/triage"),
				"{\"triageStatus\":\"NOT_APPLICABLE\"}");
		admin(post("/api/v1/admin/cards"), CARD_H.formatted(notApplicable, "해당 없음에 잇기"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LAW_REVISION_NOT_APPLICABLE"));

		// 3-1. 작성 · 칸 사이 규칙
		long card = id(admin(post("/api/v1/admin/cards"), CARD_H.formatted(rev, "보호구역 신호등 없는 횡단보도 일시정지"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.version").value(1)));
		admin(post("/api/v1/admin/cards"), CARD_H.formatted(rev, "장면 하나만")
			.replace("\"effectiveOn\"", "\"dontText\": \"그냥 지나감\", \"effectiveOn\""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("CARD_INVALID"))
			.andExpect(jsonPath("$.errors[0]").value(containsString("dontText, doText")));

		// 3-2. 결정 테이블 — 빠진 조합을 알려 준다
		admin(post("/api/v1/admin/cards"), """
				{ "cardType": "CONDITIONAL", "changeKind": "LAW", "audience": "GENERAL",
				  "title": "빈칸 있는 표", "ttsText": "t", "legalBasis": "b", "lawExcerpt": "e",
				  "decision": { "schema": 2, "tables": [
				    { "key": "crosswalk", "title": "돌아선 뒤 횡단보도",
				      "conditions": ["crosswalk_light", "pedestrian_present"],
				      "rows": [
				        { "when": { "crosswalk_light": "green", "pedestrian_present": "yes" }, "text": "a", "tts": "a" },
				        { "when": { "crosswalk_light": "green", "pedestrian_present": "no" }, "text": "b", "tts": "b" } ] } ] } }
				""")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0]").value(containsString("어느 행에도 걸리지 않는 조합")));

		// 3-3. 목록 · 상세
		admin(get("/api/v1/admin/cards").param("status", "DRAFT"), null)
			.andExpect(jsonPath("$[*].id", hasItem((int) card)));
		admin(get("/api/v1/admin/cards/" + card), null).andExpect(jsonPath("$.revisionId").value(rev));

		// 3-5. 검수 요청 · 검수
		admin(post("/api/v1/admin/cards/" + card + "/reviews"), review(r1, "APPROVE"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_CARD_STATE"));
		admin(post("/api/v1/admin/cards/" + card + "/submit"), null).andExpect(jsonPath("$.status").value("IN_REVIEW"));
		admin(post("/api/v1/admin/cards/" + card + "/submit"), null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_CARD_STATE"));
		admin(post("/api/v1/admin/cards/" + card + "/reviews"), review(999999, "APPROVE"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REVIEWER_NOT_FOUND"));
		admin(post("/api/v1/admin/cards/" + card + "/reviews"), review(r1, "APPROVE"))
			.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		admin(post("/api/v1/admin/cards/" + card + "/reviews"), review(r1, "APPROVE"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"));
		admin(post("/api/v1/admin/cards/" + card + "/reviews"), review(r2, "APPROVE"))
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.verifiedAt").value(notNullValue()));
		admin(get("/api/v1/admin/cards/" + card + "/reviews"), null).andExpect(jsonPath("$.length()").value(2));

		// 3-6. 동기화에 나옴
		mvc.perform(get("/api/v1/cards/sync"))
			.andExpect(jsonPath("$.cards[*].cardId", hasItem((int) card)));

		// 3-7. 대표 주제 — 지정 · 중복 · 해제
		admin(put("/api/v1/admin/cards/" + card + "/topic"), "{\"topic\":\"school_zone_crosswalk\"}")
			.andExpect(jsonPath("$.topic").value("school_zone_crosswalk"))
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		long other = id(admin(post("/api/v1/admin/cards"), CARD_H.formatted(rev, "같은 주제를 원하는 카드")));
		admin(put("/api/v1/admin/cards/" + other + "/topic"), "{\"topic\":\"school_zone_crosswalk\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TOPIC_TAKEN"))
			.andExpect(jsonPath("$.detail").value(containsString(String.valueOf(card))));

		// 3-4. 수정 → DRAFT. 게시된 적 있는 카드는 수정 중 주제를 바꿀 수 없다. 앱 동기화에서는 빠진다
		String edited = CARD_H.formatted(rev, "고친 제목").replace("\"cardType\"",
				"\"topic\": \"school_zone_crosswalk\", \"cardType\"");
		admin(put("/api/v1/admin/cards/" + card), edited)
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.version").value(1));
		admin(put("/api/v1/admin/cards/" + card + "/topic"), "{\"topic\":null}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TOPIC_CHANGE_NOT_SYNCED"));
		mvc.perform(get("/api/v1/cards/sync"))
			.andExpect(jsonPath("$.cards[*].cardId", not(hasItem((int) card))))
			.andExpect(jsonPath("$.retiredCardIds", not(hasItem((int) card))));

		// 3-5. 다시 검수 요청하면 버전이 오르고, 반려 1건이면 DRAFT
		admin(post("/api/v1/admin/cards/" + card + "/submit"), null).andExpect(jsonPath("$.version").value(2));
		admin(post("/api/v1/admin/cards/" + card + "/reviews"), review(r1, "REJECT"))
			.andExpect(jsonPath("$.status").value("DRAFT"));

		// 3-8. 회수 — 주제가 비고, 고칠 수 없다. 주제는 다른 카드가 이어받을 수 있다
		admin(post("/api/v1/admin/cards/" + card + "/retire"), null)
			.andExpect(jsonPath("$.status").value("RETIRED"))
			.andExpect(jsonPath("$.topic").value(nullValue()));
		admin(put("/api/v1/admin/cards/" + card), CARD_H.formatted(rev, "회수 뒤 수정"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_CARD_STATE"));
		admin(put("/api/v1/admin/cards/" + other + "/topic"), "{\"topic\":\"school_zone_crosswalk\"}")
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/cards/sync")).andExpect(jsonPath("$.retiredCardIds", hasItem((int) card)));

		// 4. 검수 요청 대기열
		mvc.perform(post("/api/v1/answer-holds").contentType(MediaType.APPLICATION_JSON)
			.content("{\"topic\":\"left_turn\",\"conditions\":{\"front_traffic_light\":\"red\"}}"))
			.andExpect(status().is2xxSuccessful());
		String list = admin(get("/api/v1/admin/review-requests").param("status", "OPEN")
			.param("requestType", "ANSWER_HOLD"), null)
			.andExpect(jsonPath("$[0].topic").value("left_turn"))
			.andExpect(jsonPath("$[0].conditions.front_traffic_light").value("red"))
			.andReturn().getResponse().getContentAsString();
		long requestId = ((Number) JsonPath.read(list, "$[0].id")).longValue();
		admin(patch("/api/v1/admin/review-requests/" + requestId), "{\"status\":\"RESOLVED\"}")
			.andExpect(jsonPath("$.status").value("RESOLVED"));

		// 5. 없는 id · 모르는 enum 값
		admin(get("/api/v1/admin/cards/999999"), null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
		admin(get("/api/v1/admin/cards").param("status", "LIVE"), null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	static String review(long reviewerId, String decision) {
		return "{\"reviewerId\":" + reviewerId + ",\"decision\":\"" + decision + "\"}";
	}

	ResultActions admin(MockHttpServletRequestBuilder req, String body) throws Exception {
		req.header("X-Admin-Key", "manual-key");
		if (body != null) {
			req.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mvc.perform(req);
	}

	static long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
