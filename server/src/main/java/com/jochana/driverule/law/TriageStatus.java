package com.jochana.driverule.law;

/** 법령 개정 분류 상태 — 일반 운전자 해당분만 카드로 만든다 (기능 ⑦, 결정 001). */
public enum TriageStatus {
	/** 아직 분류 전 */
	PENDING,
	/** 일반 운전자 해당 — 카드 작성 대상 */
	APPLICABLE,
	/** 해당 없음 */
	NOT_APPLICABLE
}
