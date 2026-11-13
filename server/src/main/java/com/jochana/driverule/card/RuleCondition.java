package com.jochana.driverule.card;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 공통 조건 목록 (결정 009 B-2). Gleo App Schema 의 {@code ask_rule} 파라미터와 1:1 — 이름과 값을 바꾸면
 * 앱 schema.json 도 바꿔 Pleos 심사를 다시 받아야 한다.
 * <p>
 * <b>초안</b>: 기획 v6 2-4 에서 뽑았다. App Schema 제출(11/16 주) 전 기획 확인 대기.
 * 질문 · 선택지 문구는 앱의 되묻기 화면(Figma 6)에 쓰도록 동기화로 함께 보낸다.
 */
public enum RuleCondition {

	RIGHT_TURN_TRAFFIC_LIGHT("right_turn_traffic_light", "우회전 신호등이 있었나요?",
			List.of(new Option("yes", "있었어요"), new Option("no", "없었어요"))),
	FRONT_TRAFFIC_LIGHT("front_traffic_light", "전방 신호가 빨간불이었나요?",
			List.of(new Option("red", "빨간불이었어요"), new Option("green", "초록불이었어요"))),
	CROSSWALK_LIGHT("crosswalk_light", "돌아선 뒤 횡단보도 보행 신호는 무슨 색이었나요?",
			List.of(new Option("green", "초록불이었어요"), new Option("red", "빨간불이었어요"))),
	PEDESTRIAN_PRESENT("pedestrian_present", "건너거나 건너려는 사람이 있었나요?",
			List.of(new Option("yes", "있었어요"), new Option("no", "없었어요")));

	public record Option(String key, String label) {
	}

	private final String key;
	private final String question;
	private final List<Option> options;

	RuleCondition(String key, String question, List<Option> options) {
		this.key = key;
		this.question = question;
		this.options = options;
	}

	public static Optional<RuleCondition> byKey(String key) {
		return Arrays.stream(values()).filter(c -> c.key.equals(key)).findFirst();
	}

	public boolean hasOption(String optionKey) {
		return options.stream().anyMatch(o -> o.key().equals(optionKey));
	}

	public List<String> optionKeys() {
		return options.stream().map(Option::key).toList();
	}

	public String key() {
		return key;
	}

	public String question() {
		return question;
	}

	public List<Option> options() {
		return options;
	}

}
