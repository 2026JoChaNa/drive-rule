package com.jochana.driverule.geo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 어린이보호구역 좌표 파일 (결정 010). 부팅 때 한 번 읽어 검사하고 해시를 계산한다.
 * 파일이 잘못되면 서버가 뜨지 않는다 — 잘못된 좌표를 차에 내보내는 것보다 배포 실패가 낫다.
 * 파일은 {@code server/geo/build-school-zones.mjs} 가 만든다.
 */
@Component
public class SchoolZoneDataset {

	static final String RESOURCE = "geo/school-zones.json";
	static final String DATASET = "school-zones";
	static final int SCHEMA = 1;

	// 국내 범위 — 생성 스크립트와 같은 값
	static final double MIN_LAT = 33.0;
	static final double MAX_LAT = 38.7;
	static final double MIN_LON = 124.5;
	static final double MAX_LON = 132.0;

	private final byte[] bytes;
	private final Manifest manifest;
	private final String manifestEtag;

	@Autowired
	public SchoolZoneDataset(JsonMapper json) {
		this(json, read(RESOURCE));
	}

	SchoolZoneDataset(JsonMapper json, byte[] bytes) {
		this.bytes = bytes;
		JsonNode root = json.readTree(bytes);
		int count = validate(root);
		String sha256 = sha256(bytes);
		JsonNode referenceDate = root.path("referenceDate");
		this.manifest = new Manifest(DATASET, SCHEMA, referenceDate.isString() ? referenceDate.asString() : null,
				count, sha256, bytes.length, "/api/v1/geo/" + DATASET + "/" + sha256 + ".json");
		// manifest 자체의 해시 — 파일이 같아도 url · 항목이 바뀌면 ETag 가 바뀐다
		this.manifestEtag = "\"" + sha256(json.writeValueAsBytes(manifest)) + "\"";
	}

	/** 앱이 바뀜을 판단하는 정보. {@code url} 은 상대 경로일 수도 절대 URL 일 수도 있다 (CDN 으로 옮길 때). */
	public record Manifest(String dataset, int schema, String referenceDate, int count, String sha256, long bytes,
			String url) {
	}

	public Manifest manifest() {
		return manifest;
	}

	/** manifest 응답의 ETag (따옴표 포함). */
	public String manifestEtag() {
		return manifestEtag;
	}

	/**
	 * 해시 계산에 쓴 바이트 그대로 — 앱이 같은 해시를 얻으려면 한 바이트도 바뀌면 안 된다.
	 * 요청마다 31만 바이트를 복사하지 않으려고 내부 배열을 넘긴다. 패키지 밖에 열지 않고, 받는 쪽은 고치지 않는다.
	 */
	byte[] content() {
		return bytes;
	}

	private static int validate(JsonNode root) {
		// asInt 는 "1" · 1.9 도 1 로 바꾼다 — 정수 칸인지부터 본다
		require(root.path("schema").isInt() && root.path("schema").intValue() == SCHEMA, "schema 가 " + SCHEMA + " 이 아닙니다");
		require(DATASET.equals(root.path("dataset").asString(null)), "dataset 이 " + DATASET + " 이 아닙니다");
		JsonNode zones = root.path("zones");
		require(zones.isArray() && !zones.isEmpty(), "zones 가 비어 있습니다");
		require(root.path("count").isInt() && root.path("count").intValue() == zones.size(),
				"count(" + root.path("count") + ") 와 zones 수(" + zones.size() + ")가 다릅니다");
		JsonNode referenceDate = root.path("referenceDate");
		require(referenceDate.isMissingNode() || referenceDate.isNull()
				|| (referenceDate.isString() && referenceDate.asString().matches("\\d{4}-\\d{2}-\\d{2}")),
				"referenceDate 가 YYYY-MM-DD 가 아닙니다");
		for (int i = 0; i < zones.size(); i++) {
			JsonNode z = zones.get(i);
			require(z.isArray() && z.size() == 2 && z.get(0).isNumber() && z.get(1).isNumber(),
					"zones[" + i + "] 가 [위도, 경도] 가 아닙니다");
			double lat = z.get(0).asDouble();
			double lon = z.get(1).asDouble();
			require(lat >= MIN_LAT && lat <= MAX_LAT && lon >= MIN_LON && lon <= MAX_LON,
					"zones[" + i + "] (" + lat + ", " + lon + ") 가 국내 범위 밖입니다");
		}
		return zones.size();
	}

	private static void require(boolean ok, String message) {
		if (!ok) {
			throw new IllegalStateException(RESOURCE + ": " + message);
		}
	}

	private static byte[] read(String resource) {
		try (InputStream in = new ClassPathResource(resource).getInputStream()) {
			return in.readAllBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException(resource + " 를 읽지 못했습니다", e);
		}
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
