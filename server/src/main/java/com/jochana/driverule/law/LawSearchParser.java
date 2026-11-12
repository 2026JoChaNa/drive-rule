package com.jochana.driverule.law;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * lawSearch.do (type=JSON) 응답 해석 — 결정 011 '조사'의 응답 모양을 받는다.
 * <ul>
 * <li>결과가 1건이면 {@code law} 가 객체, 여러 건이면 배열, 0건이면 없다</li>
 * <li>숫자도 문자열로 온다. 공포번호는 앞에 0 이 붙는다 (08635)</li>
 * <li>인증 실패는 HTTP 200 + {@code {"result":…,"msg":…}} — {@code LawSearch} 가 없다</li>
 * </ul>
 */
final class LawSearchParser {

	/** @param skippedRows 필수 칸(일련번호 · 법령명 · 공포번호 · 공포일)이 없어 버린 행 수 */
	record Page(int totalCount, List<LawVersion> versions, int skippedRows) {
	}

	private LawSearchParser() {
	}

	static Page parse(JsonMapper json, String body) {
		JsonNode root;
		try {
			root = json.readTree(body);
		}
		catch (JacksonException e) {
			throw new LawApiException("JSON 이 아닌 응답입니다 (인증값 승인 전이면 안내 화면이 온다): " + head(body));
		}
		JsonNode search = root == null ? null : root.get("LawSearch");
		if (search == null || !search.isObject()) {
			String result = text(root, "result");
			String msg = text(root, "msg");
			if (result != null || msg != null) {
				throw new LawApiException("Open API 거절: " + join(result, msg));
			}
			throw new LawApiException("LawSearch 가 없는 응답입니다: " + head(body));
		}
		List<LawVersion> versions = new ArrayList<>();
		int skipped = 0;
		for (JsonNode row : rows(search.get("law"))) {
			LawVersion v = version(row);
			if (v == null) {
				skipped++;
			}
			else {
				versions.add(v);
			}
		}
		Integer total = integer(text(search, "totalCnt"));
		return new Page(total == null ? versions.size() + skipped : total, List.copyOf(versions), skipped);
	}

	private static List<JsonNode> rows(JsonNode law) {
		List<JsonNode> out = new ArrayList<>();
		if (law == null || law.isNull()) {
			return out;
		}
		if (law.isArray()) {
			law.forEach(out::add);
		}
		else if (law.isObject()) {
			out.add(law);
		}
		return out;
	}

	private static LawVersion version(JsonNode row) {
		if (!row.isObject()) {
			return null;
		}
		String serial = text(row, "법령일련번호");
		String name = text(row, "법령명한글");
		Integer number = integer(text(row, "공포번호"));
		LocalDate promulgated = date(text(row, "공포일자"));
		if (serial == null || name == null || number == null || number <= 0 || promulgated == null) {
			return null;
		}
		return new LawVersion(serial, text(row, "법령ID"), name, text(row, "법령구분명"), number, promulgated,
				date(text(row, "시행일자")), text(row, "제개정구분명"));
	}

	/** 값 칸을 글자로. 없거나 비었거나 객체 · 배열이면 null */
	static String text(JsonNode node, String field) {
		JsonNode v = node == null ? null : node.get(field);
		if (v == null || !v.isValueNode() || v.isNull()) {
			return null;
		}
		String s = v.asString().trim();
		return s.isEmpty() ? null : s;
	}

	private static Integer integer(String s) {
		if (s == null || !s.matches("\\d{1,9}")) {
			return null;
		}
		return Integer.parseInt(s);
	}

	private static LocalDate date(String s) {
		if (s == null) {
			return null;
		}
		String digits = s.replaceAll("[^0-9]", "");
		if (digits.length() != 8) {
			return null;
		}
		try {
			return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE);
		}
		catch (DateTimeParseException e) {
			return null;
		}
	}

	private static String join(String a, String b) {
		return a == null ? b : b == null ? a : a + " " + b;
	}

	private static String head(String body) {
		if (body == null) {
			return "(빈 응답)";
		}
		String flat = body.replaceAll("\\s+", " ").trim();
		return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
	}

}
