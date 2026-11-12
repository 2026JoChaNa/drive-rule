package com.jochana.driverule.card;

/** 카드 수명주기 (결정 004). */
public enum CardStatus {
	/** 작성·수정 중 — 동기화하지 않음 */
	DRAFT,
	/** 2인 교차 검수 중 — 동기화하지 않음 */
	IN_REVIEW,
	/** 게시 — 내용 + version 을 동기화 */
	PUBLISHED,
	/** 회수 — 카드 ID 만 동기화. 되돌릴 수 없다 */
	RETIRED
}
