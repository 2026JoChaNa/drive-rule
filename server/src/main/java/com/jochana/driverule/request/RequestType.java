package com.jochana.driverule.request;

/** 검수 요청 유형 (결정 005 · 009 E). */
public enum RequestType {
	/** 근거 시트 '오류 신고' — card_id · card_version 필수 */
	ERROR_REPORT,
	/** Gleo 답변 보류 — 대표 카드가 없거나 보류 행에 걸림. topic 필수, 조건은 선택. 질문 원문은 받지 않는다 */
	ANSWER_HOLD
}
