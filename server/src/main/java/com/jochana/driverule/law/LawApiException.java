package com.jochana.driverule.law;

/** Open API 호출 · 응답 해석 실패. 문장에 인증값(OC)을 넣지 않는다. */
public class LawApiException extends RuntimeException {

	public LawApiException(String message) {
		super(message);
	}

}
