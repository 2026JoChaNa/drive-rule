package com.jochana.driverule.request;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "앱 · 신고", description = "식별자(프로필 · 기기 ID) · 질문 원문 · 위치를 보내지 않는다")
@RestController
@RequestMapping("/api/v1")
public class AppRequestController {

	private final AppRequestService service;

	public AppRequestController(AppRequestService service) {
		this.service = service;
	}

	public record ErrorReportRequest(@NotNull @Positive Integer cardVersion) {
	}

	/** @param conditions 운전자가 답한 조건 (공통 조건 키 → 값). 없으면 생략 */
	public record AnswerHoldRequest(@NotBlank @Size(max = 40) String topic, Map<String, String> conditions) {
	}

	@Operation(summary = "카드 오류 신고", description = "근거 시트의 '오류 신고'. cardVersion 은 앱 사본의 버전")
	@PostMapping("/cards/{cardId}/error-reports")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void reportError(@PathVariable Long cardId, @Valid @RequestBody ErrorReportRequest req) {
		service.reportError(cardId, req.cardVersion());
	}

	@Operation(summary = "Gleo 답변 보류 신고",
			description = "앱 사본에 답할 카드가 없을 때(대표 카드 없음 · 보류 행). topic 과 조건만 보낸다")
	@PostMapping("/answer-holds")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void reportAnswerHold(@Valid @RequestBody AnswerHoldRequest req) {
		service.reportAnswerHold(req.topic(), req.conditions());
	}

}
