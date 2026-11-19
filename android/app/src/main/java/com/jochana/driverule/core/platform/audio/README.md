# 음성 adapter

`PleosGuidanceSpeechOutput`이 `GuidanceSpeechOutput`을 구현한다. TTS 3.3.1의 ON_DEVICE 모드와 Locale.KOREA를 사용하며 오디오 포커스 허용 후에만 발화한다. SDK 연결·listener·focus 수명은 adapter, 이벤트 선택·기한·중복·취소는 coordinator가 맡는다.

콜백에는 발화 ID가 없고 원격 stop은 package 단위다. 로컬 요청 격리뿐 아니라 이전 발화 중지 확인 뒤 다음 요청을 시작하는 경계가 필요하다. 초기화/포커스 실패로 speak하지 않은 경로에는 stop을 요청하지 않는다. 종료 확인 실패와 실제 기기 검증 범위는 `docs/android/history/voice-and-transient-guidance-2026-11-15.md`를 따른다.

현재 debug 샘플 host에 연결했다. 재생 완료는 사용자 청취/확인이 아니며 실제 검수 법령 선택과 제품용 차량 실행 host는 후속 작업이다. 단순 UI 수집·복귀로 재생하지 않는다.
