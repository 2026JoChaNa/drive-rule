package com.jochana.driverule.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 검수 요청 대기열 1건. 차에서 오지만 식별자(프로필 · 기기)와 질문 원문 · 위치는 담지 않는다 (결정 003 · 005 · 009 E).
 */
@Entity
@Table(name = "review_request")
public class ReviewRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "card_id")
	private Long cardId;

	@Column(name = "card_version")
	private Integer cardVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "request_type", nullable = false)
	private RequestType requestType;

	@Column(name = "topic", length = 40)
	private String topic;

	// 운전자가 답한 조건 {"crosswalk_light":"green"} — 공통 조건 키 · 값만 (검증 후 저장)
	@Column(name = "conditions_json", columnDefinition = "json")
	private String conditionsJson;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RequestStatus status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected ReviewRequest() {
	}

	private ReviewRequest(RequestType type, Long cardId, Integer cardVersion, String topic, String conditionsJson,
			LocalDateTime createdAt) {
		this.requestType = type;
		this.cardId = cardId;
		this.cardVersion = cardVersion;
		this.topic = topic;
		this.conditionsJson = conditionsJson;
		this.status = RequestStatus.OPEN;
		this.createdAt = createdAt;
	}

	public static ReviewRequest errorReport(Long cardId, int cardVersion, LocalDateTime now) {
		return new ReviewRequest(RequestType.ERROR_REPORT, cardId, cardVersion, null, null, now);
	}

	public static ReviewRequest answerHold(String topic, String conditionsJson, LocalDateTime now) {
		return new ReviewRequest(RequestType.ANSWER_HOLD, null, null, topic, conditionsJson, now);
	}

	public void changeStatus(RequestStatus status) {
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public Long getCardId() {
		return cardId;
	}

	public Integer getCardVersion() {
		return cardVersion;
	}

	public RequestType getRequestType() {
		return requestType;
	}

	public String getTopic() {
		return topic;
	}

	public String getConditionsJson() {
		return conditionsJson;
	}

	public RequestStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

}
