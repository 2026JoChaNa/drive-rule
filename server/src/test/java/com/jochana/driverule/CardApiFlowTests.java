package com.jochana.driverule;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MySQL(Testcontainers) 위에서 Flyway 스키마 + JPA 매핑 + API 흐름을 끝까지 확인한다.
 * 시나리오: 기획 v6 — 이벤트 카드(H) 와 Gleo 조건 분기 카드(우회전) 를 검수 · 게시 · 동기화 · 회수 (결정 004 · 009).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "driverule.admin.api-key=test-key")
@AutoConfigureMockMvc
class CardApiFlowTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper jsonMapper;

	@Test
	void adminApiNeedsKey() throws Exception {
		mvc.perform(get("/api/v1/admin/cards"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ADMIN_KEY_REQUIRED"));
	}

	@Test
	void cardLifecycleFromDraftToRetiredReachesTheApp() throws Exception {
		long revisionId = idOf(admin(post("/api/v1/admin/law-revisions"), """
				{ "lawName": "도로교통법 일부개정", "lawNo": "제99001호", "sourceKey": "TEST-99001",
				  "promulgatedOn": "2026-10-13" }
				""").andExpect(status().isCreated()));
		long r1 = idOf(admin(post("/api/v1/admin/reviewers"), "{\"name\":\"검수자1\"}"));
		long r2 = idOf(admin(post("/api/v1/admin/reviewers"), "{\"name\":\"검수자2\"}"));

		// 작성 — 시드의 우회전 조건 분기 카드 (형식 2, 소표 두 개)
		String rightTurn = seedCard("right_turn").replace("\"topic\":\"right_turn\"", "\"topic\":\"right_turn_flow\"")
				.replaceFirst("\\{", "{\"revisionId\":" + revisionId + ",");
		long cardId = idOf(admin(post("/api/v1/admin/cards"), rightTurn)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.changeKind").value("LAW"))
				.andExpect(jsonPath("$.decision.tables.length()").value(2)));

		// DRAFT 는 앱에 가지 않는다
		mvc.perform(get("/api/v1/cards/sync"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullSync").value(true))
				.andExpect(jsonPath("$.cards[*].cardId", not(hasItem((int) cardId))))
				.andExpect(jsonPath("$.conditions[*].key", hasItem("ped_signal")));

		// 검수 요청 → 승인 1건 → 같은 검수자 중복 → 승인 2건 = 게시
		admin(post("/api/v1/admin/cards/" + cardId + "/submit"), "")
				.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		admin(post("/api/v1/admin/cards/" + cardId + "/reviews"), review(r1, "APPROVE"))
				.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		admin(post("/api/v1/admin/cards/" + cardId + "/reviews"), review(r1, "APPROVE"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"));
		admin(post("/api/v1/admin/cards/" + cardId + "/reviews"), review(r2, "APPROVE"))
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.verifiedAt", notNullValue()));

		// 앱 동기화 — 근거 시트용 법령명 · 공포 번호 · 공포일과 결정 테이블이 사본에 함께 온다
		String card = "$.cards[?(@.cardId == " + cardId + ")]";
		String firstSync = mvc.perform(get("/api/v1/cards/sync"))
				.andExpect(jsonPath(card + ".lawNo").value(hasItem("제99001호")))
				.andExpect(jsonPath(card + ".promulgatedOn").value(hasItem("2026-10-13")))
				.andExpect(jsonPath(card + ".audience").value(hasItem("GENERAL")))
				.andExpect(jsonPath(card + ".verifiedAt").value(hasItem(matchesPattern(".*T.*\\+09:00$"))))
				.andExpect(jsonPath(card + ".decision.tables[1].conditions[0]").value(hasItem("ped_signal")))
				.andReturn().getResponse().getContentAsString();
		String syncToken = JsonPath.read(firstSync, "$.syncToken");

		// 오류 신고 (근거 시트) · 답변 보류 (Gleo)
		mvc.perform(json(post("/api/v1/cards/" + cardId + "/error-reports"), "{\"cardVersion\":1}"))
				.andExpect(status().isAccepted());
		mvc.perform(json(post("/api/v1/cards/" + cardId + "/error-reports"), "{\"cardVersion\":9}"))
				.andExpect(status().isUnprocessableContent());
		mvc.perform(json(post("/api/v1/cards/999999/error-reports"), "{\"cardVersion\":1}"))
				.andExpect(status().isNotFound());
		mvc.perform(json(post("/api/v1/answer-holds"),
				"{\"topic\":\"highway_shoulder\",\"conditions\":{\"pedestrian\":\"no\"}}"))
				.andExpect(status().isAccepted());
		mvc.perform(json(post("/api/v1/answer-holds"), "{\"topic\":\"x\",\"conditions\":{\"weather\":\"rain\"}}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CONDITION_UNKNOWN"));
		admin(get("/api/v1/admin/review-requests?requestType=ANSWER_HOLD"), "")
				.andExpect(jsonPath("$[?(@.topic == 'highway_shoulder')].conditions.pedestrian").value(hasItem("no")));

		// 게시된 적 있는 카드의 주제는 수정으로 못 바꾼다 — DRAFT 라 차에 가지 않는다
		admin(put("/api/v1/admin/cards/" + cardId), rightTurn.replace("right_turn_flow", "right_turn_other"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TOPIC_CHANGE_NOT_SYNCED"));

		// 게시 카드 수정 → DRAFT, 다시 검수에 올리면 version 2
		admin(put("/api/v1/admin/cards/" + cardId), rightTurn)
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.version").value(1));
		admin(put("/api/v1/admin/cards/" + cardId + "/topic"), "{\"topic\":null}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TOPIC_CHANGE_NOT_SYNCED"));
		// 수정 중이어도 오류 신고는 받는다 — 차에는 v1 게시본이 있다
		mvc.perform(json(post("/api/v1/cards/" + cardId + "/error-reports"), "{\"cardVersion\":1}"))
				.andExpect(status().isAccepted());
		admin(post("/api/v1/admin/cards/" + cardId + "/submit"), "")
				.andExpect(jsonPath("$.version").value(2));

		// 회수 → topic 비움, 증분 동기화에 ID 만 간다
		admin(post("/api/v1/admin/cards/" + cardId + "/retire"), "")
				.andExpect(jsonPath("$.status").value("RETIRED"))
				.andExpect(jsonPath("$.topic", nullValue()));
		mvc.perform(get("/api/v1/cards/sync").param("syncToken", syncToken))
				.andExpect(jsonPath("$.fullSync").value(false))
				.andExpect(jsonPath("$.retiredCardIds", hasItem((int) cardId)))
				.andExpect(jsonPath("$.cards[*].cardId", not(hasItem((int) cardId))));
		admin(put("/api/v1/admin/cards/" + cardId), rightTurn)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_CARD_STATE"));
	}

	@Test
	void retiredDraftNeverReachesTheApp() throws Exception {
		long id = idOf(admin(post("/api/v1/admin/cards"), seedCard("H")).andExpect(status().isCreated()));
		admin(post("/api/v1/admin/cards/" + id + "/retire"), "").andExpect(jsonPath("$.status").value("RETIRED"));

		mvc.perform(get("/api/v1/cards/sync"))
				.andExpect(jsonPath("$.retiredCardIds", not(hasItem((int) id))));
	}

	@Test
	void eventCardsFromSeedAreAccepted() throws Exception {
		for (String demo : new String[] { "B", "H", "I" }) {
			long id = idOf(admin(post("/api/v1/admin/cards"), seedCard(demo))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.eventTag", notNullValue())));
			// 게시된 적 없는 카드는 차에 있을 수 없다 → 없는 카드와 같은 404
			mvc.perform(json(post("/api/v1/cards/" + id + "/error-reports"), "{\"cardVersion\":1}"))
					.andExpect(status().isNotFound());
		}
	}

	@Test
	void badQueryValueUsesErrorFormat() throws Exception {
		admin(get("/api/v1/admin/cards?status=BOGUS"), "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void invalidSyncTokenAsksForFullSync() throws Exception {
		mvc.perform(get("/api/v1/cards/sync").param("syncToken", "not-a-token"))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("SYNC_TOKEN_EXPIRED"));
	}

	@Test
	void decisionTableWithAGapIsRejected() throws Exception {
		// 보행 신호 적색 행을 지우면 기획 v6 2-4 표와 같아진다 → 완전성 검사에 걸린다
		String gap = seedCard("right_turn").replace("\"topic\":\"right_turn\"", "\"topic\":null")
				.replaceAll(",\\{\"when\":\\{\"ped_signal\":\"red\"\\}[^}]*\\}", "");
		admin(post("/api/v1/admin/cards"), gap)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CARD_INVALID"))
				.andExpect(jsonPath("$.errors[0]").value(containsString("ped_signal=red")));
	}

	@Test
	void topicBelongsToOneCard() throws Exception {
		String card = seedCard("right_turn").replace("\"topic\":\"right_turn\"", "\"topic\":\"topic_unique\"");
		admin(post("/api/v1/admin/cards"), card).andExpect(status().isCreated());
		admin(post("/api/v1/admin/cards"), card)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TOPIC_TAKEN"));
	}

	/** 시드 파일의 카드 하나를 API 요청 본문으로 (seed.mjs 와 같은 변환: 메모 칸 · 공포 번호 참조 제거). */
	private String seedCard(String demoOrTopic) throws Exception {
		JsonNode seed = jsonMapper.readTree(Files.readString(Path.of("seed", "pt-cards.json")));
		JsonNode card = seed.get("cards").valueStream()
				.filter(n -> n.get("_demo").asString().equals(demoOrTopic)
						|| (n.has("topic") && n.get("topic").asString().equals(demoOrTopic)))
				.findFirst().orElseThrow();
		var copy = ((tools.jackson.databind.node.ObjectNode) card.deepCopy());
		copy.remove("_demo");
		copy.remove("revisionLawNo");
		return jsonMapper.writeValueAsString(copy);
	}

	private static String review(long reviewerId, String decision) {
		return "{\"reviewerId\":" + reviewerId + ",\"decision\":\"" + decision + "\"}";
	}

	private ResultActions admin(MockHttpServletRequestBuilder req, String body) throws Exception {
		return mvc.perform(json(req, body).header("X-Admin-Key", "test-key"));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
		return body.isEmpty() ? req : req.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static long idOf(ResultActions result) throws Exception {
		Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

}
