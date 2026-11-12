package com.jochana.driverule.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** manifest · 내용 주소 파일 · 캐시 헤더 (결정 010). 커밋된 실제 파일을 쓴다. */
@WebMvcTest(controllers = GeoDatasetController.class, properties = "driverule.admin.api-key=k")
@Import(SchoolZoneDataset.class)
class GeoDatasetControllerWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	SchoolZoneDataset dataset;

	@Test
	void manifestPointsToContentAddressedFile() throws Exception {
		String sha = dataset.manifest().sha256();

		mvc.perform(get("/api/v1/geo/school-zones"))
				.andExpect(status().isOk())
				.andExpect(header().string("ETag", "\"" + sha + "\""))
				.andExpect(header().string("Cache-Control", "no-cache"))
				.andExpect(jsonPath("$.dataset").value("school-zones"))
				.andExpect(jsonPath("$.schema").value(1))
				.andExpect(jsonPath("$.sha256").value(sha))
				.andExpect(jsonPath("$.count").value(dataset.manifest().count()))
				.andExpect(jsonPath("$.url").value("/api/v1/geo/school-zones/" + sha + ".json"));
	}

	@Test
	void unchangedManifestIs304() throws Exception {
		mvc.perform(get("/api/v1/geo/school-zones")
				.header("If-None-Match", "\"" + dataset.manifest().sha256() + "\""))
				.andExpect(status().isNotModified());
	}

	@Test
	void fileBytesMatchManifestHashAndCacheForever() throws Exception {
		SchoolZoneDataset.Manifest m = dataset.manifest();

		byte[] body = mvc.perform(get(m.url()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/json"))
				.andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
				.andReturn().getResponse().getContentAsByteArray();

		assertThat(SchoolZoneDataset.sha256(body)).as("앱이 계산할 해시").isEqualTo(m.sha256());
		assertThat(body.length).isEqualTo(m.bytes());
	}

	@Test
	void otherHashIs404() throws Exception {
		mvc.perform(get("/api/v1/geo/school-zones/" + "0".repeat(64) + ".json"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DATASET_VERSION_NOT_FOUND"));
	}

}
