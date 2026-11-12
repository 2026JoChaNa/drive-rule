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
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 수명주기 (결정 004 · 006, 세부 규칙은 결정 008).
 *
 * <pre>
 * DRAFT ──submit──▶ IN_REVIEW ──승인 2건──▶ PUBLISHED
 *   ▲                  │ 반려                 │ 수정
 *   └──────────────────┴──────────────────────┘
 * (RETIRED 외 어느 상태에서든) ──retire──▶ RETIRED (끝)
 * </pre>
 *
 * <ul>
 * <li>버전 = 검수받은 내용 묶음의 번호. 이미 검수 기록이 있는 버전을 다시 검수에 올리면 +1 한다</li>
 * <li>수정하면 어느 상태든 DRAFT 로 돌아간다 — 앱은 마지막으로 받은 게시본을 계속 쓴다</li>
 * <li>회수하면 topic 을 비운다 — 다른 카드를 대표로 지정할 수 있게</li>
 * </ul>
 */
@Service
public class CardService {

	static final int REQUIRED_APPROVALS = 2;

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
		RuleCard card = new RuleCard(now());
		card.applyContent(content, findRevision(content.revisionId()), decisionJson.write(content.decision()));
		return cards.save(card);
	}

	@Transactional
	public RuleCard update(Long id, CardContent raw) {
		RuleCard card = lock(id);
		ensureNotRetired(card);
		CardContent content = validated(raw);
		// 수정하면 DRAFT 가 되어 동기화되지 않는다 — 게시된 적 있는 카드의 주제를 여기서 바꾸면 차 사본에 옛 주제가 남는다
		if (card.getVerifiedAt() != null && !Objects.equals(card.getTopic(), content.topic())) {
			throw ApiException.conflict("TOPIC_CHANGE_NOT_SYNCED",
					"게시된 적 있는 카드의 주제는 카드가 게시 중일 때 대표 주제 지정으로만 바꿀 수 있습니다");
		}
		ensureTopicFree(content.topic(), card.getId());
		card.applyContent(content, findRevision(content.revisionId()), decisionJson.write(content.decision()));
		card.changeStatus(CardStatus.DRAFT, now());
		return card;
	}

	@Transactional
	public RuleCard submitForReview(Long id) {
		RuleCard card = lock(id);
		if (card.getStatus() != CardStatus.DRAFT) {
			throw invalidState(card, "검수 요청은 DRAFT 카드만 할 수 있습니다");
		}
		// 같은 버전 번호에 이전 검수 기록이 있으면 새 내용 묶음으로 본다 — card_review UQ(card, version, reviewer)
		if (reviews.existsByCardIdAndCardVersion(card.getId(), card.getVersion())) {
			card.bumpVersion();
		}
		card.changeStatus(CardStatus.IN_REVIEW, now());
		return card;
	}

	@Transactional
	public RuleCard review(Long id, Long reviewerId, ReviewDecision decision) {
		RuleCard card = lock(id);
		if (card.getStatus() != CardStatus.IN_REVIEW) {
			throw invalidState(card, "IN_REVIEW 카드만 검수할 수 있습니다");
		}
		if (!reviewers.existsById(reviewerId)) {
			throw ApiException.notFound("REVIEWER_NOT_FOUND", "검수자가 없습니다: " + reviewerId);
		}
		if (reviews.existsByCardIdAndCardVersionAndReviewerId(card.getId(), card.getVersion(), reviewerId)) {
			throw ApiException.conflict("ALREADY_REVIEWED", "이 검수자는 이미 이 버전을 검수했습니다");
		}
		LocalDateTime now = now();
		reviews.save(new CardReview(card.getId(), reviewerId, card.getVersion(), decision, now));

		if (decision == ReviewDecision.REJECT) {
			card.changeStatus(CardStatus.DRAFT, now);
		}
		else if (reviews.countByCardIdAndCardVersionAndDecision(card.getId(), card.getVersion(),
				ReviewDecision.APPROVE) >= REQUIRED_APPROVALS) {
			card.markVerified(now);
			card.changeStatus(CardStatus.PUBLISHED, now);
		}
		return card;
	}

	@Transactional
	public RuleCard retire(Long id) {
		RuleCard card = lock(id);
		ensureNotRetired(card);
		LocalDateTime now = now();
		card.changeTopic(null, now);
		card.changeStatus(CardStatus.RETIRED, now);
		return card;
	}

	/**
	 * 대표 카드 지정·해제 (006). 문장이 바뀌지 않으므로 재검수 없이 바꾼다.
	 * 게시된 적 있는 카드는 지금 게시 중일 때만 바꾼다 — 수정 · 검수 중이면 바뀐 주제가 차에 가지 않아,
	 * 차 사본에 같은 주제의 카드가 둘 생길 수 있다.
	 */
	@Transactional
	public RuleCard assignTopic(Long id, String rawTopic) {
		RuleCard card = lock(id);
		ensureNotRetired(card);
		String topic = rawTopic == null || rawTopic.isBlank() ? null : rawTopic.strip();
		if (card.getVerifiedAt() != null && card.getStatus() != CardStatus.PUBLISHED
				&& !Objects.equals(card.getTopic(), topic)) {
			throw ApiException.conflict("TOPIC_CHANGE_NOT_SYNCED",
					"수정 · 검수 중인 카드의 주제는 바꿀 수 없습니다 — 게시 후 바꾸거나 회수하세요");
		}
		ensureTopicFree(topic, card.getId());
		card.changeTopic(topic, now());
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

	private static void ensureNotRetired(RuleCard card) {
		if (card.getStatus() == CardStatus.RETIRED) {
			throw invalidState(card, "회수된 카드는 바꿀 수 없습니다");
		}
	}

	private static ApiException invalidState(RuleCard card, String message) {
		return ApiException.conflict("INVALID_CARD_STATE", message + " (현재 " + card.getStatus() + ")");
	}

	public static ApiException cardNotFound(Long id) {
		return ApiException.notFound("CARD_NOT_FOUND", "카드가 없습니다: " + id);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

}
