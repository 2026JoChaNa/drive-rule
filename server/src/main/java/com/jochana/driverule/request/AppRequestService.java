package com.jochana.driverule.request;

import com.jochana.driverule.card.CardService;
import com.jochana.driverule.card.RuleCard;
import com.jochana.driverule.card.RuleCardRepository;
import com.jochana.driverule.card.RuleCondition;
import com.jochana.driverule.common.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 차 → 서버 경로 두 개: 오류 신고 · 답변 보류 (결정 003 · 005 · 009 E). 둘 다 식별자 없이 받는다.
 * Gleo 답변 자체는 앱이 차량 사본으로 하므로 서버로 오지 않는다 (009 F).
 */
@Service
public class AppRequestService {

	private final RuleCardRepository cards;
	private final ReviewRequestRepository requests;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public AppRequestService(RuleCardRepository cards, ReviewRequestRepository requests, JsonMapper jsonMapper,
			Clock clock) {
		this.cards = cards;
		this.requests = requests;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/**
	 * 근거 시트 '오류 신고'. 앱이 가진 사본의 버전을 함께 받는다 — 이미 고친 내용인지 알려고.
	 * 한 번도 게시되지 않은 카드는 차에 있을 수 없으니 없는 카드와 똑같이 404 로 답한다 (작업 중인 카드 ID 를 드러내지 않게).
	 */
	@Transactional
	public void reportError(Long cardId, int cardVersion) {
		RuleCard card = cards.findById(cardId)
				.filter(c -> c.getVerifiedAt() != null)
				.orElseThrow(() -> CardService.cardNotFound(cardId));
		if (cardVersion > card.getVersion()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CARD_VERSION_UNKNOWN",
					"서버에 없는 카드 버전입니다: " + cardVersion);
		}
		requests.save(ReviewRequest.errorReport(cardId, cardVersion, LocalDateTime.now(clock)));
	}

	/**
	 * Gleo 답변 보류 — 검수 대기열에 '이 주제 · 조건에 답할 카드가 필요하다'를 남긴다 (Figma 07-1).
	 * 조건은 공통 조건 목록에 있는 키 · 값만 받는다 — 자유 문자열이 들어올 틈을 두지 않으려고.
	 */
	@Transactional
	public void reportAnswerHold(String topic, Map<String, String> conditions) {
		Map<String, String> given = conditions == null ? Map.of() : conditions;
		List<String> errors = new ArrayList<>();
		given.forEach((key, value) -> RuleCondition.byKey(key).ifPresentOrElse(
				c -> {
					if (!c.hasOption(value)) {
						errors.add("conditions." + key + ": 없는 선택지입니다 (" + value + ")");
					}
				},
				() -> errors.add("conditions." + key + ": 공통 조건 목록에 없는 조건입니다")));
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CONDITION_UNKNOWN", "조건이 공통 조건 목록에 맞지 않습니다",
					errors);
		}
		String json = given.isEmpty() ? null : jsonMapper.writeValueAsString(new TreeMap<>(given));
		requests.save(ReviewRequest.answerHold(topic.strip(), json, LocalDateTime.now(clock)));
	}

}
