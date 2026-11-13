package com.jochana.driverule.law;

import java.time.LocalDate;

/**
 * Open API 시행일 법령 목록(eflaw)의 한 행 = 한 법령의 시행일별 판.
 * 시행일이 나뉜 개정은 같은 공포번호로 여러 행이 온다 (결정 011).
 *
 * @param serial          법령일련번호 — 판마다 다르다. 원문 화면 주소(lsiSeq)에 쓴다
 * @param promulgationNo  공포번호 (앞의 0 을 뗀 수)
 * @param effectiveOn     시행일자 (없으면 null)
 */
public record LawVersion(String serial, String lawId, String lawName, String lawKind, int promulgationNo,
		LocalDate promulgatedOn, LocalDate effectiveOn, String revisionKind) {

	/** 기존 손 등록과 같은 형식: 제22067호 */
	public String lawNo() {
		return "제" + promulgationNo + "호";
	}

}
