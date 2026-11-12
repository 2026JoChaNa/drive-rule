package com.jochana.driverule.card;

import com.jochana.driverule.common.ApiException;
import com.jochana.driverule.law.LawRevision;
import com.jochana.driverule.review.ReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 검수 카드 1장 = 1행. 게시본과 작업본을 따로 두지 않는다 (결정 004).
 * 상태는 수명주기 메서드(작성 · 수정 · 검수 요청 · 검수 반영 · 회수 · 대표 주제)로만 바뀐다.
 * 조회 · 잠금 · 저장과 다른 데이터가 필요한 확인은 {@link CardService} 가 한다.
 */
@Entity
@Table(name = "rule_card")
public class RuleCard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revision_id")
	private LawRevision revision;

	@Enumerated(EnumType.STRING)
	@Column(name = "card_type", nullable = false)
	private CardType cardType;

	@Enumerated(EnumType.STRING)
	@Column(name = "change_kind", nullable = false)
	private ChangeKind changeKind;

	@Enumerated(EnumType.STRING)
	@Column(name = "audience", nullable = false)
	private Audience audience;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_tag")
	private EventTag eventTag;

	@Column(name = "source_org", length = 100)
	private String sourceOrg;

	@Column(name = "topic", length = 40, unique = true)
	private String topic;

	@Column(name = "title", nullable = false, length = 100)
	private String title;

	@Column(name = "dont_text", length = 300)
	private String dontText;

	@Column(name = "do_text", length = 300)
	private String doText;

	@Column(name = "scene_key", length = 40)
	private String sceneKey;

	@Column(name = "tts_text", nullable = false, length = 500)
	private String ttsText;

	@Column(name = "legal_basis", nullable = false, length = 200)
	private String legalBasis;

	@Column(name = "law_excerpt", nullable = false, columnDefinition = "text")
	private String lawExcerpt;

	@Column(name = "penalty_text", length = 300)
	private String penaltyText;

	@Column(name = "effective_on")
	private LocalDate effectiveOn;

	@Column(name = "exceptions", columnDefinition = "text")
	private String exceptions;

	// JSON 문자열 그대로 둔다 — 형식 검사는 DecisionTableValidator, 변환은 DecisionTableJson
	@Column(name = "decision_json", columnDefinition = "json")
	private String decisionJson;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private CardStatus status;

	@Column(name = "version", nullable = false)
	private int version;

	@Column(name = "verified_at")
	private LocalDateTime verifiedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected RuleCard() {
	}

	private RuleCard(LocalDateTime now) {
		this.status = CardStatus.DRAFT;
		this.version = 1;
		this.createdAt = now;
		this.updatedAt = now;
	}

	// ===================== 수명주기 (결정 004 · 006 · 008 · 009) =====================
	// 카드 혼자 판단하는 규칙은 여기서 지킨다. 다른 데이터가 필요한 규칙(검수 기록 · 다른 카드의 주제 · 법령 개정)은
	// CardService 가 조회해 인자로 넘긴다 — 검수 기록은 카드와 다른 집합(card_review)이라 엔티티가 직접 읽지 않는다.

	/** 작성 — DRAFT · version 1 로 시작한다. 내용은 검증된 것({@link CardValidator})이어야 한다. */
	static RuleCard draft(CardContent content, LawRevision revision, String decisionJson, LocalDateTime now) {
		RuleCard card = new RuleCard(now);
		card.applyContent(content, revision, decisionJson);
		return card;
	}

	/**
	 * 수정 — 어느 상태든 DRAFT 로 돌아간다. 앱은 마지막으로 받은 게시본을 계속 쓴다 (004).
	 * 게시된 적 있는 카드는 여기서 주제를 바꾸지 못한다 — DRAFT 라 동기화되지 않아 차 사본에 옛 주제가 남는다.
	 */
	void edit(CardContent content, LawRevision revision, String decisionJson, LocalDateTime now) {
		ensureNotRetired();
		if (wasPublished() && !Objects.equals(topic, content.topic())) {
			throw ApiException.conflict("TOPIC_CHANGE_NOT_SYNCED",
					"게시된 적 있는 카드의 주제는 카드가 게시 중일 때 대표 주제 지정으로만 바꿀 수 있습니다");
		}
		applyContent(content, revision, decisionJson);
		changeStatus(CardStatus.DRAFT, now);
	}

	/**
	 * 검수 요청 — DRAFT 만. 이 버전에 이미 검수 기록이 있으면 새 내용 묶음으로 보고 버전을 올린다
	 * (card_review UQ(card, version, reviewer) — 반려한 검수자가 고친 내용을 다시 볼 수 있게).
	 *
	 * @param versionAlreadyReviewed 이 버전 번호에 검수 기록이 있는지 (card_review)
	 */
	void submitForReview(boolean versionAlreadyReviewed, LocalDateTime now) {
		if (status != CardStatus.DRAFT) {
			throw invalidState("검수 요청은 DRAFT 카드만 할 수 있습니다");
		}
		if (versionAlreadyReviewed) {
			version++;
		}
		changeStatus(CardStatus.IN_REVIEW, now);
	}

	/** 검수를 받을 수 있는지 — 검수 기록을 저장하기 전에 확인한다. */
	void ensureReviewable() {
		if (status != CardStatus.IN_REVIEW) {
			throw invalidState("IN_REVIEW 카드만 검수할 수 있습니다");
		}
	}

	/**
	 * 검수 결과 반영 — 반려 1건이면 DRAFT, 서로 다른 검수자 승인이 {@value #REQUIRED_APPROVALS}건이면 게시.
	 *
	 * @param approvalsAtVersion 방금 저장한 검수를 포함한, 이 버전의 승인 수
	 */
	void applyReview(ReviewDecision decision, long approvalsAtVersion, LocalDateTime now) {
		ensureReviewable();
		if (decision == ReviewDecision.REJECT) {
			changeStatus(CardStatus.DRAFT, now);
		}
		else if (approvalsAtVersion >= REQUIRED_APPROVALS) {
			verifiedAt = now;
			changeStatus(CardStatus.PUBLISHED, now);
		}
	}

	/** 회수 — 끝 상태. 주제를 비워 다른 카드를 대표로 지정할 수 있게 한다 (006). */
	void retire(LocalDateTime now) {
		ensureNotRetired();
		topic = null;
		changeStatus(CardStatus.RETIRED, now);
	}

	/**
	 * 대표 주제 지정 · 해제 (006). 문장이 바뀌지 않으므로 재검수 없이 바꾼다.
	 * 게시된 적 있는 카드는 지금 게시 중일 때만 바꾼다 — 그래야 바뀐 주제가 동기화로 차에 간다.
	 *
	 * @param newTopic {@link #normalizeTopic} 을 거친 값
	 */
	void assignTopic(String newTopic, LocalDateTime now) {
		ensureNotRetired();
		if (wasPublished() && status != CardStatus.PUBLISHED && !Objects.equals(topic, newTopic)) {
			throw ApiException.conflict("TOPIC_CHANGE_NOT_SYNCED",
					"수정 · 검수 중인 카드의 주제는 바꿀 수 없습니다 — 게시 후 바꾸거나 회수하세요");
		}
		topic = newTopic;
		updatedAt = now;
	}

	/** 빈 주제는 '주제 없음' 하나로 — 공백만 있는 주제가 UQ 를 차지하지 않게. */
	static String normalizeTopic(String raw) {
		return raw == null || raw.isBlank() ? null : raw.strip();
	}

	/** 한 번이라도 게시됐는지 — 차에 사본이 있을 수 있다. */
	boolean wasPublished() {
		return verifiedAt != null;
	}

	static final int REQUIRED_APPROVALS = 2;

	private void ensureNotRetired() {
		if (status == CardStatus.RETIRED) {
			throw invalidState("회수된 카드는 바꿀 수 없습니다");
		}
	}

	private ApiException invalidState(String message) {
		return ApiException.conflict("INVALID_CARD_STATE", message + " (현재 " + status + ")");
	}

	private void applyContent(CardContent c, LawRevision revision, String decisionJson) {
		this.revision = revision;
		this.cardType = c.cardType();
		this.changeKind = c.changeKind();
		this.audience = c.audience();
		this.eventTag = c.eventTag();
		this.sourceOrg = c.sourceOrg();
		this.topic = c.topic();
		this.title = c.title();
		this.dontText = c.dontText();
		this.doText = c.doText();
		this.sceneKey = c.sceneKey();
		this.ttsText = c.ttsText();
		this.legalBasis = c.legalBasis();
		this.lawExcerpt = c.lawExcerpt();
		this.penaltyText = c.penaltyText();
		this.effectiveOn = c.effectiveOn();
		this.exceptions = c.exceptions();
		this.decisionJson = decisionJson;
	}

	private void changeStatus(CardStatus status, LocalDateTime now) {
		this.status = status;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public LawRevision getRevision() {
		return revision;
	}

	public CardType getCardType() {
		return cardType;
	}

	public ChangeKind getChangeKind() {
		return changeKind;
	}

	public Audience getAudience() {
		return audience;
	}

	public EventTag getEventTag() {
		return eventTag;
	}

	public String getSourceOrg() {
		return sourceOrg;
	}

	public String getTopic() {
		return topic;
	}

	public String getTitle() {
		return title;
	}

	public String getDontText() {
		return dontText;
	}

	public String getDoText() {
		return doText;
	}

	public String getSceneKey() {
		return sceneKey;
	}

	public String getTtsText() {
		return ttsText;
	}

	public String getLegalBasis() {
		return legalBasis;
	}

	public String getLawExcerpt() {
		return lawExcerpt;
	}

	public String getPenaltyText() {
		return penaltyText;
	}

	public LocalDate getEffectiveOn() {
		return effectiveOn;
	}

	public String getExceptions() {
		return exceptions;
	}

	public String getDecisionJson() {
		return decisionJson;
	}

	public CardStatus getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public LocalDateTime getVerifiedAt() {
		return verifiedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
