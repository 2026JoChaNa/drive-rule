package com.jochana.driverule.law;

import com.jochana.driverule.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CMS · 법령 개정")
@RestController
@RequestMapping("/api/v1/admin/law-revisions")
public class LawRevisionAdminController {

	private final LawRevisionRepository repository;
	private final LawRevisionCollector collector;
	private final LawSyncProperties syncProps;
	private final Clock clock;

	public LawRevisionAdminController(LawRevisionRepository repository, LawRevisionCollector collector,
			LawSyncProperties syncProps, Clock clock) {
		this.repository = repository;
		this.collector = collector;
		this.syncProps = syncProps;
		this.clock = clock;
	}

	public record CreateRequest(
			@NotBlank @Size(max = 100) String lawName,
			@NotBlank @Size(max = 30) String lawNo,
			@NotBlank @Size(max = 40) String sourceKey,
			@NotNull LocalDate promulgatedOn) {
	}

	public record TriageRequest(@NotNull TriageStatus triageStatus) {
	}

	/** @param sourceUrl 수집기가 넣은 행이면 국가법령정보센터 원문 화면, 손 등록 행이면 null (결정 011) */
	public record LawRevisionResponse(Long id, String lawName, String lawNo, String sourceKey,
			LocalDate promulgatedOn, TriageStatus triageStatus, LocalDateTime createdAt, String sourceUrl) {

		static LawRevisionResponse of(LawRevision r) {
			return new LawRevisionResponse(r.getId(), r.getLawName(), r.getLawNo(), r.getSourceKey(),
					r.getPromulgatedOn(), r.getTriageStatus(), r.getCreatedAt(), sourceUrl(r.getSourceKey()));
		}

		static String sourceUrl(String sourceKey) {
			if (sourceKey == null || !sourceKey.startsWith(LawRevisionCollector.SOURCE_PREFIX)) {
				return null;
			}
			String serial = sourceKey.substring(LawRevisionCollector.SOURCE_PREFIX.length());
			return serial.matches("\\d+") ? "https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=" + serial : null;
		}
	}

	@Operation(summary = "법령 개정 등록", description = "분류 상태는 PENDING 으로 시작한다")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public LawRevisionResponse create(@Valid @RequestBody CreateRequest req) {
		if (repository.existsByLawNo(req.lawNo())) {
			throw ApiException.conflict("LAW_NO_DUPLICATED", "이미 등록된 공포 번호입니다: " + req.lawNo());
		}
		LawRevision saved = repository.save(new LawRevision(req.lawName(), req.lawNo(), req.sourceKey(),
				req.promulgatedOn(), LocalDateTime.now(clock)));
		return LawRevisionResponse.of(saved);
	}

	@Operation(summary = "법령 개정 목록", description = "공포일 최신순")
	@GetMapping
	@Transactional(readOnly = true)
	public List<LawRevisionResponse> list(@RequestParam(required = false) TriageStatus triageStatus) {
		List<LawRevision> rows = triageStatus == null
				? repository.findAllByOrderByPromulgatedOnDescIdDesc()
				: repository.findAllByTriageStatusOrderByPromulgatedOnDescIdDesc(triageStatus);
		return rows.stream().map(LawRevisionResponse::of).toList();
	}

	@Operation(summary = "법령 개정 분류")
	@PatchMapping("/{id}/triage")
	@Transactional
	public LawRevisionResponse triage(@PathVariable Long id, @Valid @RequestBody TriageRequest req) {
		LawRevision revision = repository.findById(id)
				.orElseThrow(() -> ApiException.notFound("LAW_REVISION_NOT_FOUND", "법령 개정이 없습니다: " + id));
		revision.changeTriageStatus(req.triageStatus());
		return LawRevisionResponse.of(revision);
	}


	@Operation(summary = "법령 개정 지금 수집",
			description = "국가법령정보 Open API 로 감시 법령의 최근 공포분을 받아 없는 개정만 PENDING 으로 넣는다 (결정 011). "
					+ "평소에는 매일 06:30 에 자동으로 돈다. LAW_API_OC 가 없으면 503 LAW_API_NOT_CONFIGURED")
	@PostMapping("/sync")
	public LawRevisionCollector.SyncReport sync() {
		if (!syncProps.configured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LAW_API_NOT_CONFIGURED",
					"LAW_API_OC 가 설정되지 않아 법령 개정을 수집할 수 없습니다");
		}
		return collector.sync();
	}

}
