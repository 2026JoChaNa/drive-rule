// DRIVE RULE ERD 데이터 — 이 파일만 고치고 생성기로 ERD.drawio 를 만든다.
//   node .claude/skills/drive-rule-erd/gen-erd.mjs
// 현재 단계: 설계 모드 (기준 = Notion 🚦 기획 v5, 2026-11-10 + 화면 대조 v5.2 요청 1~5, 2026-11-11 — 결정 005).
//   구현 이후에는 Flyway SQL / Room Entity 가 기준.
//   검토 중(미반영): topic 대표 카드(결정 006 초안) · favorite_place 삭제(결정 007 초안, 11/18 NaviHelper 검증 대기)
// MVP 최소안 (2026-11-11): 기획의 기능이 실제로 쓰는 컬럼만 둔다.
//   - 조건 분기는 JSON 1칸, 보호구역 목록·배포 정보는 DB 없이 CDN 파일(manifest)로
//   - 적용 대상(audience)·운전 상황(driving_context)은 기획에 정의가 없어 제외 — 게시 카드는 모든 일반 운전자 대상
//   - 시기별 재알림(2027 하반기)은 MVP 밖이라 재알림 시기 데이터 없음
//
// Flyway / Room 작성 시 함께 처리 (ERD 에 표기되지 않는 것)
//   - 복합 UNIQUE: card_review(card_id, card_version, reviewer_id) · notice_log(profile_id, card_id, stage)
//                  · destination_briefing(profile_id, dest_cell, card_id)
//   - ENUM 값 정의 (Java enum 이 기준): rule_card.card_type · rule_card.status(004 참고) · review_request.status
//                  · review_request.request_type(QUESTION / ERROR_REPORT, 005 참고)
//                  · law_revision.triage_status · card_review.decision
//   - 카드 수명주기·회수 규칙: docs/decisions/004-card-lifecycle.md

const pk = (kr, type = 'BIGINT') => ({ kr, name: 'id', type, cons: 'PK' });
const fk = (name, kr, { nn = true, type = 'BIGINT' } = {}) => ({ kr, name, type, cons: nn ? 'FK, NN' : 'FK' });
const c = (name, kr, type, cons = 'NN') => ({ kr, name, type, cons });
const opt = (name, kr, type) => ({ kr, name, type, cons: '' });

