package com.jochana.driverule.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * API 가 의도해서 돌려주는 오류. {@link GlobalExceptionHandler} 가 ProblemDetail 로 바꾼다.
 *
 * @param code   클라이언트가 분기할 때 쓰는 고정 문자열 (예: CARD_NOT_FOUND)
 * @param errors 검증 실패 목록 등 세부 사항 (없으면 빈 목록)
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final List<String> errors;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, List.of());
	}

	public ApiException(HttpStatus status, String code, String message, List<String> errors) {
		super(message);
		this.status = status;
		this.code = code;
		this.errors = List.copyOf(errors);
	}

	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message);
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public List<String> errors() {
		return errors;
	}

}
