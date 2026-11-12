package com.jochana.driverule.card;

/**
 * 카드가 누구에게 해당하는지 (기획 v6 2-3, 결정 009 C). 운전자 입력으로 거르지 않는다 (결정 001).
 */
public enum Audience {
	/** 일반 운전자 — 바뀐 규칙 목록 · 이벤트 안내 · Gleo 답변 */
	GENERAL,
	/** 일부 운전자(예: 재범자 조건부 면허) — 목록 · 이벤트에서 빼고 물으면 답한다 */
	LIMITED
}
