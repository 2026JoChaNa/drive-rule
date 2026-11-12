package com.jochana.driverule.geo;

import com.jochana.driverule.common.ApiException;
import com.jochana.driverule.geo.SchoolZoneDataset.Manifest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@Tag(name = "앱 · 공공 좌표")
@RestController
@RequestMapping("/api/v1/geo/school-zones")
public class GeoDatasetController {

	private final SchoolZoneDataset schoolZones;

	public GeoDatasetController(SchoolZoneDataset schoolZones) {
		this.schoolZones = schoolZones;
	}

	@Operation(summary = "어린이보호구역 좌표 manifest",
			description = "카드 동기화 때 함께 부른다. ETag 를 저장해 If-None-Match 로 보내면 바뀌지 않았을 때 304."
					+ " sha256 이 저장한 값과 다르면 url(이 주소 기준으로 풀기)에서 파일을 받아 SHA-256 을 직접 확인한 뒤"
					+ " 차량 사본을 통째로 바꾼다. 확인에 실패하면 옛 사본을 계속 쓴다 (결정 010)")
	@GetMapping
	public ResponseEntity<Manifest> manifest(WebRequest request) {
		Manifest manifest = schoolZones.manifest();
		String etag = "\"" + manifest.sha256() + "\"";
		if (request.checkNotModified(etag)) {
			return null; // 304 — Spring 이 헤더와 상태를 이미 썼다
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(etag).body(manifest);
	}

	@Operation(summary = "어린이보호구역 좌표 파일",
			description = "manifest 의 url. 경로의 해시와 내용이 묶여 있어 오래 캐시해도 된다. 지금 내보내는 해시가 아니면 404")
	@GetMapping(value = "/{sha256}.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<byte[]> file(@PathVariable String sha256) {
		if (!schoolZones.manifest().sha256().equals(sha256)) {
			throw ApiException.notFound("DATASET_VERSION_NOT_FOUND",
					"이 해시의 보호구역 파일은 내보내지 않습니다 — manifest 를 다시 받으세요");
		}
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
				.eTag("\"" + sha256 + "\"")
				.contentType(MediaType.APPLICATION_JSON)
				.body(schoolZones.bytes());
	}

}
