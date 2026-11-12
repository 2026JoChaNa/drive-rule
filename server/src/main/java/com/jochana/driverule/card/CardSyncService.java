package com.jochana.driverule.card;

import com.jochana.driverule.common.ApiException;
import com.jochana.driverule.law.LawRevision;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차량 앱 카드 동기화 (ERD '게시본·회수 동기화', 결정 004 · 009 G). 하루 1회 + 시동 시 부른다 (009 F).
 * <ul>
 * <li>PUBLISHED → 내용 전체 + version (앱 cached_card 와 같은 모양, 법령명·공포 번호·공포일은 law_revision 에서 복사)</li>
 * <li>RETIRED → 카드 ID 만 (앱이 is_retired = 1)</li>
 * <li>DRAFT · IN_REVIEW → 보내지 않음 (앱은 마지막 게시본을 계속 씀)</li>
 * </ul>
 * 증분은 불투명 토큰({@code syncToken})으로 한다. 앱은 토큰을 해석하지 않고 저장했다가 그대로 돌려준다.
 * 토큰이 무효면 410 → 앱은 토큰 없이 전체를 다시 받는다 (Google Calendar 동기화와 같은 방식).
 */
@Service
public class CardSyncService {

	/**
	 * 토큰 형식 번호. 카드 형식이 바뀌어 모든 차가 전체를 다시 받아야 할 때 올린다 — 예전 토큰은 410 이 된다.
	 * 2 = 결정 테이블 형식 2 (009 B)
	 */
	static final int TOKEN_FORMAT = 2;

	/**
	 * 다음 증분 기준 시각을 이만큼 앞당긴다. 동기화 직전에 시작해 늦게 커밋된 변경을 놓치지 않으려고
	 * 겹치게 받는다 (중복은 앱이 카드 ID 로 덮어쓰기로 흡수).
	 */
	static final Duration OVERLAP = Duration.ofMinutes(5);

	private final RuleCardRepository cards;
	private final DecisionTableJson decisionJson;
	private final Clock clock;

	public CardSyncService(RuleCardRepository cards, DecisionTableJson decisionJson, Clock clock) {
		this.cards = cards;
		this.decisionJson = decisionJson;
		this.clock = clock;
	}

	/**
	 * @param fullSync   true = 토큰 없이 처음부터 보낸 응답 (게시 카드 전부 + 회수 ID 전부).
	 *                   <b>앱은 받지 못한 사본을 지우지 않는다</b> — 게시 후 수정 · 검수 중인 카드는 목록에 없지만
	 *                   차는 마지막 게시본을 계속 써야 한다 (결정 004). 카드를 내리는 신호는 retiredCardIds 뿐이다
	 * @param conditions 공통 조건 목록 — 앱 되묻기 화면의 질문 · 선택지 문구 (009 B-2)
	 */
	public record SyncResponse(String syncToken, boolean fullSync, List<SyncedCard> cards, List<Long> retiredCardIds,
			List<ConditionDef> conditions) {
	}

	/**
	 * 앱 cached_card 1행에 해당한다. {@code verifiedAt} 은 시차를 붙여 보낸다 (예: 2026-11-12T09:00:00+09:00) —
	 * 앱이 epoch 로 바꿀 때 시간대를 짐작하지 않게.
	 */
	public record SyncedCard(Long cardId, int version, CardType cardType, ChangeKind changeKind, Audience audience,
			EventTag eventTag, String sourceOrg, String topic, String title, String dontText, String doText,
			String sceneKey, String ttsText, String legalBasis, String lawExcerpt, String penaltyText, String lawName,
			String lawNo, LocalDate promulgatedOn, LocalDate effectiveOn, String exceptions, DecisionTable decision,
			OffsetDateTime verifiedAt) {
	}

	public record ConditionDef(String key, String question, List<RuleCondition.Option> options) {

		static ConditionDef of(RuleCondition c) {
			return new ConditionDef(c.key(), c.question(), c.options());
		}
	}

	@Transactional(readOnly = true)
	public SyncResponse sync(String syncToken) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime since = syncToken == null ? null : decode(syncToken);
		List<RuleCard> published = since == null
				? cards.findAllWithRevisionByStatus(CardStatus.PUBLISHED)
				: cards.findAllWithRevisionByStatusUpdatedSince(CardStatus.PUBLISHED, since);
		List<Long> retired = since == null
				? cards.findIdsByStatus(CardStatus.RETIRED)
				: cards.findIdsByStatusUpdatedSince(CardStatus.RETIRED, since);
		List<ConditionDef> conditions = Arrays.stream(RuleCondition.values()).map(ConditionDef::of).toList();
		return new SyncResponse(encode(now.minus(OVERLAP)), since == null,
				published.stream().map(this::toSynced).toList(), retired, conditions);
	}

	private String encode(LocalDateTime since) {
		long millis = since.atZone(clock.getZone()).toInstant().toEpochMilli();
		String raw = TOKEN_FORMAT + ":" + millis;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private LocalDateTime decode(String token) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = raw.split(":", 2);
			if (parts.length == 2 && Integer.parseInt(parts[0]) == TOKEN_FORMAT) {
				return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[1])), clock.getZone());
			}
		}
		catch (IllegalArgumentException e) {
			// 아래에서 410 으로 알린다 (NumberFormatException 포함)
		}
		throw new ApiException(HttpStatus.GONE, "SYNC_TOKEN_EXPIRED", "syncToken 이 무효입니다 — 토큰 없이 전체를 다시 받으세요");
	}

	private SyncedCard toSynced(RuleCard c) {
		LawRevision r = c.getRevision();
		return new SyncedCard(c.getId(), c.getVersion(), c.getCardType(), c.getChangeKind(), c.getAudience(),
				c.getEventTag(), c.getSourceOrg(), c.getTopic(), c.getTitle(), c.getDontText(), c.getDoText(),
				c.getSceneKey(), c.getTtsText(), c.getLegalBasis(), c.getLawExcerpt(), c.getPenaltyText(),
				r == null ? null : r.getLawName(),
				r == null ? null : r.getLawNo(),
				r == null ? null : r.getPromulgatedOn(),
				c.getEffectiveOn(), c.getExceptions(), decisionJson.read(c.getDecisionJson()),
				c.getVerifiedAt().atZone(clock.getZone()).toOffsetDateTime());
	}

}
