package com.jochana.driverule.card;

import com.jochana.driverule.review.CardReview;
import com.jochana.driverule.review.ReviewDecision;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CMS · 검수 카드", description = "작성 → 검수 요청 → 2인 교차 검수 → 게시 / 회수 (결정 004)")
@RestController
@RequestMapping("/api/v1/admin/cards")
public class CardAdminController {

	private final CardService service;
	private final DecisionTableJson decisionJson;

	public CardAdminController(CardService service, DecisionTableJson decisionJson) {
		this.service = service;
		this.decisionJson = decisionJson;
	}

	public record ReviewBody(@NotNull Long reviewerId, @NotNull ReviewDecision decision) {
	}

	public record TopicRequest(@Size(max = 40) String topic) {
	}

	public record CardResponse(Long id, Long revisionId, CardType cardType, ChangeKind changeKind, Audience audience,
			EventTag eventTag, String sourceOrg, String topic, String title,
			String dontText, String doText, String sceneKey, String ttsText, String legalBasis, String lawExcerpt,
			String penaltyText, LocalDate effectiveOn, String exceptions, DecisionTable decision, CardStatus status,
			int version, LocalDateTime verifiedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
	}

	public record CardReviewResponse(Long id, Long reviewerId, int cardVersion, ReviewDecision decision,
			LocalDateTime reviewedAt) {

		static CardReviewResponse of(CardReview r) {
			return new CardReviewResponse(r.getId(), r.getReviewerId(), r.getCardVersion(), r.getDecision(),
					r.getReviewedAt());
		}
	}

	@Operation(summary = "카드 작성", description = "DRAFT · version 1 로 만든다. 유형별 필수 칸과 결정 테이블을 검증한다 (422)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CardResponse create(@Valid @RequestBody CardContent content) {
		return toResponse(service.create(content));
	}

	@Operation(summary = "카드 목록")
	@GetMapping
	public List<CardResponse> list(@RequestParam(required = false) CardStatus status) {
		return service.list(status).stream().map(this::toResponse).toList();
	}

	@Operation(summary = "카드 상세")
	@GetMapping("/{id}")
	public CardResponse get(@PathVariable Long id) {
		return toResponse(service.get(id));
	}

	@Operation(summary = "카드 수정", description = "내용 전체를 바꾸고 DRAFT 로 돌린다. 게시 중이던 카드도 앱에는 이전 게시본이 남는다")
	@PutMapping("/{id}")
	public CardResponse update(@PathVariable Long id, @Valid @RequestBody CardContent content) {
		return toResponse(service.update(id, content));
	}

	@Operation(summary = "검수 요청", description = "DRAFT → IN_REVIEW. 이 버전에 검수 기록이 있으면 version + 1")
	@PostMapping("/{id}/submit")
	public CardResponse submit(@PathVariable Long id) {
		return toResponse(service.submitForReview(id));
	}

	@Operation(summary = "교차 검수", description = "반려 1건이면 DRAFT, 서로 다른 검수자 승인 2건이면 PUBLISHED")
	@PostMapping("/{id}/reviews")
	public CardResponse review(@PathVariable Long id, @Valid @RequestBody ReviewBody req) {
		return toResponse(service.review(id, req.reviewerId(), req.decision()));
	}

	@Operation(summary = "검수 기록")
	@GetMapping("/{id}/reviews")
	public List<CardReviewResponse> reviews(@PathVariable Long id) {
		return service.reviewsOf(id).stream().map(CardReviewResponse::of).toList();
	}

	@Operation(summary = "회수", description = "RETIRED 로 바꾸고 topic 을 비운다. 다음 동기화에서 앱이 카드를 내린다")
	@PostMapping("/{id}/retire")
	public CardResponse retire(@PathVariable Long id) {
		return toResponse(service.retire(id));
	}

	@Operation(summary = "Gleo 대표 주제 지정·해제", description = "topic 을 비우면 해제. 한 주제에 대표 카드는 1장 (결정 006)")
	@PutMapping("/{id}/topic")
	public CardResponse assignTopic(@PathVariable Long id, @Valid @RequestBody TopicRequest req) {
		return toResponse(service.assignTopic(id, req.topic()));
	}

	private CardResponse toResponse(RuleCard c) {
		Long revisionId = c.getRevision() == null ? null : c.getRevision().getId();
		return new CardResponse(c.getId(), revisionId, c.getCardType(), c.getChangeKind(), c.getAudience(),
				c.getEventTag(), c.getSourceOrg(), c.getTopic(), c.getTitle(),
				c.getDontText(), c.getDoText(), c.getSceneKey(), c.getTtsText(), c.getLegalBasis(),
				c.getLawExcerpt(), c.getPenaltyText(), c.getEffectiveOn(), c.getExceptions(),
				decisionJson.read(c.getDecisionJson()), c.getStatus(), c.getVersion(), c.getVerifiedAt(),
				c.getCreatedAt(), c.getUpdatedAt());
	}

}
