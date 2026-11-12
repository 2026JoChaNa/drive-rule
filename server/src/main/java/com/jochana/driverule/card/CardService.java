package com.jochana.driverule.card;

import com.jochana.driverule.common.ApiException;
import com.jochana.driverule.law.LawRevision;
import com.jochana.driverule.law.LawRevisionRepository;
import com.jochana.driverule.law.TriageStatus;
import com.jochana.driverule.review.CardReview;
import com.jochana.driverule.review.CardReviewRepository;
import com.jochana.driverule.review.ReviewDecision;
import com.jochana.driverule.review.ReviewerRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 수명주기의 진행 — 잠금 · 조회 · 저장과, 카드 밖 데이터가 필요한 확인을 맡는다.
 * 카드 혼자 판단하는 규칙(상태별 허용 동작 · 버전 · 게시 · 주제 잠금)은 {@link RuleCard} 에 있다.
 *
 * <pre>
 * DRAFT ──submit──▶ IN_REVIEW ──승인 2건──▶ PUBLISHED
 *   ▲                  │ 반려                 │ 수정
 *   └──────────────────┴──────────────────────┘
 * (RETIRED 외 어느 상태에서든) ──retire──▶ RETIRED (끝)
 * </pre>
 *
 * 여기서 확인하는 것 (카드 밖 데이터): 카드 내용 규칙({@link CardValidator}) · 주제 중복 · 법령 개정 분류 ·
 * 검수자 존재 · 같은 검수자 중복 · 이 버전의 검수 기록과 승인 수.
 * 상태를 바꾸는 작업은 카드 행을 잠근 뒤 한다 — 동시에 두 승인이 들어와도 게시를 놓치지 않게.
 */
@Service
public class CardService {

	private final RuleCardRepository cards;
	private final LawRevisionRepository revisions;
	private final ReviewerRepository reviewers;
	private final CardReviewRepository reviews;
	private final DecisionTableJson decisionJson;
	private final Clock clock;

	public CardService(RuleCardRepository cards, LawRevisionRepository revisions, ReviewerRepository reviewers,
			CardReviewRepository reviews, DecisionTableJson decisionJson, Clock clock) {
		this.cards = cards;
		this.revisions = revisions;
		this.reviewers = reviewers;
		this.reviews = reviews;
		this.decisionJson = decisionJson;
		this.clock = clock;
	}

	@Transactional
	public RuleCard create(CardContent raw) {
		CardContent content = validated(raw);
		ensureTopicFree(content.topic(), null);
		return cards.save(RuleCard.draft(content, findRevision(content.revisionId()),
				decisionJson.write(content.decision()), now()));
	}

	@Transactional
	public RuleCard update(Long id, CardContent raw) {
		RuleCard card = lock(id);
		CardContent content = validated(raw);
		ensureTopicFree(content.topic(), card.getId());
		card.edit(content, findRevision(content.revisionId()), decisionJson.write(content.decision()), now());
		return card;
	}

	@Transactional
	public RuleCard submitForReview(Long id) {
		RuleCard card = lock(id);
		card.submitForReview(reviews.existsByCardIdAndCardVersion(card.getId(), card.getVersion()), now());
		return card;
	}

	@Transactional
	public RuleCard review(Long id, Long reviewerId, ReviewDecision decision) {
		RuleCard card = lock(id);
		card.ensureReviewable();
		if (!reviewers.existsById(reviewerId)) {
			throw ApiException.notFound("REVIEWER_NOT_FOUND", "검수자가 없습니다: " + reviewerId);
		}
		if (reviews.existsByCardIdAndCardVersionAndReviewerId(card.getId(), card.getVersion(), reviewerId)) {
			throw ApiException.conflict("ALREADY_REVIEWED", "이 검수자는 이미 이 버전을 검수했습니다");
		}
		LocalDateTime now = now();
		reviews.save(new CardReview(card.getId(), reviewerId, card.getVersion(), decision, now));
		long approvals = decision == ReviewDecision.APPROVE
				? reviews.countByCardIdAndCardVersionAndDecision(card.getId(), card.getVersion(), ReviewDecision.APPROVE)
				: 0;
		card.applyReview(decision, approvals, now);
		return card;
	}

	@Transactional
	public RuleCard retire(Long id) {
		RuleCard card = lock(id);
		card.retire(now());
		return card;
	}

	/** 대표 카드 지정 · 해제 (006). 규칙은 {@link RuleCard#assignTopic}. */
	@Transactional
	public RuleCard assignTopic(Long id, String rawTopic) {
		RuleCard card = lock(id);
		String topic = RuleCard.normalizeTopic(rawTopic);
		ensureTopicFree(topic, card.getId());
		card.assignTopic(topic, now());
		return card;
	}

	@Transactional(readOnly = true)
	public RuleCard get(Long id) {
		return cards.findById(id).orElseThrow(() -> cardNotFound(id));
	}

	@Transactional(readOnly = true)
	public List<RuleCard> list(CardStatus status) {
		return status == null ? cards.findAllByOrderByIdAsc() : cards.findAllByStatusOrderByIdAsc(status);
	}

	@Transactional(readOnly = true)
	public List<CardReview> reviewsOf(Long id) {
		get(id);
		return reviews.findAllByCardIdOrderByReviewedAtAscIdAsc(id);
	}

	private CardContent validated(CardContent raw) {
		CardContent content = raw.normalized();
		List<String> errors = CardValidator.validate(content);
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CARD_INVALID", "카드 내용이 카드 유형 규칙에 맞지 않습니다",
					errors);
		}
		return content;
	}

	private void ensureTopicFree(String topic, Long selfId) {
		if (topic == null) {
			return;
		}
		cards.findByTopic(topic)
				.filter(other -> !other.getId().equals(selfId))
				.ifPresent(other -> {
					throw ApiException.conflict("TOPIC_TAKEN",
							"주제 '" + topic + "' 는 이미 카드 " + other.getId() + " 가 대표입니다");
				});
	}

	private LawRevision findRevision(Long revisionId) {
		if (revisionId == null) {
			return null;
		}
		LawRevision revision = revisions.findById(revisionId)
				.orElseThrow(() -> ApiException.notFound("LAW_REVISION_NOT_FOUND", "법령 개정이 없습니다: " + revisionId));
		if (revision.getTriageStatus() == TriageStatus.NOT_APPLICABLE) {
			throw ApiException.conflict("LAW_REVISION_NOT_APPLICABLE",
					"일반 운전자 해당 없음으로 분류된 법령 개정에는 카드를 잇지 않습니다: " + revision.getLawNo());
		}
		return revision;
	}

	private RuleCard lock(Long id) {
		return cards.findForUpdate(id).orElseThrow(() -> cardNotFound(id));
	}

	public static ApiException cardNotFound(Long id) {
		return ApiException.notFound("CARD_NOT_FOUND", "카드가 없습니다: " + id);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

}
