package com.jochana.driverule.law;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jochana.driverule.common.TimeConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 수동 수집 API · sourceUrl (결정 011 3 · 5번). DB 없이 웹 계층만. */
class LawRevisionAdminControllerWebTest {

	@Nested
	@WebMvcTest(controllers = LawRevisionAdminController.class,
			properties = { "driverule.admin.api-key=k", "driverule.law-sync.oc=oc-value" })
	@Import({ LawSyncConfig.class, TimeConfig.class })
	class Configured {

		@Autowired
		MockMvc mvc;

		@MockitoBean
		LawRevisionRepository repository;

		@MockitoBean
		LawRevisionCollector collector;

		@Test
		void syncReturnsReport() throws Exception {
			given(collector.sync()).willReturn(new LawRevisionCollector.SyncReport(LocalDate.of(2026, 10, 1),
					LocalDate.of(2026, 11, 13), List.of(
							new LawRevisionCollector.LawResult("003395", LawRevisionCollector.Outcome.OK,
									"도로교통법 시행령", List.of("제36728호"), 0, null),
							new LawRevisionCollector.LawResult("007079", LawRevisionCollector.Outcome.FAILED, null,
									List.of(), 0, "Open API 거절: …"))));

			mvc.perform(post("/api/v1/admin/law-revisions/sync").header("X-Admin-Key", "k"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.from").value("2026-10-01"))
					.andExpect(jsonPath("$.laws[0].outcome").value("OK"))
					.andExpect(jsonPath("$.laws[0].created[0]").value("제36728호"))
					.andExpect(jsonPath("$.laws[1].outcome").value("FAILED"))
					.andExpect(jsonPath("$.laws[1].error").value("Open API 거절: …"));
		}

		@Test
		void syncNeedsAdminKey() throws Exception {
			mvc.perform(post("/api/v1/admin/law-revisions/sync")).andExpect(status().isUnauthorized());
			verify(collector, never()).sync();
		}

		@Test
		void listShowsSourceUrlOnlyForCollectedRows() throws Exception {
			LawRevision collected = new LawRevision("도로교통법 시행령 타법개정", "제36728호", "LAWGO-290387",
					LocalDate.of(2026, 9, 29), LocalDateTime.of(2026, 11, 13, 6, 30));
			LawRevision manual = new LawRevision("도로교통법 일부개정", "제22067호", "MANUAL-22067",
					LocalDate.of(2026, 10, 13), LocalDateTime.of(2026, 11, 12, 9, 0));
			ReflectionTestUtils.setField(collected, "id", 2L);
			ReflectionTestUtils.setField(manual, "id", 1L);
			given(repository.findAllByOrderByPromulgatedOnDescIdDesc()).willReturn(List.of(manual, collected));

			mvc.perform(get("/api/v1/admin/law-revisions").header("X-Admin-Key", "k"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].sourceUrl").doesNotExist())
					.andExpect(jsonPath("$[1].sourceUrl")
							.value("https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=290387"));
		}

	}

	@Nested
	@WebMvcTest(controllers = LawRevisionAdminController.class, properties = "driverule.admin.api-key=k")
	@Import({ LawSyncConfig.class, TimeConfig.class })
	class NotConfigured {

		@Autowired
		MockMvc mvc;

		@MockitoBean
		LawRevisionRepository repository;

		@MockitoBean
		LawRevisionCollector collector;

		@Test
		void syncIs503WithoutOc() throws Exception {
			mvc.perform(post("/api/v1/admin/law-revisions/sync").header("X-Admin-Key", "k"))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.code").value("LAW_API_NOT_CONFIGURED"));
			verify(collector, never()).sync();
		}

	}

}
