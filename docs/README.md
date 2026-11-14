# docs

| 폴더 · 파일 | 내용 |
|---|---|
| [decisions/](decisions/) | 공유 결정 기록 001~012 (서버 · ERD · 기획 정렬) |
| [api/](api/) | [차량 앱 API](api/app-api.md) · [검수 CMS API](api/admin-api.md) |
| [db/](db/) | ERD (`erd.data.mjs` → `gen-erd.mjs` → `ERD.drawio`) |
| [flows/](flows/) | 도메인별 흐름도 8장 |
| [legal-check/](legal-check/) | 시연 · 규칙 변경 카드의 법령 대조 기록 |
| [requests/](requests/) | 팀 사이 요청서 원문 (기획 → 개발, 백엔드 → 앱) |
| [android/](android/) | Android 설계 · 기록 (아래) |
| [deploy.md](deploy.md) | 서버 배포 (EC2 + Docker) |

## android/

지금 기준으로 읽는 문서는 위쪽 네 개입니다. `history/`는 날짜가 붙은 작업 · 검증 기록이라 당시 상태를 담고 있고, 내용이 현재와 다를 수 있습니다.

| 문서 | 내용 |
|---|---|
| [ui-progress.md](android/ui-progress.md) | Figma 대비 UI 개발 현황과 남은 일 |
| [architecture.md](android/architecture.md) | 앱 구조 (MVVM + UDF, 주행 안내 수명 분리) |
| [motion-spec.md](android/motion-spec.md) | 화면 움직임 명세 (앱에서 설계, 디자이너 검토 대기) |
| [figma-assets.md](android/figma-assets.md) | Figma node · 에셋 · 폰트 출처 |
| [decisions/](android/decisions/) | Android 전용 결정 A01 · A02 (공유 결정과 번호를 나눔) |
| [references/](android/references/) | Pleos SDK 상황 신호 정리 |
| [history/](android/history/) | 작업 · 검증 기록 (11/12 ~ 11/14) |
| [screenshots/](android/screenshots/) | 기록에 쓴 화면 캡처 |
