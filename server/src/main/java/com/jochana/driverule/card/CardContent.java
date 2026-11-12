package com.jochana.driverule.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * CMS 가 카드를 만들거나 고칠 때 보내는 내용 전체. 칸 사이의 규칙은 {@link CardValidator} 가 본다.
 * {@code topic} 은 Gleo 대표 카드에만 붙인다 (결정 006).
 */
public record CardContent(
		Long revisionId,
		@NotNull CardType cardType,
		@NotNull ChangeKind changeKind,
		@NotNull Audience audience,
		EventTag eventTag,
		@Size(max = 100) String sourceOrg,
		@Size(max = 40) String topic,
		@NotBlank @Size(max = 100) String title,
		@Size(max = 300) String dontText,
		@Size(max = 300) String doText,
		@Size(max = 40) String sceneKey,
		@NotBlank @Size(max = 500) String ttsText,
		@NotBlank @Size(max = 200) String legalBasis,
		// MySQL TEXT 는 65,535 바이트 — 한글(utf8mb4 3바이트) 기준 약 2만 자
		@NotBlank @Size(max = 20000) String lawExcerpt,
		@Size(max = 300) String penaltyText,
		LocalDate effectiveOn,
		@Size(max = 20000) String exceptions,
		DecisionTable decision) {

	/** 빈 문자열은 NULL 로 본다 — '값 없음'을 한 가지로만 저장하려고. */
	public CardContent normalized() {
		return new CardContent(revisionId, cardType, changeKind, audience, eventTag, blankToNull(sourceOrg),
				blankToNull(topic), title, blankToNull(dontText), blankToNull(doText), blankToNull(sceneKey), ttsText,
				legalBasis, lawExcerpt, blankToNull(penaltyText), effectiveOn, blankToNull(exceptions), decision);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

}