export default {
  config: { V_GUTTER: 300 }, // 앱 1단 아래 gap 에 lane 7개

  zones: [
    {
      id: 'server', label: '서버 DB · MySQL 8.4', source: 'flyway',
      fill: 'light-dark(#cce5ff,#1d3a5c)',
      bands: { 1: 'Depth 1 - 원천', 2: 'Depth 2 - 카드' },
    },
    {
      id: 'app', label: '차량 앱 DB · Room (기기 안)', source: 'room',
      fill: 'light-dark(#eeeeee,#333333)',
      bands: { 1: 'Depth 1 - 사본·프로필', 2: 'Depth 2 - 프로필별 이력' },
    },
  ],

  tables: [
    // ===================== 서버 =====================
    { id: 'law_revision', kr: '법령 개정', zone: 'server', band: 1, col: 1, columns: [
      pk('개정ID'),
      c('law_name', '법령명', 'VARCHAR(100)'),
      c('law_no', '공포 번호', 'VARCHAR(30)', 'UQ, NN'),
      c('source_key', '원문 키', 'VARCHAR(40)'),
      c('promulgated_on', '공포일', 'DATE'),
      c('triage_status', '분류 상태', 'ENUM'),
      c('created_at', '생성일시', 'DATETIME'),
    ] },
    { id: 'reviewer', kr: '검수자', zone: 'server', band: 1, col: 2, columns: [
      pk('검수자ID'),
      c('name', '이름', 'VARCHAR(50)'),
    ] },

    { id: 'review_request', kr: '검수 요청 대기열', zone: 'server', band: 2, col: 0, columns: [
      pk('요청ID'),
      fk('card_id', '카드ID', { nn: false }),
      opt('card_version', '신고 버전', 'INT'),
      c('request_type', '요청 유형', 'ENUM'),
      opt('question_text', '질문 원문', 'VARCHAR(500)'),
      c('status', '상태', 'ENUM'),
      c('created_at', '생성일시', 'DATETIME'),
    ] },
    { id: 'rule_card', kr: '검수 카드', zone: 'server', band: 2, col: 1, columns: [
      pk('카드ID'),
      fk('revision_id', '개정ID', { nn: false }),
      c('card_type', '카드 유형', 'ENUM'),
      c('topic', '주제', 'VARCHAR(40)', 'UQ'),
      c('title', '제목', 'VARCHAR(100)'),
      opt('dont_text', '하지 않을 행동', 'VARCHAR(300)'),
      c('do_text', '할 행동', 'VARCHAR(300)'),
      opt('scene_key', '상황 그림 키', 'VARCHAR(40)'),
      c('tts_text', '읽어 주기 문장', 'VARCHAR(500)'),
      c('legal_basis', '근거 조문', 'VARCHAR(200)'),
      c('law_excerpt', '법령 원문', 'TEXT'),
      opt('penalty_text', '처벌', 'VARCHAR(300)'),
      opt('effective_on', '시행일', 'DATE'),
      opt('exceptions', '예외', 'TEXT'),
      opt('decision_json', '조건·분기', 'JSON'),
      c('status', '상태', 'ENUM'),
      c('version', '버전', 'INT'),
      opt('verified_at', '마지막 확인', 'DATETIME'),
      c('created_at', '생성일시', 'DATETIME'),
      c('updated_at', '수정일시', 'DATETIME'),
    ] },
    { id: 'card_review', kr: '교차 검수', zone: 'server', band: 2, col: 2, columns: [
      pk('검수ID'),
      fk('card_id', '카드ID'),
      fk('reviewer_id', '검수자ID'),
      c('card_version', '검수 버전', 'INT'),
      c('decision', '판정', 'ENUM'),
      c('reviewed_at', '검수일시', 'DATETIME'),
    ] },

    // ===================== 차량 앱 =====================
    { id: 'favorite_place', kr: '자주 가는 장소', zone: 'app', band: 1, col: 0, columns: [
      pk('장소ID', 'INTEGER'),
      c('name', '이름', 'TEXT'),
      c('latitude', '위도', 'REAL'),
      c('longitude', '경도', 'REAL'),
    ] },
    { id: 'cached_card', kr: '검수 카드 사본', zone: 'app', band: 1, col: 1, columns: [
      { kr: '카드ID', name: 'card_id', type: 'INTEGER', cons: 'PK' },
      opt('revision_id', '개정ID', 'INTEGER'),
      c('version', '버전', 'INTEGER'),
      c('card_type', '카드 유형', 'TEXT'),
      opt('topic', '주제', 'TEXT'),
      c('title', '제목', 'TEXT'),
      opt('dont_text', '하지 않을 행동', 'TEXT'),
      c('do_text', '할 행동', 'TEXT'),
      opt('scene_key', '상황 그림 키', 'TEXT'),
      c('tts_text', '읽어 주기 문장', 'TEXT'),
      c('legal_basis', '근거 조문', 'TEXT'),
      c('law_excerpt', '법령 원문', 'TEXT'),
      opt('penalty_text', '처벌', 'TEXT'),
      opt('law_no', '공포 번호', 'TEXT'),
      opt('promulgated_on', '공포일', 'TEXT'),
      opt('effective_on', '시행일', 'TEXT'),
      opt('exceptions', '예외', 'TEXT'),
      opt('decision_json', '조건·분기', 'TEXT'),
      opt('verified_at', '마지막 확인', 'INTEGER'),
      c('is_retired', '회수 여부', 'INTEGER'),
    ] },
    { id: 'driver_profile', kr: '운전자 프로필', zone: 'app', band: 1, col: 3, columns: [
      pk('프로필ID', 'INTEGER'),
      c('name', '이름', 'TEXT'),
      opt('last_selected_at', '마지막 선택', 'INTEGER'),
    ] },
    { id: 'local_school_zone', kr: '보호구역 사본', zone: 'app', band: 1, col: 4, columns: [
      pk('보호구역ID', 'INTEGER'),
      c('latitude', '위도', 'REAL'),
      c('longitude', '경도', 'REAL'),
    ] },

    { id: 'card_confirmation', kr: '확인 이력', zone: 'app', band: 2, col: 1, columns: [
      pk('확인ID', 'INTEGER'),
      fk('profile_id', '프로필ID', { type: 'INTEGER' }),
      fk('card_id', '카드ID', { type: 'INTEGER' }),
      c('card_version', '확인 버전', 'INTEGER'),
      opt('stage', '안내 단계', 'TEXT'),
      c('confirmed_at', '확인 시각', 'INTEGER'),
    ] },
    { id: 'notice_log', kr: '알림 게시 기록', zone: 'app', band: 2, col: 2, columns: [
      pk('게시ID', 'INTEGER'),
      fk('profile_id', '프로필ID', { type: 'INTEGER' }),
      fk('card_id', '카드ID', { type: 'INTEGER' }),
      c('stage', '안내 단계', 'TEXT'),
      c('posted_at', '게시 시각', 'INTEGER'),
      c('repost_count', '재게시 횟수', 'INTEGER'),
    ] },
    { id: 'destination_briefing', kr: '목적지 브리핑 이력', zone: 'app', band: 2, col: 3, columns: [
      pk('브리핑ID', 'INTEGER'),
      fk('profile_id', '프로필ID', { type: 'INTEGER' }),
      fk('card_id', '카드ID', { type: 'INTEGER' }),
      c('card_version', '안내 버전', 'INTEGER'),
      c('dest_cell', '목적지 격자', 'TEXT'),
    ] },
    { id: 'pending_question', kr: '이어 볼 질문', zone: 'app', band: 2, col: 4, columns: [
      pk('질문ID', 'INTEGER'),
      fk('profile_id', '프로필ID', { type: 'INTEGER' }),
      c('topic', '주제', 'TEXT'),
      c('question_text', '질문 원문', 'TEXT'),
      c('created_at', '생성일시', 'INTEGER'),
    ] },
  ],

  relations: [
    // 서버
    { from: 'law_revision', to: 'rule_card', fk: 'revision_id', card: '0..1:0..N' },
    { from: 'rule_card', to: 'card_review', fk: 'card_id', card: '1:0..N' },
    { from: 'rule_card', to: 'review_request', fk: 'card_id', card: '0..1:0..N' },
    { from: 'reviewer', to: 'card_review', fk: 'reviewer_id', card: '1:0..N' },
    // 앱
    { from: 'cached_card', to: 'card_confirmation', fk: 'card_id', card: '1:0..N' },
    { from: 'cached_card', to: 'notice_log', fk: 'card_id', card: '1:0..N' },
    { from: 'cached_card', to: 'destination_briefing', fk: 'card_id', card: '1:0..N' },
    { from: 'driver_profile', to: 'card_confirmation', fk: 'profile_id', card: '1:0..N' },
    { from: 'driver_profile', to: 'notice_log', fk: 'profile_id', card: '1:0..N' },
    { from: 'driver_profile', to: 'destination_briefing', fk: 'profile_id', card: '1:0..N' },
    { from: 'driver_profile', to: 'pending_question', fk: 'profile_id', card: '1:0..N' },
  ],

  flows: [
    { from: 'rule_card', to: 'cached_card', label: '게시본·회수 동기화 (1일 1회)' },
  ],

  note: {
    title: 'DB 역할 분리',
    width: 760,
    sections: [
      { heading: '서버 = 팀이 만들어 모든 운전자에게 똑같이 보내는 것', items: [
        '예: 카드 문장·근거 조문 — 누가 보든 내용이 같다',
      ] },
      { heading: '차량 = 운전자가 쓰면서 생기는 것 (차 밖으로 안 보냄)', items: [
        '예: 누가 어떤 카드를 확인했나, 어디로 가나 — 운전자를 특정하는 개인정보',
        '서버 카드도 사본을 둔다 — 인터넷이 끊겨도, 주행 중 Gleo 질문에도 바로 답하려고',
        '보호구역 목록은 서버 DB 없이 CDN 파일로 받는다 (해시 검증)',
      ] },
    ],
    footer: [
      '새 데이터가 운전자를 특정하거나 위치를 담으면 → 차량 DB',
      '예외: 주차 중 자유 질문 문장, 카드 오류 신고(카드ID·버전)만 서버로 보낸다 (식별자 없음)',
    ],
  },
};
