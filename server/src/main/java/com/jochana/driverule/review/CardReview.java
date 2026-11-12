package com.jochana.driverule.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 교차 검수 1건 — "몇 번째 버전을 누가 승인/반려했나"만 남긴다 (결정 004 한계 참고). */
@Entity
@Table(name = "card_review")
public class CardReview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "card_id", nullable = false)
	private Long cardId;

	@Column(name = "reviewer_id", nullable = false)
	private Long reviewerId;

	@Column(name = "card_version", nullable = false)
	private int cardVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", nullable = false)
	private ReviewDecision decision;

	@Column(name = "reviewed_at", nullable = false)
	private LocalDateTime reviewedAt;

	protected CardReview() {
	}

	public CardReview(Long cardId, Long reviewerId, int cardVersion, ReviewDecision decision,
			LocalDateTime reviewedAt) {
		this.cardId = cardId;
		this.reviewerId = reviewerId;
		this.cardVersion = cardVersion;
		this.decision = decision;
		this.reviewedAt = reviewedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getCardId() {
		return cardId;
	}

	public Long getReviewerId() {
		return reviewerId;
	}

	public int getCardVersion() {
		return cardVersion;
	}

	public ReviewDecision getDecision() {
		return decision;
	}

	public LocalDateTime getReviewedAt() {
		return reviewedAt;
	}

}
