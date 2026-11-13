package com.jochana.driverule.law;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 국가법령정보 공동활용 Open API — 시행일 법령 목록 (lawSearch.do?target=eflaw, 결정 011).
 * 연혁 · 시행예정 · 현행 판을 모두 묻고(nw=1,2,3), 공포일 내림차순으로 100건씩 받는다.
 */
@Component
public class LawApiClient implements LawVersionSource {

	static final String BASE_URL = "https://www.law.go.kr";
	static final int PAGE_SIZE = 100;
	static final int MAX_PAGES = 10;
	private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
	private static final Pattern OC_PARAM = Pattern.compile("(?i)(OC=)[^&\\s\"']*");

	private final RestClient http;
	private final LawSyncProperties props;
	private final JsonMapper json;

	@Autowired
	public LawApiClient(LawSyncProperties props, JsonMapper json) {
		this(RestClient.builder().requestFactory(timeouts()), props, json);
	}

	LawApiClient(RestClient.Builder builder, LawSyncProperties props, JsonMapper json) {
		this.http = builder.baseUrl(BASE_URL).build();
		this.props = props;
		this.json = json;
	}

	private static JdkClientHttpRequestFactory timeouts() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		return factory;
	}

	@Override
	public List<LawVersion> versions(String lawId, LocalDate from, LocalDate to) {
		if (!props.configured()) {
			throw new LawApiException("LAW_API_OC 가 비어 있습니다");
		}
		List<LawVersion> out = new ArrayList<>();
		for (int page = 1; page <= MAX_PAGES; page++) {
			LawSearchParser.Page result;
			try {
				result = LawSearchParser.parse(json, fetch(lawId, from, to, page));
			}
			catch (LawApiException e) {
				throw new LawApiException(mask(e.getMessage()));
			}
			out.addAll(result.versions());
			boolean lastPage = result.versions().size() + result.skippedRows() < PAGE_SIZE
					|| page * PAGE_SIZE >= result.totalCount();
			// 공포일 내림차순 — 범위보다 오래된 행이 나왔으면 뒤쪽은 더 오래됐다 (서버가 ancYd 를 무시해도)
			boolean pastRange = !result.versions().isEmpty()
					&& result.versions().getLast().promulgatedOn().isBefore(from);
			if (lastPage || pastRange) {
				return out;
			}
		}
		throw new LawApiException(lawId + ": " + MAX_PAGES + "쪽을 넘었습니다 — 공포일 범위가 너무 넓습니다");
	}

	private String fetch(String lawId, LocalDate from, LocalDate to, int page) {
		try {
			byte[] body = http.get()
					.uri(b -> b.path("/DRF/lawSearch.do")
							.queryParam("OC", props.oc())
							.queryParam("target", "eflaw")
							.queryParam("type", "JSON")
							.queryParam("LID", lawId)
							.queryParam("nw", "1,2,3")
							.queryParam("ancYd", from.format(YMD) + "~" + to.format(YMD))
							.queryParam("sort", "ddes")
							.queryParam("display", PAGE_SIZE)
							.queryParam("page", page)
							.build())
					.retrieve()
					.body(byte[].class);
			// 응답 Content-Type 에 charset 이 없어도 한글이 깨지지 않게 직접 UTF-8 로 읽는다
			return body == null ? null : new String(body, StandardCharsets.UTF_8);
		}
		catch (RestClientException e) {
			throw new LawApiException(mask(e.getClass().getSimpleName() + ": " + e.getMessage()));
		}
	}

	/** 예외 문장에 섞인 요청 주소에서 인증값을 지운다. */
	String mask(String message) {
		if (message == null) {
			return null;
		}
		String masked = OC_PARAM.matcher(message).replaceAll("$1***");
		return props.oc().isEmpty() ? masked : masked.replace(props.oc(), "***");
	}

}
