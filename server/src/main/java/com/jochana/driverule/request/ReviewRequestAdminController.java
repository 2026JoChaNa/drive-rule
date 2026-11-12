package com.jochana.driverule.request;

import com.jochana.driverule.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Tag(name = "CMS · 검수 요청 대기열")
@RestController
@RequestMapping("/api/v1/admin/review-requests")
public class ReviewRequestAdminController {

	private static final TypeReference<Map<String, String>> CONDITIONS = new TypeReference<>() {
	};

	private final ReviewRequestRepository repository;
	private final JsonMapper jsonMapper;

	public ReviewRequestAdminController(ReviewRequestRepository repository, JsonMapper jsonMapper) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
	}

	private ReviewRequestResponse toResponse(ReviewRequest r) {
		Map<String, String> conditions = r.getConditionsJson() == null ? null
				: jsonMapper.readValue(r.getConditionsJson(), CONDITIONS);
		return new ReviewRequestResponse(r.getId(), r.getRequestType(), r.getCardId(), r.getCardVersion(),
				r.getTopic(), conditions, r.getStatus(), r.getCreatedAt());
	}

	public record StatusRequest(@NotNull RequestStatus status) {
	}

	public record ReviewRequestResponse(Long id, RequestType requestType, Long cardId, Integer cardVersion,
			String topic, Map<String, String> conditions, RequestStatus status, LocalDateTime createdAt) {
	}

	@Operation(summary = "검수 요청 목록", description = "최신순. status · requestType 으로 거른다")
	@GetMapping
	@Transactional(readOnly = true)
	public List<ReviewRequestResponse> list(@RequestParam(required = false) RequestStatus status,
			@RequestParam(required = false) RequestType requestType) {
		return repository.search(status, requestType).stream().map(this::toResponse).toList();
	}

	@Operation(summary = "검수 요청 처리 상태 변경")
	@PatchMapping("/{id}")
	@Transactional
	public ReviewRequestResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
		ReviewRequest request = repository.findById(id)
				.orElseThrow(() -> ApiException.notFound("REVIEW_REQUEST_NOT_FOUND", "검수 요청이 없습니다: " + id));
		request.changeStatus(req.status());
		return toResponse(request);
	}

}
