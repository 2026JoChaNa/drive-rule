package com.jochana.driverule.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jochana.driverule.common.ApiException;
import com.jochana.driverule.review.ReviewDecision;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 카드 혼자 판단하는 수명주기 규칙 (결정 004 · 006 · 008 · 009). 목도 DB 도 없다. */
class RuleCardTest {

	static final LocalDateTime T1 = LocalDateTime.of(2026, 11, 12, 9, 0);
	static final LocalDateTime T2 = T1.plusHours(1);
	static final LocalDateTime T3 = T1.plusHours(2);

	static RuleCard draft(String topic) {
		return RuleCard.draft(CardServiceTest.content(topic).normalized(), null, null, T1);
	}

	/** 작성 → 검수 요청 → 승인 2건 = 게시. */
	static RuleCard published(String topic) {
		RuleCard card = draft(topic);
		card.submitForReview(false, T1);
		card.applyReview(ReviewDecision.APPROVE, 1, T1);
		card.applyReview(ReviewDecision.APPROVE, 2, T2);
		return card;
	}

	static void assertCode(Runnable call, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.code()).isEqualTo(code));
	}

	@Test
	void lifecycleFromDraftToRepublishedVersion() {
		RuleCard card = draft("right_turn");
		assertThat(card.getStatus()).isEqualTo(CardStatus.DRAFT);
		assertThat(card.getVersion()).isEqualTo(1);
		assertThat(card.wasPublished()).isFalse();

		card.submitForReview(false, T1);
		assertThat(card.getStatus()).isEqualTo(CardStatus.IN_REVIEW);

		card.applyReview(ReviewDecision.APPROVE, 1, T1);
		assertThat(card.getStatus()).as("승인 1건은 아직 게시가 아니다").isEqualTo(CardStatus.IN_REVIEW);

		card.applyReview(ReviewDecision.APPROVE, 2, T2);
		assertThat(card.getStatus()).isEqualTo(CardStatus.PUBLISHED);
		assertThat(card.getVerifiedAt()).isEqualTo(T2);

		card.edit(CardServiceTest.content("right_turn").normalized(), null, null, T3);
		assertThat(card.getStatus()).as("게시 카드도 고치면 DRAFT — 앱은 마지막 게시본을 쓴다").isEqualTo(CardStatus.DRAFT);
		assertThat(card.getVersion()).isEqualTo(1);
		assertThat(card.wasPublished()).isTrue();

		card.submitForReview(true, T3);
		assertThat(card.getVersion()).as("검수 기록이 있는 버전을 다시 올리면 다음 버전").isEqualTo(2);
	}

	@Test
	void rejectionReturnsToDraft() {
		RuleCard card = draft(null);
		card.submitForReview(false, T1);

		card.applyReview(ReviewDecision.REJECT, 0, T2);

		assertThat(card.getStatus()).isEqualTo(CardStatus.DRAFT);
		assertThat(card.getUpdatedAt()).isEqualTo(T2);
	}

	@Test
	void reviewsOnlyApplyInReview() {
		assertCode(() -> draft(null).applyReview(ReviewDecision.APPROVE, 2, T1), "INVALID_CARD_STATE");
		assertCode(() -> published(null).ensureReviewable(), "INVALID_CARD_STATE");
	}

	@Test
	void onlyDraftCanBeSubmitted() {
		RuleCard inReview = draft(null);
		inReview.submitForReview(false, T1);

		assertCode(() -> inReview.submitForReview(false, T2), "INVALID_CARD_STATE");
		assertCode(() -> published(null).submitForReview(false, T2), "INVALID_CARD_STATE");
	}

	@Test
	void retireClearsTopicAndIsFinal() {
		RuleCard card = published("right_turn");

		card.retire(T3);

		assertThat(card.getStatus()).isEqualTo(CardStatus.RETIRED);
		assertThat(card.getTopic()).isNull();
		assertCode(() -> card.retire(T3), "INVALID_CARD_STATE");
		assertCode(() -> card.edit(CardServiceTest.content(null).normalized(), null, null, T3), "INVALID_CARD_STATE");
		assertCode(() -> card.assignTopic("x", T3), "INVALID_CARD_STATE");
	}

	@Test
	void topicOfPublishedBeforeCardChangesOnlyWhilePublished() {
		RuleCard card = published("old");

		card.assignTopic("new", T3);
		assertThat(card.getTopic()).as("게시 중이면 바로 바뀌고 동기화된다").isEqualTo("new");

		card.edit(CardServiceTest.content("new").normalized(), null, null, T3);
		assertCode(() -> card.assignTopic(null, T3), "TOPIC_CHANGE_NOT_SYNCED");
		card.assignTopic("new", T3); // 같은 값은 바뀌는 것이 없으니 허용
		assertCode(() -> card.edit(CardServiceTest.content("other").normalized(), null, null, T3),
				"TOPIC_CHANGE_NOT_SYNCED");
	}

	@Test
	void neverPublishedCardTopicIsFree() {
		RuleCard card = draft("old");

		card.assignTopic("new", T2);
		card.edit(CardServiceTest.content("other").normalized(), null, null, T3);

		assertThat(card.getTopic()).isEqualTo("other");
	}

	@Test
	void blankTopicMeansNone() {
		assertThat(RuleCard.normalizeTopic("  ")).isNull();
		assertThat(RuleCard.normalizeTopic(null)).isNull();
		assertThat(RuleCard.normalizeTopic(" right_turn ")).isEqualTo("right_turn");
	}

}
