# 원격 데이터

앱 API v1의 `BackendApiClient`, wire DTO, 성공/HTTP/통신/파싱 오류 결과를 둔다. Retrofit + OkHttp + kotlinx.serialization 조합이다. `AppContainer.backendApi`로 지연 생성하며 현재 UI·프로필 저장에서 호출하지 않는다.

계약에는 사용자 등록 API가 없다. 이름·프로필 ID·기기 ID·좌표·VIN·질문 원문을 요청에 붙이지 않는다. 운전자 등록은 `RoomDriverProfileRepository`가 계속 담당한다.

알 수 없는 응답 필드를 무시하고 wire enum은 문자열로 보존한다. DTO를 그대로 화면/안내에 사용하지 않는다. schema 2 검증, 미지원 카드 건너뛰기, 회수 처리, 카드·조건·syncToken의 원자적 저장, 재시도 스케줄은 후속 repository/mapper의 책임이다. 현재는 동기화 스케줄이나 법령 카드 캐시를 구현하지 않았다.
