package com.jochana.driverule.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 시연 시드({@code server/seed/pt-cards.json})가 카드 규칙을 통과하는지 — seed.mjs 를 돌리기 전에 깨진 카드를 잡는다. */
class SeedCardsTest {

	@TestFactory
	Stream<DynamicTest> seedCardsPassCardRules() throws Exception {
		JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
		JsonNode seed = mapper.readTree(Files.readString(Path.of("seed", "pt-cards.json")));

		return seed.get("cards").valueStream().map(node -> DynamicTest.dynamicTest("카드 " + node.get("_demo").asString(),
				() -> {
					CardContent card = mapper.treeToValue(node, CardContent.class);
					// seed.mjs 는 revisionLawNo 를 실제 ID 로 바꿔 넣는다 — 여기서는 '연결됨'만 흉내 낸다
					if (node.has("revisionLawNo")) {
						card = CardValidatorTest.with(card, card.changeKind(), card.audience(), card.eventTag(),
								card.sourceOrg(), 1L);
					}
					List<String> errors = CardValidator.validate(card.normalized());
					assertThat(errors).isEmpty();
				}));
	}

	/** seed.mjs 는 제목으로 이미 넣은 카드를 건너뛰고, 서버는 주제를 카드 1장에만 허용한다 (006). */
	@org.junit.jupiter.api.Test
	void titlesAndTopicsAreUniqueAndRevisionsExist() throws Exception {
		JsonNode seed = JsonMapper.builder().build().readTree(Files.readString(Path.of("seed", "pt-cards.json")));
		List<String> titles = seed.get("cards").valueStream().map(n -> n.get("title").asString()).toList();
		List<String> topics = seed.get("cards").valueStream().filter(n -> n.has("topic"))
				.map(n -> n.get("topic").asString()).toList();
		List<String> lawNos = seed.get("lawRevisions").valueStream().map(n -> n.get("lawNo").asString()).toList();

		assertThat(titles).doesNotHaveDuplicates();
		assertThat(topics).doesNotHaveDuplicates();
		assertThat(lawNos).doesNotHaveDuplicates();
		seed.get("cards").valueStream().filter(n -> n.has("revisionLawNo"))
				.forEach(n -> assertThat(lawNos).contains(n.get("revisionLawNo").asString()));
	}

	@TestFactory
	Stream<DynamicTest> everyEventHasADemoCard() throws Exception {
		JsonNode seed = JsonMapper.builder().build().readTree(Files.readString(Path.of("seed", "pt-cards.json")));
		List<String> tags = seed.get("cards").valueStream()
				.filter(n -> n.has("eventTag"))
				.map(n -> n.get("eventTag").asString())
				.toList();

		return Stream.of(EventTag.values()).map(tag -> DynamicTest.dynamicTest("이벤트 " + tag + " 시연 카드",
				() -> assertThat(tags).contains(tag.name())));
	}

}
