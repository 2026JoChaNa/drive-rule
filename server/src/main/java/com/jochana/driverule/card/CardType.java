package com.jochana.driverule.card;

/**
 * 카드의 문장 형식 (결정 005 · 009 H). 어떤 이벤트에 쓰이는지는 {@link EventTag} 가 따로 맡는다.
 * 유형별 필수 칸은 {@link CardValidator} 가 확인한다.
 */
public enum CardType {
	/** 단문 — 안내 문장 하나. 두 장면(dont_text · do_text)은 둘 다 있거나 둘 다 없다 */
	SHORT,
	/** 조건 분기형 — decision_json 결정 테이블 (결정 002 · 009 B) */
	CONDITIONAL,
	/** 고정 응답 */
	FIXED_RESPONSE
}
