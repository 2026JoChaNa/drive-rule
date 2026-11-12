package com.jochana.driverule.card;

import java.util.List;
import java.util.Map;

/**
 * 조건 분기 카드의 결정 테이블, 형식 2 (결정 009 B — 002 를 개정).
 * <ul>
 * <li>카드 하나에 소표 여러 개. 소표 안은 UNIQUE(한 상황에 맞는 행 정확히 1개), 소표끼리는 모아서 함께 답한다</li>
 * <li>조건 정의(질문 · 선택지)는 카드에 두지 않고 {@link RuleCondition} 공통 목록을 참조한다</li>
 * </ul>
 * DB 에는 {@code rule_card.decision_json} 으로, 앱에는 그대로 전송한다.
 *
 * @param schema JSON 형식 버전 (현재 2)
 */
public record DecisionTable(Integer schema, List<SubTable> tables) {

	public static final int CURRENT_SCHEMA = 2;

	/**
	 * 판단 하나 (예: '교차로 진입 전', '돌아선 뒤 횡단보도').
	 *
	 * @param conditions 이 소표가 쓰는 공통 조건 키 — 순서가 되묻는 순서다
	 */
	public record SubTable(String key, String title, List<String> conditions, List<Row> rows) {
	}

	/**
	 * 소표의 행. {@code when} 에 없는 조건은 '상관없음'이다.
	 *
	 * @param text  화면 문장
	 * @param tts   읽어 주기 문장
	 * @param scene 앱 번들 그림 키 (선택)
	 */
	public record Row(Map<String, String> when, String text, String tts, String scene) {
	}

}
