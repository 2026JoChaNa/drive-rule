package com.jochana.driverule.card;

import com.jochana.driverule.card.CardSyncService.SyncResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "앱 · 카드 동기화")
@RestController
@RequestMapping("/api/v1/cards")
public class CardSyncController {

	private final CardSyncService service;

	public CardSyncController(CardSyncService service) {
		this.service = service;
	}

	@Operation(summary = "게시본·회수 동기화",
			description = "하루 1회 + 시동 시. syncToken 없이 부르면 전체(fullSync=true)."
					+ " 응답의 syncToken 을 저장해 다음 호출에 넘긴다. 410 이면 토큰을 버리고 전체를 다시 받는다."
					+ " 앱은 받은 카드를 ID 로 덮어쓰고 retiredCardIds 만 내린다 — 목록에 없다고 사본을 지우지 않는다"
					+ " (게시 후 수정 중인 카드는 목록에 없다)")
	@GetMapping("/sync")
	public SyncResponse sync(
			@Parameter(description = "이전 응답의 syncToken (해석하지 말고 그대로)")
			@RequestParam(required = false) String syncToken) {
		return service.sync(syncToken);
	}

}
