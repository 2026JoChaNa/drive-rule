package com.jochana.driverule.card;

import com.jochana.driverule.law.LawRevision;
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

/**
 * 검수 카드 1장 = 1행. 게시본과 작업본을 따로 두지 않는다 (결정 004).
 * 상태 전이는 {@link CardService} 만 한다.
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

	RuleCard(LocalDateTime now) {
		this.status = CardStatus.DRAFT;
		this.version = 1;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void applyContent(CardContent c, LawRevision revision, String decisionJson) {
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

	void changeStatus(CardStatus status, LocalDateTime now) {
		this.status = status;
		this.updatedAt = now;
	}

	void bumpVersion() {
		this.version++;
	}

	void markVerified(LocalDateTime now) {
		this.verifiedAt = now;
	}

	void changeTopic(String topic, LocalDateTime now) {
		this.topic = topic;
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
