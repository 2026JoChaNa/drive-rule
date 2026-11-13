package com.jochana.driverule.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gleo App Schema 초안({@code spec/gleo-app-schema/schema.json})이 Pleos 작성 가이드와 서버 공통 조건 목록에 맞는지 (결정 012).
 * 앱이 이 파일을 {@code assets/schema.json} 으로 복사해 심사를 받는다 — 여기서 어긋나면 앱에 들어가기 전에 잡는다.
 */
class GleoAppSchemaTest {

	static final Path SCHEMA = Path.of("..", "spec", "gleo-app-schema", "schema.json");

	static final List<String> PARAM_TYPES = List.of("string", "number", "boolean", "array");

	final JsonNode schema = read(SCHEMA);

	static JsonNode read(Path path) {
		try {
			return JsonMapper.builder().build().readTree(Files.readString(path));
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	Map<String, JsonNode> functions() {
		Map<String, JsonNode> byName = new LinkedHashMap<>();
		schema.get("actionCalls").forEach(a -> byName.put(a.get("function").get("name").asString(), a.get("function")));
		return byName;
	}

	@Test
	void followsPleosWritingGuide() {
		assertThat(schema.get("packageName").asString()).isEqualTo("com.jochana.driverule");
		assertThat(schema.get("actionCalls")).hasSizeLessThanOrEqualTo(3);
		assertThat(englishOnly(schema.get("appDescription"))).isTrue();
		schema.get("actionCalls").forEach(a -> assertThat(a.get("type").asString()).isEqualTo("function"));

		functions().forEach((name, f) -> {
			assertThat(name).as("function 이름은 대문자 + 밑줄").matches("[A-Z]+(_[A-Z]+)+");
			assertThat(englishOnly(f.get("description"))).as(name + " description 은 영어").isTrue();
			assertThat(f.get("component").asString()).isIn("activity", "service");
			JsonNode params = f.get("parameters");
			assertThat(params.get("type").asString()).isEqualTo("object");
			List<String> names = new ArrayList<>();
			params.get("properties").propertyNames().forEach(names::add);
			params.get("properties").forEach(p -> {
				assertThat(p.get("type").isString()).as("type 은 하나만").isTrue();
				assertThat(p.get("type").asString()).isIn(PARAM_TYPES);
				assertThat(englishOnly(p.get("description"))).isTrue();
				if (p.has("enum")) {
					assertThat(p.get("type").asString()).as("enum 은 문자열만").isEqualTo("string");
					p.get("enum").forEach(v -> assertThat(v.isString()).isTrue());
				}
			});
			params.get("required").forEach(r -> assertThat(names).contains(r.asString()));
		});
	}

	@Test
	void askRuleConditionsAreExactlyTheCommonConditionList() {
		JsonNode props = functions().get("ASK_RULE").get("parameters").get("properties");
		Map<String, List<String>> inSchema = new LinkedHashMap<>();
		props.propertyNames().forEach(name -> {
			if (!name.equals("topic")) {
				inSchema.put(name, values(props.get(name).get("enum")));
			}
		});
		Map<String, List<String>> inServer = new LinkedHashMap<>();
		Arrays.stream(RuleCondition.values()).forEach(c -> inServer.put(c.key(), c.optionKeys()));

		assertThat(inSchema).containsExactlyEntriesOf(inServer);
	}

	@Test
	void seedTopicsAreAskable() {
		List<String> topics = values(
				functions().get("ASK_RULE").get("parameters").get("properties").get("topic").get("enum"));
		assertThat(topics).contains("other").doesNotHaveDuplicates();

		read(Path.of("seed", "pt-cards.json")).get("cards").forEach(card -> {
			if (card.has("topic")) {
				assertThat(topics).contains(card.get("topic").asString());
			}
		});
	}

	static List<String> values(JsonNode array) {
		List<String> out = new ArrayList<>();
		array.forEach(v -> out.add(v.asString()));
		return out;
	}

	static boolean englishOnly(JsonNode text) {
		return text != null && !text.asString().isBlank() && text.asString().chars().allMatch(ch -> ch < 128);
	}

}
