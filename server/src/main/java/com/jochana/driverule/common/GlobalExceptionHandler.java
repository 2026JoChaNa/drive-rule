package com.jochana.driverule.common;

import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 오류 응답 형식: RFC 9457 ProblemDetail + {@code code}, {@code errors}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApi(ApiException e) {
		return build(e.status(), e.code(), e.getMessage(), e.errors());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
		List<String> errors = e.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + ": " + f.getDefaultMessage())
				.sorted()
				.toList();
		return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다", errors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
		return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 본문을 읽을 수 없습니다", List.of());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다",
				List.of(e.getName() + ": 형식이 맞지 않습니다 (" + e.getValue() + ")"));
	}

	/**
	 * DB 제약 위반. 동시 요청이 앞선 중복 검사를 지나쳐 UNIQUE 에 걸린 경우만 409 이고,
	 * 나머지(길이 초과 등 — 원래 요청 검증에서 막혀야 하는 것)는 422 로 돌려준다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof ConstraintViolationException cve
					&& cve.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
				return build(HttpStatus.CONFLICT, "CONFLICT", "이미 같은 값이 있습니다", List.of());
			}
		}
		return build(HttpStatus.UNPROCESSABLE_CONTENT, "DATA_REJECTED", "저장할 수 없는 값입니다", List.of());
	}

	private static ResponseEntity<ProblemDetail> build(HttpStatus status, String code, String message,
			List<String> errors) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, message);
		body.setProperty("code", code);
		body.setProperty("errors", errors);
		return ResponseEntity.status(status).body(body);
	}

}
