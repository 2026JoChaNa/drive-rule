package com.jochana.driverule.card;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** {@link DecisionTable} ↔ {@code rule_card.decision_json} 문자열 변환. */
@Component
public class DecisionTableJson {

	private final JsonMapper jsonMapper;

	public DecisionTableJson(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public String write(DecisionTable table) {
		return table == null ? null : jsonMapper.writeValueAsString(table);
	}

	public DecisionTable read(String json) {
		return json == null ? null : jsonMapper.readValue(json, DecisionTable.class);
	}

}
