package com.jochana.driverule.request;

public enum RequestStatus {
	/** 처리 전 */
	OPEN,
	/** 카드 작성·수정으로 처리함 */
	RESOLVED,
	/** 처리하지 않기로 함 (중복·범위 밖 등) */
	DISMISSED
}
