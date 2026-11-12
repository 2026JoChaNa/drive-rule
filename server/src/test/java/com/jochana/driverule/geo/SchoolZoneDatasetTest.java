package com.jochana.driverule.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 커밋된 좌표 파일과 부팅 검사 (결정 010). */
class SchoolZoneDatasetTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static SchoolZoneDataset of(String json) {
		return new SchoolZoneDataset(JSON, json.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void committedFilePassesBootChecks() {
		SchoolZoneDataset dataset = new SchoolZoneDataset(JSON);
		SchoolZoneDataset.Manifest m = dataset.manifest();

		assertThat(m.count()).as("전국 보호구역 — 원본 14,642건에서 같은 좌표를 합친 수").isGreaterThan(10_000);
		assertThat(m.sha256()).hasSize(64).isEqualTo(SchoolZoneDataset.sha256(dataset.bytes()));
		assertThat(m.bytes()).isEqualTo(dataset.bytes().length);
		assertThat(m.url()).isEqualTo("/api/v1/geo/school-zones/" + m.sha256() + ".json");
		assertThat(m.referenceDate()).matches("\\d{4}-\\d{2}-\\d{2}");
	}

	@Test
	void smallValidFile() {
		SchoolZoneDataset.Manifest m = of("""
				{"schema":1,"dataset":"school-zones","referenceDate":"2026-10-27","count":2,
				 "zones":[[33.5,126.5],[37.5,127.0]]}""").manifest();

		assertThat(m.count()).isEqualTo(2);
		assertThat(m.schema()).isEqualTo(1);
		assertThat(m.referenceDate()).isEqualTo("2026-10-27");
	}

	@Test
	void rejectsBrokenFiles() {
		assertRejected("""
				{"schema":2,"dataset":"school-zones","count":1,"zones":[[37.5,127.0]]}""", "schema");
		assertRejected("""
				{"schema":1,"dataset":"crosswalks","count":1,"zones":[[37.5,127.0]]}""", "dataset");
		assertRejected("""
				{"schema":1,"dataset":"school-zones","count":0,"zones":[]}""", "비어");
		assertRejected("""
				{"schema":1,"dataset":"school-zones","count":3,"zones":[[37.5,127.0]]}""", "count");
		assertRejected("""
				{"schema":1,"dataset":"school-zones","count":1,"zones":[[127.0,37.5]]}""", "범위");
		assertRejected("""
				{"schema":1,"dataset":"school-zones","count":1,"zones":[["37.5","127.0"]]}""", "zones[0]");
	}

	static void assertRejected(String json, String messagePart) {
		assertThatThrownBy(() -> of(json)).isInstanceOf(IllegalStateException.class).hasMessageContaining(messagePart);
	}

}
