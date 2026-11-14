package com.jochana.driverule.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jochana.driverule.common.ApiException;
import com.jochana.driverule.law.LawRevision;
import com.jochana.driverule.law.LawRevisionRepository;
import com.jochana.driverule.law.TriageStatus;
import com.jochana.driverule.review.CardReview;
import com.jochana.driverule.review.CardReviewRepository;
import com.jochana.driverule.review.ReviewDecision;
import com.jochana.driverule.review.ReviewerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카드 수명주기 (결정 004 · 006 · 008 · 009) — DB 없이 서비스 규칙을 고정한다.
 * 리포지토리는 목(mock)이다. 저장 · 잠금 · 제약은 CardApiFlowTests(MySQL) 가 맡는다.
 */
class CardServiceTest {

	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 12, 9, 0);
	static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);

	RuleCardRepository cards;
	LawRevisionRepository revisions;
	ReviewerRepository reviewers;
	CardReviewRepository reviews;
	CardService service;

	@BeforeEach
	void setUp() {
		cards = mock(RuleCardRepository.class);
		revisions = mock(LawRevisionRepository.class);
		reviewers = mock(ReviewerRepository.class);
		reviews = mock(CardReviewRepository.class);
		Clock clock = Clock.fixed(ZonedDateTime.of(NOW, SEOUL).toInstant(), SEOUL);
		service = new CardService(cards, revisions, reviewers, reviews,
				new DecisionTableJson(JsonMapper.builder().build()), clock);
		when(cards.findByTopic(any())).thenReturn(Optional.empty());
		when(cards.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	// ---------- 준비 ----------

	static CardContent content(String topic) {
		CardContent c = CardValidatorTest.eventCard();
		return new CardContent(c.revisionId(), c.cardType(), c.changeKind(), c.audience(), c.eventTag(),
				c.sourceOrg(), topic, c.title(), c.dontText(), c.doText(), c.sceneKey(), c.ttsText(), c.legalBasis(),
				c.lawExcerpt(), c.penaltyText(), c.effectiveOn(), c.exceptions(), c.decision());
	}

	/** 저장소에 있는 카드 — 상태 · 버전 · 주제 · 게시 이력을 지정한다. */
	RuleCard stored(long id, CardStatus status, int version, String topic, boolean publishedBefore) {
		// 상태는 수명주기 메서드로만 바뀌므로, 저장소에서 읽어 온 상태를 흉내 내려고 필드를 직접 채운다
		RuleCard card = RuleCard.draft(content(topic).normalized(), null, null, T0);
		ReflectionTestUtils.setField(card, "version", version);
		ReflectionTestUtils.setField(card, "verifiedAt", publishedBefore ? T0 : null);
		ReflectionTestUtils.setField(card, "status", status);
		ReflectionTestUtils.setField(card, "id", id);
		when(cards.findForUpdate(id)).thenReturn(Optional.of(card));
		when(cards.findById(id)).thenReturn(Optional.of(card));
		return card;
	}

	static void assertRejected(Runnable call, HttpStatus status, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.status()).isEqualTo(status);
			assertThat(e.code()).isEqualTo(code);
		});
	}

	// ---------- 규칙 ----------

	@Nested
	class Create {

		@Test
		void startsAsDraftVersionOne() {
			RuleCard card = service.create(content("right_turn"));

			assertThat(card.getStatus()).isEqualTo(CardStatus.DRAFT);
			assertThat(card.getVersion()).isEqualTo(1);
			assertThat(card.getVerifiedAt()).isNull();
			assertThat(card.getCreatedAt()).isEqualTo(NOW);
			assertThat(card.getUpdatedAt()).isEqualTo(NOW);
		}

		@Test
		void rejectsCardBreakingTypeRules() {
			CardContent c = content(null);
			CardContent half = new CardContent(null, c.cardType(), c.changeKind(), c.audience(), c.eventTag(), null,
					null, c.title(), "한 장면만", null, null, c.ttsText(), c.legalBasis(), c.lawExcerpt(), null, null,
					null, null);

			assertRejected(() -> service.create(half), HttpStatus.UNPROCESSABLE_CONTENT, "CARD_INVALID");
		}

		@Test
		void rejectsTopicHeldByAnotherCard() {
			RuleCard other = stored(9, CardStatus.PUBLISHED, 1, "right_turn", true);
			when(cards.findByTopic("right_turn")).thenReturn(Optional.of(other));

			assertRejected(() -> service.create(content("right_turn")), HttpStatus.CONFLICT, "TOPIC_TAKEN");
		}

		@Test
		void rejectsRevisionClassifiedNotApplicable() {
			LawRevision revision = new LawRevision("도로교통법", "제1호", "K", LocalDate.of(2026, 1, 1), T0);
			revision.changeTriageStatus(TriageStatus.NOT_APPLICABLE);
			when(revisions.findById(5L)).thenReturn(Optional.of(revision));
			CardContent c = content(null);
			CardContent linked = new CardContent(5L, c.cardType(), c.changeKind(), c.audience(), c.eventTag(), null,
					null, c.title(), null, null, null, c.ttsText(), c.legalBasis(), c.lawExcerpt(), null, null, null,
					null);

			assertRejected(() -> service.create(linked), HttpStatus.CONFLICT, "LAW_REVISION_NOT_APPLICABLE");
		}

	}

	@Nested
	class Update {

		@Test
		void anyEditReturnsToDraftKeepingVersion() {
			stored(1, CardStatus.PUBLISHED, 3, null, true);

			RuleCard card = service.update(1L, content(null));

			assertThat(card.getStatus()).isEqualTo(CardStatus.DRAFT);
			assertThat(card.getVersion()).isEqualTo(3);
			assertThat(card.getUpdatedAt()).isEqualTo(NOW);
		}

		@Test
		void retiredCardCannotBeEdited() {
			stored(1, CardStatus.RETIRED, 1, null, true);

			assertRejected(() -> service.update(1L, content(null)), HttpStatus.CONFLICT, "INVALID_CARD_STATE");
		}

		@Test
		void publishedBeforeCardCannotChangeTopicByEdit() {
			stored(1, CardStatus.PUBLISHED, 1, "right_turn", true);

			assertRejected(() -> service.update(1L, content("other")), HttpStatus.CONFLICT, "TOPIC_CHANGE_NOT_SYNCED");
		}

		@Test
		void neverPublishedCardCanChangeTopicByEdit() {
			stored(1, CardStatus.DRAFT, 1, "right_turn", false);

			assertThat(service.update(1L, content("other")).getTopic()).isEqualTo("other");
		}

	}

	@Nested
	class Submit {

		@Test
		void draftGoesToReviewKeepingVersionWhenNotYetReviewed() {
			stored(1, CardStatus.DRAFT, 2, null, false);
			when(reviews.existsByCardIdAndCardVersion(1L, 2)).thenReturn(false);

			RuleCard card = service.submitForReview(1L);

			assertThat(card.getStatus()).isEqualTo(CardStatus.IN_REVIEW);
			assertThat(card.getVersion()).isEqualTo(2);
		}

		@Test
		void versionAlreadyReviewedBecomesNextVersion() {
			stored(1, CardStatus.DRAFT, 2, null, true);
			when(reviews.existsByCardIdAndCardVersion(1L, 2)).thenReturn(true);

			assertThat(service.submitForReview(1L).getVersion()).isEqualTo(3);
		}

		@Test
		void onlyDraftCanBeSubmitted() {
			for (CardStatus status : new CardStatus[] { CardStatus.IN_REVIEW, CardStatus.PUBLISHED, CardStatus.RETIRED }) {
				stored(1, status, 1, null, false);

				assertRejected(() -> service.submitForReview(1L), HttpStatus.CONFLICT, "INVALID_CARD_STATE");
			}
		}

	}

	@Nested
	class Review {

		@BeforeEach
		void reviewerExists() {
			when(reviewers.existsById(anyLong())).thenReturn(true);
		}

		@Test
		void firstApprovalKeepsReviewing() {
			stored(1, CardStatus.IN_REVIEW, 1, null, false);
			when(reviews.countByCardIdAndCardVersionAndDecision(1L, 1, ReviewDecision.APPROVE)).thenReturn(1L);

			RuleCard card = service.review(1L, 7L, ReviewDecision.APPROVE);

			assertThat(card.getStatus()).isEqualTo(CardStatus.IN_REVIEW);
			assertThat(card.getVerifiedAt()).isNull();
			verify(reviews).save(any(CardReview.class));
		}

		@Test
		void secondApprovalPublishesAndStampsVerification() {
			stored(1, CardStatus.IN_REVIEW, 1, null, false);
			when(reviews.countByCardIdAndCardVersionAndDecision(1L, 1, ReviewDecision.APPROVE)).thenReturn(2L);

			RuleCard card = service.review(1L, 8L, ReviewDecision.APPROVE);

			assertThat(card.getStatus()).isEqualTo(CardStatus.PUBLISHED);
			assertThat(card.getVerifiedAt()).isEqualTo(NOW);
		}

		@Test
		void singleReviewerConfigurationPublishesAfterOneApproval() {
			Clock clock = Clock.fixed(ZonedDateTime.of(NOW, SEOUL).toInstant(), SEOUL);
			CardService singleReviewerService = new CardService(cards, revisions, reviewers, reviews,
					new DecisionTableJson(JsonMapper.builder().build()), clock, 1);
			stored(1, CardStatus.IN_REVIEW, 1, null, false);
			when(reviews.countByCardIdAndCardVersionAndDecision(1L, 1, ReviewDecision.APPROVE)).thenReturn(1L);

			RuleCard card = singleReviewerService.review(1L, 7L, ReviewDecision.APPROVE);

			assertThat(card.getStatus()).isEqualTo(CardStatus.PUBLISHED);
			assertThat(card.getVerifiedAt()).isEqualTo(NOW);
			verify(reviews).save(any(CardReview.class));
		}

		@Test
		void rejectionReturnsToDraft() {
			stored(1, CardStatus.IN_REVIEW, 1, null, false);

			assertThat(service.review(1L, 7L, ReviewDecision.REJECT).getStatus()).isEqualTo(CardStatus.DRAFT);
		}

		@Test
		void onlyCardInReviewCanBeReviewed() {
			stored(1, CardStatus.DRAFT, 1, null, false);

			assertRejected(() -> service.review(1L, 7L, ReviewDecision.APPROVE), HttpStatus.CONFLICT,
					"INVALID_CARD_STATE");
			verify(reviews, never()).save(any());
		}

		@Test
		void unknownReviewerIsNotFound() {
			stored(1, CardStatus.IN_REVIEW, 1, null, false);
			when(reviewers.existsById(99L)).thenReturn(false);

			assertRejected(() -> service.review(1L, 99L, ReviewDecision.APPROVE), HttpStatus.NOT_FOUND,
					"REVIEWER_NOT_FOUND");
		}

		@Test
		void sameReviewerCannotReviewSameVersionTwice() {
			stored(1, CardStatus.IN_REVIEW, 1, null, false);
			when(reviews.existsByCardIdAndCardVersionAndReviewerId(eq(1L), anyInt(), eq(7L))).thenReturn(true);

			assertRejected(() -> service.review(1L, 7L, ReviewDecision.APPROVE), HttpStatus.CONFLICT,
					"ALREADY_REVIEWED");
			verify(reviews, never()).save(any());
		}

	}

	@Nested
	class Retire {

		@Test
		void retiringClearsTopic() {
			stored(1, CardStatus.PUBLISHED, 1, "right_turn", true);

			RuleCard card = service.retire(1L);

			assertThat(card.getStatus()).isEqualTo(CardStatus.RETIRED);
			assertThat(card.getTopic()).isNull();
			assertThat(card.getUpdatedAt()).isEqualTo(NOW);
		}

		@Test
		void anyNonRetiredStateCanRetire() {
			for (CardStatus status : new CardStatus[] { CardStatus.DRAFT, CardStatus.IN_REVIEW }) {
				stored(1, status, 1, null, true);

				assertThat(service.retire(1L).getStatus()).isEqualTo(CardStatus.RETIRED);
			}
		}

		@Test
		void retiredIsFinal() {
			stored(1, CardStatus.RETIRED, 1, null, true);

			assertRejected(() -> service.retire(1L), HttpStatus.CONFLICT, "INVALID_CARD_STATE");
		}

	}

	@Nested
	class AssignTopic {

		@Test
		void publishedCardTopicChangesAndIsResent() {
			stored(1, CardStatus.PUBLISHED, 1, "old", true);

			RuleCard card = service.assignTopic(1L, " right_turn ");

			assertThat(card.getTopic()).isEqualTo("right_turn");
			assertThat(card.getUpdatedAt()).isEqualTo(NOW);
		}

		@Test
		void blankClearsTopic() {
			stored(1, CardStatus.PUBLISHED, 1, "old", true);

			assertThat(service.assignTopic(1L, "  ").getTopic()).isNull();
		}

		@Test
		void publishedBeforeButNowEditedCardIsLocked() {
			stored(1, CardStatus.DRAFT, 1, "old", true);

			assertRejected(() -> service.assignTopic(1L, null), HttpStatus.CONFLICT, "TOPIC_CHANGE_NOT_SYNCED");
		}

		@Test
		void sameTopicOnLockedCardIsAllowed() {
			stored(1, CardStatus.IN_REVIEW, 1, "old", true);

			assertThat(service.assignTopic(1L, "old").getTopic()).isEqualTo("old");
		}

		@Test
		void neverPublishedDraftCanChangeTopic() {
			stored(1, CardStatus.DRAFT, 1, null, false);

			assertThat(service.assignTopic(1L, "right_turn").getTopic()).isEqualTo("right_turn");
		}

		@Test
		void retiredCardCannotGetTopic() {
			stored(1, CardStatus.RETIRED, 1, null, true);

			assertRejected(() -> service.assignTopic(1L, "x"), HttpStatus.CONFLICT, "INVALID_CARD_STATE");
		}

		@Test
		void topicHeldByAnotherCardIsRejected() {
			stored(1, CardStatus.PUBLISHED, 1, null, true);
			RuleCard other = stored(2, CardStatus.PUBLISHED, 1, "right_turn", true);
			when(cards.findByTopic("right_turn")).thenReturn(Optional.of(other));

			assertRejected(() -> service.assignTopic(1L, "right_turn"), HttpStatus.CONFLICT, "TOPIC_TAKEN");
		}

	}

	@Test
	void unknownCardIsNotFound() {
		when(cards.findForUpdate(404L)).thenReturn(Optional.empty());

		assertRejected(() -> service.retire(404L), HttpStatus.NOT_FOUND, "CARD_NOT_FOUND");
	}

}
