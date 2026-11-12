# 주행 안내

앱 화면에서 허용된 주행 안내의 Route/Screen/ViewModel/UiState를 둘 위치다. coordinator의 읽기 전용 상태를 표시하고 허용된 사용자 명령만 전달한다. 현재 기능 구현 없음.

판단 정책은 domain/guidance, 실제 실행·타이머는 app/guidance, 플랫폼 표시 구현은 core/platform/presentation이 맡는다. SDK 콜백·TTS·자동 종료를 UI에서 직접 실행하지 않으며 화면 재구독으로 안내를 시작하지 않는다. 최종 카드 표면은 플랫폼 검증 후 연결한다.
