package com.jochana.driverule;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** V2 — 예전 조건 키로 저장된 결정 테이블 · 답변 보류 조건을 새 이름으로 바꾼다 (결정 012 2번). */
@Testcontainers
class ConditionKeyMigrationTests {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	Flyway flyway(String target) {
		return Flyway.configure()
			.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.target(target)
			.cleanDisabled(false)
			.load();
	}

	@Test
	void renamesKeysInsideJsonAndKeepsValues() throws Exception {
		flyway("latest").clean();
		flyway("1").migrate();
		try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
				Statement st = c.createStatement()) {
			st.executeUpdate("""
					INSERT INTO rule_card (card_type, change_kind, audience, title, tts_text, legal_basis, law_excerpt,
					  status, version, created_at, updated_at, decision_json)
					VALUES ('CONDITIONAL', 'LAW', 'GENERAL', 't', 't', 'b', 'e', 'DRAFT', 1, NOW(), NOW(),
					  '{"schema":2,"tables":[{"key":"approach","conditions":["turn_light","front_signal"],
					    "rows":[{"when":{"turn_light":"no","front_signal":"red"},"text":"pedestrian 이라는 낱말은 그대로"}]},
					   {"key":"crosswalk","conditions":["ped_signal","pedestrian"],
					    "rows":[{"when":{"ped_signal":"green","pedestrian":"yes"},"text":"x"}]}]}')
					""");
			st.executeUpdate("""
					INSERT INTO review_request (request_type, status, topic, conditions_json, created_at)
					VALUES ('ANSWER_HOLD', 'OPEN', 'right_turn', '{"ped_signal":"red","pedestrian":"no"}', NOW())
					""");

			flyway("latest").migrate();

			try (ResultSet rs = st.executeQuery("SELECT CAST(decision_json AS CHAR) FROM rule_card")) {
				rs.next();
				String json = rs.getString(1);
				assertThat(json).contains("\"right_turn_traffic_light\": \"no\"", "\"front_traffic_light\": \"red\"",
						"\"crosswalk_light\": \"green\"", "\"pedestrian_present\": \"yes\"",
						"[\"crosswalk_light\", \"pedestrian_present\"]", "pedestrian 이라는 낱말은 그대로")
					.doesNotContain("\"turn_light\"", "\"front_signal\"", "\"ped_signal\"", "\"pedestrian\"");
			}
			try (ResultSet rs = st.executeQuery("SELECT CAST(conditions_json AS CHAR) FROM review_request")) {
				rs.next();
				assertThat(rs.getString(1)).isEqualTo("{\"crosswalk_light\": \"red\", \"pedestrian_present\": \"no\"}");
			}
		}
	}

}
