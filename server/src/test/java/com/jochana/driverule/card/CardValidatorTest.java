package com.jochana.driverule.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardValidatorTest {

	/** 기획 v6 카드 H — 이벤트 안내 단문, 두 장면 없음. */
	static CardContent eventCard() {
		return new CardContent(null, CardType.SHORT, ChangeKind.LAW, Audience.GENERAL, EventTag.SCHOOL_ZONE_ENTRY,
				null, null, "보호구역 신호등 없는 횡단보도 일시정지", null, null, null,
				"어린이보호구역입니다. 신호등 없는 횡단보도 앞에서는 사람이 없어도 먼저 멈춥니다.", "도로교통법 제27조", "원문",
				"승용차 범칙금 6만 원 · 벌점 10점", null, null, null);
	}

	static CardContent conditionalCard() {
		return new CardContent(null, CardType.CONDITIONAL, ChangeKind.LAW, Audience.GENERAL, null, null, "right_turn",
				"우회전할 때 멈추는 곳", null, null, null, "우회전 안내", "도로교통법 제27조 제1항", "원문", null, null, null,
				DecisionTableValidatorTest.rightTurn());
	}

	static CardContent with(CardContent c, ChangeKind kind, Audience audience, EventTag event, String sourceOrg,
			Long revisionId) {
		return new CardContent(revisionId, c.cardType(), kind, audience, event, sourceOrg, c.topic(), c.title(),
				c.dontText(), c.doText(), c.sceneKey(), c.ttsText(), c.legalBasis(), c.lawExcerpt(), c.penaltyText(),
				c.effectiveOn(), c.exceptions(), c.decision());
	}

	@Test
	void shortCardScenesAreBothOrNeither() {
		assertThat(CardValidator.validate(eventCard())).isEmpty();

		CardContent c = eventCard();
		CardContent half = new CardContent(null, CardType.SHORT, ChangeKind.LAW, Audience.GENERAL, null, null, null,
				c.title(), "술 마신 사람에게 차 키 건네기", null, null, c.ttsText(), c.legalBasis(), c.lawExcerpt(), null,
				null, null, DecisionTableValidatorTest.rightTurn());
		assertThat(CardValidator.validate(half)).containsExactlyInAnyOrder(
				"dontText, doText: 두 장면은 둘 다 쓰거나 둘 다 비워 주세요",
				"decision: 이 카드 유형에는 쓰지 않습니다 — 비워 주세요");
	}

	@Test
	void conditionalCardNeedsValidDecisionAndNoSceneFields() {
		assertThat(CardValidator.validate(conditionalCard())).isEmpty();

		CardContent c = conditionalCard();
		CardContent broken = new CardContent(null, CardType.CONDITIONAL, ChangeKind.LAW, Audience.GENERAL, null, null,
				null, c.title(), "x", null, "scene", c.ttsText(), c.legalBasis(), c.lawExcerpt(), null, null, null, null);
		assertThat(CardValidator.validate(broken))
				.contains("dontText: 이 카드 유형에는 쓰지 않습니다 — 비워 주세요",
						"sceneKey: 이 카드 유형에는 쓰지 않습니다 — 비워 주세요",
						"decision: 결정 테이블이 없습니다");
	}

	@Test
	void operationCardNeedsSourceOrgAndNoLawRevision() {
		CardContent ok = with(eventCard(), ChangeKind.OPERATION, Audience.GENERAL, EventTag.SCHOOL_ZONE_ENTRY,
				"경찰청 · 성남시", null);
		assertThat(CardValidator.validate(ok)).isEmpty();

		CardContent broken = with(eventCard(), ChangeKind.OPERATION, Audience.GENERAL, null, null, 7L);
		assertThat(CardValidator.validate(broken)).containsExactlyInAnyOrder(
				"sourceOrg: 운영 변경 카드는 출처 기관이 필수입니다",
				"revisionId: 운영 변경 카드는 법령 개정과 잇지 않습니다");
	}

	@Test
	void lawCardHasNoSourceOrg() {
		CardContent broken = with(eventCard(), ChangeKind.LAW, Audience.GENERAL, null, "경찰청", null);

		assertThat(CardValidator.validate(broken))
				.containsExactly("sourceOrg: 법령 개정 카드는 출처 기관을 쓰지 않습니다 — 비워 주세요");
	}

	@Test
	void limitedAudienceCardIsNotAnnouncedByEvent() {
		CardContent broken = with(eventCard(), ChangeKind.LAW, Audience.LIMITED, EventTag.SCHOOL_ZONE_ENTRY, null, null);

		assertThat(CardValidator.validate(broken))
				.containsExactly("eventTag: 일부 운전자 대상 카드는 이벤트로 안내하지 않습니다 — 비워 주세요");
	}

	@Test
	void normalizedTreatsBlankAsNull() {
		CardContent c = eventCard();
		CardContent blanks = new CardContent(null, CardType.SHORT, ChangeKind.LAW, Audience.GENERAL, null, " ", "  ",
				c.title(), "", " ", "", c.ttsText(), c.legalBasis(), c.lawExcerpt(), " ", null, "", null).normalized();

		assertThat(blanks.sourceOrg()).isNull();
		assertThat(blanks.topic()).isNull();
		assertThat(blanks.dontText()).isNull();
		assertThat(blanks.doText()).isNull();
		assertThat(blanks.sceneKey()).isNull();
		assertThat(blanks.penaltyText()).isNull();
		assertThat(blanks.exceptions()).isNull();
	}

}
