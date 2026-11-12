package com.jochana.driverule.law;

import java.time.LocalDate;
import java.util.List;

/** 법령 하나의 판 목록을 공포일 범위로 가져온다. 운영은 {@link LawApiClient}, 테스트는 가짜. */
public interface LawVersionSource {

	List<LawVersion> versions(String lawId, LocalDate from, LocalDate to);

}
