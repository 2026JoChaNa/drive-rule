package com.jochana.driverule.card;

/**
 * 카드를 미리 알려 줄 주행 이벤트 (기획 v6 1-3, 결정 009 A). 카드당 하나, 없으면 Gleo 답변 전용.
 * 이벤트 판정은 차가 한다 — 서버는 태그만 붙여 보낸다.
 */
public enum EventTag {
	/** ① 어린이보호구역 진입 */
	SCHOOL_ZONE_ENTRY,
	/** ② 주정차 · 목적지가 보호구역 근처 */
	PARKING_DESTINATION,
	/** ② 주정차 · 고속도로에서 기어 P */
	PARKING_HIGHWAY,
	/** ③ 우회전 후 횡단보도 */
	RIGHT_TURN_CROSSWALK
}
