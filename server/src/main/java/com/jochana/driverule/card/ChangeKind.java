package com.jochana.driverule.card;

/** 카드 종류 (기획 v6 1-2, 결정 009 H). */
public enum ChangeKind {
	/** 법령 개정 — 공포 시 1회 + 시행 후 1회 안내 */
	LAW,
	/** 운영 변경(법령 아님, 예: 스쿨존 시간제 속도) — 출처 기관 필수, "현장 표지판 우선" 표시 */
	OPERATION
}
