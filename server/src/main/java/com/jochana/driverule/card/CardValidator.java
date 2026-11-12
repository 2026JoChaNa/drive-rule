package com.jochana.driverule.card;

import java.util.ArrayList;
import java.util.List;

/**
 * 카드 칸 사이의 규칙. DB NN 이 아니라 여기서 보장한다.
 * <p>
 * 문장 형식 (결정 005 · 009 H)
 * <ul>
 * <li>단문: 두 장면(dont_text · do_text)은 둘 다 있거나 둘 다 없음, 결정 테이블 없음</li>
 * <li>조건 분기형: 결정 테이블 필수(009 B 검증 포함), 두 장면 칸(dont_text · do_text · scene_key)은 비움</li>
 * <li>고정 응답: 결정 테이블 없음</li>
 * </ul>
 * 카드 종류 · 대상 (결정 009 C · H)
 * <ul>
 * <li>운영 변경: 출처 기관 필수, 법령 개정과 잇지 않음</li>
 * <li>법령 개정: 출처 기관 없음 (출처는 법령 개정 · 근거 조문)</li>
 * <li>일부 운전자 대상: 이벤트 안내를 하지 않음</li>
 * </ul>
 */
public final class CardValidator {

	private CardValidator() {
	}

	public static List<String> validate(CardContent c) {
		List<String> errors = new ArrayList<>();
		switch (c.cardType()) {
			case SHORT -> {
				if ((c.dontText() == null) != (c.doText() == null)) {
					errors.add("dontText, doText: 두 장면은 둘 다 쓰거나 둘 다 비워 주세요");
				}
				requireAbsent(errors, "decision", c.decision());
			}
			case CONDITIONAL -> {
				requireAbsent(errors, "dontText", c.dontText());
				requireAbsent(errors, "doText", c.doText());
				requireAbsent(errors, "sceneKey", c.sceneKey());
				errors.addAll(DecisionTableValidator.validate(c.decision()));
			}
			case FIXED_RESPONSE -> requireAbsent(errors, "decision", c.decision());
		}
		switch (c.changeKind()) {
			case OPERATION -> {
				if (c.sourceOrg() == null || c.sourceOrg().isBlank()) {
					errors.add("sourceOrg: 운영 변경 카드는 출처 기관이 필수입니다");
				}
				if (c.revisionId() != null) {
					errors.add("revisionId: 운영 변경 카드는 법령 개정과 잇지 않습니다");
				}
			}
			case LAW -> requireAbsent(errors, "sourceOrg", c.sourceOrg(), "법령 개정 카드는 출처 기관을 쓰지 않습니다");
		}
		if (c.audience() == Audience.LIMITED && c.eventTag() != null) {
			errors.add("eventTag: 일부 운전자 대상 카드는 이벤트로 안내하지 않습니다 — 비워 주세요");
		}
		return errors;
	}

	private static void requireAbsent(List<String> errors, String field, Object value) {
		requireAbsent(errors, field, value, "이 카드 유형에는 쓰지 않습니다");
	}

	private static void requireAbsent(List<String> errors, String field, Object value, String reason) {
		if (value != null) {
			errors.add(field + ": " + reason + " — 비워 주세요");
		}
	}

}
