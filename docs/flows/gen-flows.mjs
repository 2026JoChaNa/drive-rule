// 도메인별 흐름도 생성기 — docs/flows/flows.drawio 를 만든다 (손으로 고치지 말고 여기 데이터를 고친다)
//   node docs/flows/gen-flows.mjs
// 좌표는 격자 산식으로만 정한다. 마지막에 겹침 · 관통 · 포개짐을 검사하고, 실패하면 파일을 쓰지 않는다.
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const LEFT = 120, TOP = 80, COL = 280, ROW = 120;
const SIZE = {
  start: [200, 60], end: [200, 60], skip: [200, 60], proc: [200, 60],
  dec: [220, 100], store: [200, 80], ext: [200, 60],
};
const STYLE = {
  start: 'rounded=1;arcSize=50;fillColor=light-dark(#d5e8d4,#1f3b1f);strokeColor=#82b366;',
  end: 'rounded=1;arcSize=50;fillColor=light-dark(#d5e8d4,#1f3b1f);strokeColor=#82b366;',
  skip: 'rounded=1;arcSize=50;fillColor=light-dark(#eeeeee,#333333);strokeColor=#999999;fontColor=light-dark(#555555,#bbbbbb);',
  proc: 'rounded=0;fillColor=light-dark(#dae8fc,#1d3a5c);strokeColor=#6c8ebf;',
  dec: 'shape=rhombus;perimeter=rhombusPerimeter;fillColor=light-dark(#e1d5e7,#3b2a45);strokeColor=#9673a6;',
  store: 'shape=cylinder3;boundedLbl=1;size=10;fillColor=light-dark(#fff2cc,#4a3d12);strokeColor=#d6b656;',
  ext: 'rounded=0;dashed=1;strokeWidth=2;fillColor=light-dark(#ffffff,#1a1a1a);strokeColor=#b85450;',
};
const LEGEND = [
  ['start', '시작 · 끝'], ['proc', '처리'], ['dec', '판단'], ['store', '저장 · 기록'],
  ['ext', '차 밖으로 나가는 데이터'], ['skip', '안내 · 처리 안 함'],
];

const cellLeft = (c) => LEFT + c * COL; // 열 c 왼쪽 경계 = 열 c-1 과 c 사이 세로 통로
const cx = (c) => LEFT + c * COL + COL / 2;
const cy = (r) => TOP + r * ROW + ROW / 2;

// ── 데이터 ──────────────────────────────────────────────────────────────
// 노드: [id, col, row, kind, label]  (label 의 \n 은 줄바꿈)
// 엣지: { f, t, label?, route? }
//   route 없음 = 같은 열(아래/위) 또는 같은 행(옆)으로 곧게
//   'hv'  = 옆으로 나가 목표 열까지 가로 → 위/아래로 들어감
//   'vh'  = 위/아래로 나가 목표 행까지 세로 → 옆으로 들어감
//   { laneX, exit, entry } = 세로 통로 x 를 따라감 (exit/entry 는 'L'|'R')
//   { laneY, exit, entry } = 가로 통로 y 를 따라감 (exit/entry 는 'T'|'B')
const pages = [
  {
    name: '0 전체 지도',
    title: '전체 지도 — 서버는 모두에게 같은 카드를, 차는 운전자별 기록을',
    zones: [
      { label: '서버 (팀이 운영)', c0: 0, c1: 0, r0: 1, r1: 6, fill: 'light-dark(#e3f0ff,#13263d)' },
      { label: '차량 앱 (운전자별 기록은 여기만)', c0: 2, c1: 4, r0: 1, r1: 6, fill: 'light-dark(#f2f2f2,#262626)' },
    ],
    nodes: [
      ['O1', 0, 1, 'proc', '① 법령 개정 수집\n(매일 자동 + 손 등록)'],
      ['O2', 0, 2, 'proc', '① 일반 운전자 해당 분류'],
      ['O3', 0, 3, 'proc', '② 카드 작성 · 2인 검수'],
      ['O4', 0, 4, 'store', '② 게시된 카드'],
      ['O5', 2, 4, 'proc', '③ 카드 · 보호구역\n사본 받기'],
      ['O6', 2, 5, 'proc', '④ 주행 이벤트 전 안내'],
      ['O7', 3, 5, 'proc', '⑤ ⑥ Gleo 질문에 답하기'],
      ['O8', 2, 6, 'store', '프로필별 안내 · 확인 기록'],
      ['O9', 4, 5, 'ext', '⑦ 오류 신고 · 답변 보류\n(카드 ID · 주제 · 조건만)'],
      ['O10', 0, 6, 'proc', '⑦ 신고 검토 대기열'],
    ],
    edges: [
      { f: 'O1', t: 'O2' }, { f: 'O2', t: 'O3', label: '해당' }, { f: 'O3', t: 'O4', label: '승인 2건' },
      { f: 'O4', t: 'O5', label: '게시본 · 회수 ID' },
      { f: 'O5', t: 'O6' }, { f: 'O5', t: 'O7', route: 'hv' },
      { f: 'O6', t: 'O8' }, { f: 'O7', t: 'O8', route: 'vh', label: '확인' },
      { f: 'O7', t: 'O9', label: '답할 카드 없음' },
      { f: 'O9', t: 'O10', route: { laneY: cy(7), exit: 'B', entry: 'B' } },
      { f: 'O10', t: 'O3', route: { laneX: cellLeft(0), exit: 'L', entry: 'L' }, label: '카드 수정' },
    ],
    note: {
      title: '읽는 법',
      items: [
        '서버는 모든 차에 같은 카드를 보낸다. 누가 받았는지 모른다',
        '차는 프로필별로 무엇을 보여 줬고 확인했는지 기록한다',
        '빨간 점선 = 차 밖으로 나가는 데이터. 프로필 ID · 위치 · 질문 원문은 없다',
        '번호 ①~⑦ 은 다음 페이지의 자세한 흐름',
      ],
    },
  },
  {
    name: '① 법령 수집·분류',
    title: '① 법령 개정 수집 · 분류 — 서버',
    nodes: [
      ['L1', 0, 1, 'start', '매일 06:30\n(또는 운영자가 바로 실행)'],
      ['L2', 0, 2, 'proc', '국가법령정보에서\n최근 90일 개정 조회'],
      ['L3', 0, 3, 'dec', '조회\n성공?'],
      ['L3n', 1, 3, 'skip', '실패 기록\n다음 실행에서 다시'],
      ['L4', 0, 4, 'dec', '이미 받은\n개정 번호?'],
      ['L4n', 1, 4, 'skip', '건너뜀'],
      ['L5', 0, 5, 'store', '분류 대기로 저장'],
      ['L6', 0, 6, 'proc', '운영자가 원문을 보고 분류'],
      ['L7', 0, 7, 'dec', '일반 운전자에게\n해당하나?'],
      ['L7n', 1, 7, 'skip', '해당 없음으로 닫음'],
      ['L8', 0, 8, 'end', '카드 작성 대상\n→ ② 카드 검수 · 게시'],
    ],
    edges: [
      { f: 'L1', t: 'L2' }, { f: 'L2', t: 'L3' },
      { f: 'L3', t: 'L3n', label: '아니오' }, { f: 'L3', t: 'L4', label: '예' },
      { f: 'L4', t: 'L4n', label: '예' }, { f: 'L4', t: 'L5', label: '아니오' },
      { f: 'L5', t: 'L6' }, { f: 'L6', t: 'L7' },
      { f: 'L7', t: 'L7n', label: '아니오' }, { f: 'L7', t: 'L8', label: '예' },
    ],
    note: {
      title: '알아 둘 것',
      items: [
        '감시 법령: 도로교통법 · 시행령 · 시행규칙',
        '수집기는 개정 기록만 만든다. 카드 초안은 사람이 쓴다',
        '운영자가 손으로 등록한 개정도 같은 분류 대기로 들어간다',
        '법령 하나가 실패해도 나머지는 계속 받는다',
        '지금 운영 서버는 인증값이 없어 자동 수집이 꺼져 있다',
      ],
    },
  },
  {
    name: '② 카드 검수·게시',
    title: '② 카드 작성 · 교차 검수 · 게시 — 서버',
    nodes: [
      ['C1', 1, 1, 'start', '해당으로 분류된 개정\n또는 ⑦ 신고 처리'],
      ['C2', 1, 2, 'proc', '카드 작성 (초안)'],
      ['C3', 1, 3, 'dec', '카드 규칙\n검사 통과?'],
      ['C4', 0, 4, 'proc', '초안 고치기'],
      ['C5', 1, 4, 'proc', '검수 요청'],
      ['C6', 1, 5, 'proc', '검수자가 승인 또는 반려'],
      ['C7', 1, 6, 'dec', '반려가\n있나?'],
      ['C8', 1, 7, 'dec', '서로 다른 2명이\n승인했나?'],
      ['C8w', 2, 7, 'proc', '다음 검수 기다림'],
      ['C9', 1, 8, 'store', '게시\n다음 동기화부터 차로'],
      ['C10', 1, 9, 'dec', '게시 후\n바꿀 일?'],
      ['C11', 2, 9, 'proc', '수정하면 초안으로\n차는 마지막 게시본 유지'],
      ['C12', 1, 10, 'end', '회수 — 차에서 내려감\n(되돌릴 수 없음)'],
    ],
    edges: [
      { f: 'C1', t: 'C2' }, { f: 'C2', t: 'C3' },
      { f: 'C3', t: 'C4', route: 'hv', label: '아니오' }, { f: 'C3', t: 'C5', label: '예' },
      { f: 'C4', t: 'C2', route: { laneX: cellLeft(0), exit: 'L', entry: 'L' } },
      { f: 'C5', t: 'C6' }, { f: 'C6', t: 'C7' },
      { f: 'C7', t: 'C4', route: 'hv', label: '예 (1건이면 바로)' }, { f: 'C7', t: 'C8', label: '아니오' },
      { f: 'C8', t: 'C8w', label: '아니오' }, { f: 'C8w', t: 'C6', route: 'vh' },
      { f: 'C8', t: 'C9', label: '예' }, { f: 'C9', t: 'C10' },
      { f: 'C10', t: 'C11', label: '수정' },
      { f: 'C11', t: 'C2', route: { laneX: cellLeft(3), exit: 'R', entry: 'R' } },
      { f: 'C10', t: 'C12', label: '회수' },
    ],
    note: {
      title: '알아 둘 것',
      items: [
        '검수 요청 때 서버가 칸 사이 규칙 · 조건 표를 검사한다',
        '고친 뒤 다시 요청하면 버전이 오르고, 앞 버전 승인은 세지 않는다',
        '차로 나가는 것은 게시된 카드와 회수된 카드 ID뿐이다',
        '회수는 어느 단계에서든 할 수 있다 (회수된 카드 제외)',
        '주제 하나에 대표 카드 1장 — Gleo 가 이 카드로 답한다',
      ],
    },
  },
  {
    name: '③ 사본 동기화',
    title: '③ 카드 · 보호구역 사본 동기화 — 차량 ↔ 서버',
    nodes: [
      ['S1', 0, 1, 'start', '시동 · 하루 1회'],
      ['S2', 0, 2, 'ext', '카드 변경분 요청\n(지난번 받은 위치 표시만)'],
      ['S3', 0, 3, 'dec', '서버가 전체를\n다시 받으라나?'],
      ['S3b', 1, 3, 'proc', '위치 표시를 버리고\n처음부터 요청'],
      ['S4', 0, 4, 'proc', '받은 카드 덮어쓰기\n회수된 카드 내리기'],
      ['S5', 0, 5, 'store', '카드 사본\n(한 번에 반영)'],
      ['S6', 0, 6, 'ext', '보호구역 목록 버전 확인'],
      ['S7', 0, 7, 'dec', '바뀌었나?'],
      ['S7n', 1, 7, 'skip', '그대로 둠'],
      ['S8', 0, 8, 'proc', '새 파일 받기'],
      ['S9', 0, 9, 'dec', '파일 해시가\n맞나?'],
      ['S9n', 1, 9, 'skip', '버리고 옛 사본 사용'],
      ['S10', 0, 10, 'store', '보호구역 사본\n통째로 교체'],
    ],
    edges: [
      { f: 'S1', t: 'S2' }, { f: 'S2', t: 'S3' },
      { f: 'S3', t: 'S3b', label: '예' }, { f: 'S3b', t: 'S2', route: 'vh' },
      { f: 'S3', t: 'S4', label: '아니오' }, { f: 'S4', t: 'S5' }, { f: 'S5', t: 'S6' }, { f: 'S6', t: 'S7' },
      { f: 'S7', t: 'S7n', label: '아니오' }, { f: 'S7', t: 'S8', label: '예' }, { f: 'S8', t: 'S9' },
      { f: 'S9', t: 'S9n', label: '아니오' }, { f: 'S9', t: 'S10', label: '예' },
    ],
    note: {
      title: '알아 둘 것',
      items: [
        '서버는 모든 차에 같은 답을 준다. 차 · 사용자를 구분하지 않는다',
        '목록에 없다고 사본을 지우지 않는다. 내리는 신호는 회수 ID뿐',
        '게시 후 수정 · 검수 중인 카드는 차에 마지막 게시본이 남는다',
        '보호구역 사본이 없으면 보호구역 · 목적지 안내는 쉰다 (틀린 안내보다 안내 없음)',
      ],
    },
  },
  {
    name: '④ 이벤트 전 안내',
    title: '④ 주행 이벤트 전 안내 — 차량 (핵심 기능 1)',
    nodes: [
      ['E1', 0, 1, 'start', '주행 이벤트 감지'],
      ['E2', 0, 2, 'dec', '이 이벤트의 카드가\n사본에 있나?'],
      ['E2n', 1, 2, 'skip', '안내 안 함'],
      ['E3', 0, 3, 'proc', '안내 단계 정하기\n시행일 전 공포 · 후 시행 후'],
      ['E4', 0, 4, 'dec', '이 프로필이 이 단계를\n이미 받았나?'],
      ['E4n', 1, 4, 'skip', '안내 안 함 (이미 알림)'],
      ['E5', 0, 5, 'dec', '이번 주행에서\n2건을 넘었나?'],
      ['E5n', 1, 5, 'skip', '안내 안 함'],
      ['E6', 0, 6, 'dec', '지금 음성 · 화면\n안내가 되나?'],
      ['E6n', 1, 6, 'skip', '안내 안 함'],
      ['E7', 0, 7, 'proc', '음성 한 문장 + 카드 표시'],
      ['E8', 0, 8, 'store', '알림 게시 기록\n(프로필 · 카드 · 단계)'],
      ['E9', 0, 9, 'dec', '운전자가\n확인했나?'],
      ['E9y', 1, 9, 'store', '확인 이력 저장'],
      ['E10', 0, 10, 'end', '끝'],
    ],
    edges: [
      { f: 'E1', t: 'E2' },
      { f: 'E2', t: 'E2n', label: '아니오' }, { f: 'E2', t: 'E3', label: '예' }, { f: 'E3', t: 'E4' },
      { f: 'E4', t: 'E4n', label: '예' }, { f: 'E4', t: 'E5', label: '아니오' },
      { f: 'E5', t: 'E5n', label: '예' }, { f: 'E5', t: 'E6', label: '아니오' },
      { f: 'E6', t: 'E6n', label: '아니오' }, { f: 'E6', t: 'E7', label: '예' },
      { f: 'E7', t: 'E8' }, { f: 'E8', t: 'E9' },
      { f: 'E9', t: 'E9y', label: '예' }, { f: 'E9', t: 'E10', label: '아니오' },
    ],
    note: {
      title: '알아 둘 것',
      items: [
        '이벤트: 어린이보호구역 진입 · 주정차 · 우회전 후 횡단보도',
        '사본 카드 조건: 게시됨 · 회수 안 됨 · 일반 운전자 대상',
        '이미 받았나는 차 안 기록으로만 본다. 서버는 모른다',
        '그래서 한 카드는 프로필마다 시행 전 1번, 시행 후 1번 나온다',
      ],
      open: [
        '시행일이 지난 뒤 처음 받은 카드에 공포 안내도 하는지',
        '확인하지 않았을 때 다시 띄우는 규칙',
        '2건을 넘을 때 무엇을 먼저 고르는지',
      ],
    },
  },
  {
    name: '⑤ Gleo 규칙 묻기',
    title: '⑤ Gleo 규칙 묻기 — 차량 (핵심 기능 2)',
    nodes: [
      ['A1', 1, 1, 'start', '운전자가 규칙을 물음\n예: 우회전 어떻게 해?'],
      ['A2', 1, 2, 'proc', 'Gleo 가 주제 · 조건을\n골라 앱에 넘김'],
      ['A3', 1, 3, 'dec', '주제의 카드가\n사본에 있나?'],
      ['H1', 2, 3, 'proc', '답변 보류 문장으로 답'],
      ['H2', 2, 4, 'ext', '답변 보류 신고\n(주제 · 조건만)'],
      ['H3', 2, 5, 'end', '끝'],
      ['A4', 1, 4, 'dec', '조건에 따라\n답이 갈리나?'],
      ['A5', 1, 5, 'proc', '받은 조건과 어긋나지\n않는 문장 모으기'],
      ['A6', 1, 6, 'dec', '후보가\n2개 이하?'],
      ['A6b', 0, 6, 'proc', '빠진 조건 하나 되묻기'],
      ['A7', 1, 7, 'dec', '보류 행인가?'],
      ['A8', 1, 8, 'proc', '검수 문장 그대로 답'],
      ['A9', 1, 9, 'end', '끝 — 확인했어는 ⑥'],
    ],
    edges: [
      { f: 'A1', t: 'A2' }, { f: 'A2', t: 'A3' },
      { f: 'A3', t: 'H1', label: '아니오' }, { f: 'H1', t: 'H2' }, { f: 'H2', t: 'H3' },
      { f: 'A3', t: 'A4', label: '예' },
      { f: 'A4', t: 'A8', route: { laneX: cellLeft(1), exit: 'L', entry: 'L' }, label: '아니오' },
      { f: 'A4', t: 'A5', label: '예' }, { f: 'A5', t: 'A6' },
      { f: 'A6', t: 'A6b', label: '아니오' },
      { f: 'A6b', t: 'A2', route: { laneX: cellLeft(0), exit: 'L', entry: 'L' } },
      { f: 'A6', t: 'A7', label: '예' },
      { f: 'A7', t: 'H1', route: { laneX: cellLeft(3), exit: 'R', entry: 'R' }, label: '예' },
      { f: 'A7', t: 'A8', label: '아니오' }, { f: 'A8', t: 'A9' },
    ],
    note: {
      title: '알아 둘 것',
      items: [
        '질문할 때 서버에 아무것도 보내지 않는다. 차 사본으로 7초 안에 답한다',
        '주제가 기타면 카드가 없어 항상 답변 보류',
        '조건: 우회전 신호등 · 전방 신호 · 보행 신호 · 보행자',
        '모른다고 한 조건은 비워 둔다. 후보가 3개 이상이면 되묻는다',
      ],
      open: ['Gleo 가 검수 문장을 바꿔 말하지 않는지 (11/18 검증)'],
    },
  },
  {
    name: '⑥ 바뀐 규칙·확인',
    title: '⑥ Gleo 바뀐 규칙 목록 · 확인 — 차량',
    nodes: [
      ['G1', 0, 1, 'start', '이번 달 바뀐 규칙\n알려 줘'],
      ['G2', 0, 2, 'dec', '기간을\n말했나?'],
      ['G2b', 1, 2, 'proc', '언제부터인지 되묻기'],
      ['G3', 0, 3, 'proc', '사본에서 고르기\n일반 운전자 · 시행일이 그 뒤'],
      ['G4', 0, 4, 'proc', '시행일 순으로\n건수와 제목을 읽어 줌'],
      ['G5', 0, 5, 'end', '끝'],
      ['K1', 3, 1, 'start', '확인했어 /\n다시 말해 줘'],
      ['K2', 3, 2, 'dec', '방금 읽어 준\n카드가 있나?'],
      ['K2n', 4, 2, 'skip', '확인할 카드 없음'],
      ['K3', 3, 3, 'dec', '확인인가?'],
      ['K3n', 4, 3, 'proc', '같은 문장 다시 읽기'],
      ['K4', 3, 4, 'store', '확인 이력 저장\n(프로필 · 카드 · 단계)'],
      ['K5', 3, 5, 'end', '끝'],
    ],
    edges: [
      { f: 'G1', t: 'G2' }, { f: 'G2', t: 'G2b', label: '아니오' }, { f: 'G2b', t: 'G1', route: 'vh' },
      { f: 'G2', t: 'G3', label: '예' }, { f: 'G3', t: 'G4' }, { f: 'G4', t: 'G5' },
      { f: 'K1', t: 'K2' }, { f: 'K2', t: 'K2n', label: '아니오' }, { f: 'K2', t: 'K3', label: '예' },
      { f: 'K3', t: 'K3n', label: '다시 듣기' }, { f: 'K3', t: 'K4', label: '확인' }, { f: 'K4', t: 'K5' },
    ],
    note: {
      title: '알아 둘 것',
      items: [
        '목록에는 일부 운전자 대상 카드가 빠진다 (물으면 ⑤ 로 답함)',
        '확인에는 카드 번호를 받지 않는다. 앱이 이 대화에서 마지막으로 읽은 카드를 기억한다',
        '확인 이력도 차 안에만 저장한다',
      ],
      open: ['방금 읽은 카드가 없을 때의 답 문장'],
    },
  },
  {
    name: '⑦ 신고 처리',
    title: '⑦ 오류 신고 · 답변 보류 처리 — 차량 → 서버',
    nodes: [
      ['R1', 0, 1, 'start', '카드 화면에서\n오류 신고'],
      ['R2', 0, 2, 'ext', '카드 ID · 버전만 전송'],
      ['R1b', 2, 1, 'start', 'Gleo 질문에\n답할 카드 없음 (⑤)'],
      ['R2b', 2, 2, 'ext', '주제 · 조건만 전송'],
      ['R3', 1, 3, 'store', '검수 요청 대기열\n(서버)'],
      ['R4', 1, 4, 'proc', '운영자가 검토\n(주제별로 모아 보기)'],
      ['R5', 1, 5, 'dec', '처리할\n신고인가?'],
      ['R5n', 2, 5, 'skip', '닫음 (중복 · 범위 밖)'],
      ['R6', 1, 6, 'proc', '카드 작성 · 수정\n→ ② 검수 흐름'],
      ['R7', 1, 7, 'end', '처리 완료로 표시'],
    ],
    edges: [
      { f: 'R1', t: 'R2' }, { f: 'R1b', t: 'R2b' },
      { f: 'R2', t: 'R3', route: 'vh' }, { f: 'R2b', t: 'R3', route: 'vh' },
      { f: 'R3', t: 'R4' }, { f: 'R4', t: 'R5' },
      { f: 'R5', t: 'R5n', label: '아니오' }, { f: 'R5', t: 'R6', label: '예' }, { f: 'R6', t: 'R7' },
    ],
    note: {
      title: '알아 둘 것',
      items: [
        '신고에는 운전자 식별자가 없어 신고한 사람에게 결과를 알릴 수 없다',
        '앱 재시도로 같은 신고가 여러 번 올 수 있다 — 하나만 처리하고 나머지는 닫는다',
        '답변 보류를 주제별로 세면 먼저 쓸 카드가 보인다',
      ],
    },
  },
];

// ── 생성 ────────────────────────────────────────────────────────────────
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const lbl = (s) => esc(s).replace(/\n/g, '&#xa;');
const errors = [];

function geom(n) {
  const [w, h] = SIZE[n.kind];
  return { x: cellLeft(n.col) + (COL - w) / 2, y: TOP + n.row * ROW + (ROW - h) / 2, w, h };
}
function anchor(g, side) {
  const m = { T: [g.x + g.w / 2, g.y], B: [g.x + g.w / 2, g.y + g.h], L: [g.x, g.y + g.h / 2], R: [g.x + g.w, g.y + g.h / 2] };
  return m[side];
}
const SIDE_XY = { T: [0.5, 0], B: [0.5, 1], L: [0, 0.5], R: [1, 0.5] };

function route(e, a, b) {
  const ga = a.g, gb = b.g;
  const acx = ga.x + ga.w / 2, acy = ga.y + ga.h / 2, bcx = gb.x + gb.w / 2, bcy = gb.y + gb.h / 2;
  const r = e.route;
  if (!r) {
    if (a.col === b.col) return b.row > a.row ? { exit: 'B', entry: 'T', pts: [] } : { exit: 'T', entry: 'B', pts: [] };
    if (a.row === b.row) return b.col > a.col ? { exit: 'R', entry: 'L', pts: [] } : { exit: 'L', entry: 'R', pts: [] };
    errors.push(`${e.f}→${e.t}: 같은 행 · 열이 아니면 route 를 정해야 함`);
    return { exit: 'B', entry: 'T', pts: [] };
  }
  if (r === 'hv') return { exit: bcx > acx ? 'R' : 'L', entry: bcy > acy ? 'T' : 'B', pts: [[bcx, acy]] };
  if (r === 'vh') return { exit: bcy > acy ? 'B' : 'T', entry: acx < bcx ? 'L' : 'R', pts: [[acx, bcy]] };
  if (r.laneX !== undefined) return { exit: r.exit, entry: r.entry, pts: [[r.laneX, acy], [r.laneX, bcy]] };
  if (r.laneY !== undefined) return { exit: r.exit, entry: r.entry, pts: [[acx, r.laneY], [bcx, r.laneY]] };
  errors.push(`${e.f}→${e.t}: 모르는 route`);
  return { exit: 'B', entry: 'T', pts: [] };
}

// 선분이 사각형 내부를 지나는가 (경계에 닿는 것은 허용)
function segHitsRect([x1, y1], [x2, y2], g) {
  const [lx, hx, ly, hy] = [Math.min(x1, x2), Math.max(x1, x2), Math.min(y1, y2), Math.max(y1, y2)];
  return hx > g.x && lx < g.x + g.w && hy > g.y && ly < g.y + g.h;
}
const rectsTouch = (a, b, gap) =>
  !(a.x + a.w + gap <= b.x || b.x + b.w + gap <= a.x || a.y + a.h + gap <= b.y || b.y + b.h + gap <= a.y);

function noteHeight(note) {
  const lines = note.items.length + (note.open ? note.open.length + 1 : 0);
  return Math.ceil((50 + lines * 34) / 10) * 10;
}

function buildPage(p, pi) {
  const cells = [];
  const nodes = new Map();
  for (const [id, col, row, kind, label] of p.nodes) {
    if (nodes.has(id)) errors.push(`${p.name}: 노드 id 중복 ${id}`);
    const n = { id, col, row, kind, label };
    n.g = geom(n);
    nodes.set(id, n);
  }
  const all = [...nodes.values()];
  for (let i = 0; i < all.length; i++)
    for (let j = i + 1; j < all.length; j++)
      if (all[i].col === all[j].col && all[i].row === all[j].row) errors.push(`${p.name}: 같은 칸 ${all[i].id} · ${all[j].id}`);

  const maxCol = Math.max(...all.map((n) => n.col));
  const maxRow = Math.max(...all.map((n) => n.row));
  // 메모 · 범례: 노드 오른쪽 한 열 띄워서
  const sideX = cellLeft(maxCol + 1) + 80;
  const noteG = { x: sideX, y: TOP + ROW + 30, w: 440, h: noteHeight(p.note) };
  const legendG = { x: sideX, y: noteG.y + noteG.h + 40, w: 440, h: 40 + Math.ceil(LEGEND.length / 2) * 40 };
  const titleG = { x: LEFT, y: 20, w: Math.max(900, sideX + 440 - LEFT), h: 40 };
  const blockers = [...all.map((n) => ({ id: n.id, g: n.g })), { id: '메모', g: noteG }, { id: '범례', g: legendG }];
  for (const b of blockers.slice(-2))
    for (const n of all) if (rectsTouch(b.g, n.g, 40)) errors.push(`${p.name}: ${b.id} 가 ${n.id} 와 겹침`);

  const P = `p${pi}`;
  cells.push(`<mxCell id="${P}_title" value="${lbl(p.title)}" style="text;html=1;fontSize=18;fontStyle=1;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="${titleG.x}" y="${titleG.y}" width="${titleG.w}" height="${titleG.h}" as="geometry"/></mxCell>`);

  for (const z of p.zones ?? []) {
    const x = cellLeft(z.c0) + 20, y = TOP + z.r0 * ROW - 50;
    const w = cellLeft(z.c1 + 1) - 20 - x, h = TOP + (z.r1 + 1) * ROW - y;
    cells.push(`<mxCell id="${P}_z${z.c0}" value="${lbl(z.label)}" style="rounded=1;arcSize=4;fillColor=${z.fill};strokeColor=none;verticalAlign=top;align=left;spacingLeft=12;spacingTop=6;fontStyle=1;fontSize=13;" vertex="1" parent="1"><mxGeometry x="${x}" y="${y}" width="${w}" height="${h}" as="geometry"/></mxCell>`);
  }

  for (const n of all) {
    const { x, y, w, h } = n.g;
    cells.push(`<mxCell id="${P}_${n.id}" value="${lbl(n.label)}" style="${STYLE[n.kind]}whiteSpace=wrap;html=1;fontSize=12;" vertex="1" parent="1"><mxGeometry x="${x}" y="${y}" width="${w}" height="${h}" as="geometry"/></mxCell>`);
  }

  const segs = [];
  const used = new Set();
  p.edges.forEach((e, k) => {
    const a = nodes.get(e.f), b = nodes.get(e.t);
    if (!a || !b) { errors.push(`${p.name}: 없는 노드 ${e.f}→${e.t}`); return; }
    const r = route(e, a, b);
    for (const [n, side] of [[a, r.exit], [b, r.entry]]) {
      const key = `${n.id}.${side}`;
      // 같은 변에 두 엣지 머리가 붙으면 포개진다 — 들어오는 엣지끼리 합류만 허용
      if (used.has(key) && !(n === b && used.has(`${key}.in`))) errors.push(`${p.name}: ${key} 앵커를 두 엣지가 씀`);
      used.add(key);
      if (n === b) used.add(`${key}.in`);
    }
    const pts = [anchor(a.g, r.exit), ...r.pts, anchor(b.g, r.entry)];
    for (let i = 0; i + 1 < pts.length; i++) {
      const [p1, p2] = [pts[i], pts[i + 1]];
      if (p1[0] !== p2[0] && p1[1] !== p2[1]) errors.push(`${p.name}: ${e.f}→${e.t} 대각선`);
      for (const bl of blockers) {
        if ((bl.id === a.id && i === 0) || (bl.id === b.id && i === pts.length - 2)) continue;
        if (segHitsRect(p1, p2, bl.g)) errors.push(`${p.name}: ${e.f}→${e.t} 가 ${bl.id} 를 관통`);
      }
      segs.push({ e: `${e.f}→${e.t}`, to: e.t, p1, p2 });
    }
    for (const v of pts.flat()) if (v % 10) errors.push(`${p.name}: ${e.f}→${e.t} 격자 밖 좌표 ${v}`);
    const [ex, ey] = SIDE_XY[r.exit], [nx, ny] = SIDE_XY[r.entry];
    const style = `edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;html=1;endArrow=block;endFill=1;fontSize=11;labelBackgroundColor=default;exitX=${ex};exitY=${ey};exitDx=0;exitDy=0;entryX=${nx};entryY=${ny};entryDx=0;entryDy=0;`;
    const ptsXml = r.pts.length ? `<Array as="points">${r.pts.map(([x, y]) => `<mxPoint x="${x}" y="${y}"/>`).join('')}</Array>` : '';
    const lx = e.label ? ' x="-0.4"' : '';
    cells.push(`<mxCell id="${P}_e${k}" value="${lbl(e.label ?? '')}" style="${style}" edge="1" parent="1" source="${P}_${a.id}" target="${P}_${b.id}"><mxGeometry relative="1"${lx} as="geometry">${ptsXml}</mxGeometry></mxCell>`);
  });

  // 서로 다른 엣지가 같은 직선 위에서 겹치면 포개져 보인다 (같은 목표로 합류하는 끝 구간은 허용)
  for (let i = 0; i < segs.length; i++)
    for (let j = i + 1; j < segs.length; j++) {
      const s = segs[i], t = segs[j];
      if (s.e === t.e) continue;
      const vert = (q) => q.p1[0] === q.p2[0];
      if (vert(s) !== vert(t)) continue;
      const fixed = vert(s) ? 0 : 1, run = 1 - fixed;
      if (s.p1[fixed] !== t.p1[fixed]) continue;
      const [a1, a2] = [s.p1[run], s.p2[run]].sort((x, y) => x - y);
      const [b1, b2] = [t.p1[run], t.p2[run]].sort((x, y) => x - y);
      if (Math.min(a2, b2) - Math.max(a1, b1) > 0 && s.to !== t.to) errors.push(`${p.name}: ${s.e} 와 ${t.e} 가 포개짐`);
    }

  // 메모
  const noteHtml = [
    `<b>${esc(p.note.title)}</b>`,
    ...p.note.items.map((s) => `• ${esc(s)}`),
    ...(p.note.open ? [`<b><font color="#b85450">정해지지 않은 것</font></b>`, ...p.note.open.map((s) => `• ${esc(s)}`)] : []),
  ].join('<br>');
  cells.push(`<mxCell id="${P}_note" value="${esc(noteHtml)}" style="shape=note;size=16;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacing=12;fontSize=12;fillColor=light-dark(#fffbe6,#3a3520);strokeColor=#d6b656;" vertex="1" parent="1"><mxGeometry x="${noteG.x}" y="${noteG.y}" width="${noteG.w}" height="${noteG.h}" as="geometry"/></mxCell>`);

  // 범례
  cells.push(`<mxCell id="${P}_lg" value="범례" style="rounded=0;html=1;verticalAlign=top;align=left;spacingLeft=10;fontStyle=1;fontSize=12;fillColor=none;strokeColor=#999999;" vertex="1" parent="1"><mxGeometry x="${legendG.x}" y="${legendG.y}" width="${legendG.w}" height="${legendG.h}" as="geometry"/></mxCell>`);
  LEGEND.forEach(([kind, text], i) => {
    const x = legendG.x + 20 + (i % 2) * 210, y = legendG.y + 30 + Math.floor(i / 2) * 40;
    const sw = kind === 'dec' ? 40 : 50;
    cells.push(`<mxCell id="${P}_lg${i}" value="" style="${STYLE[kind]}html=1;" vertex="1" parent="1"><mxGeometry x="${x}" y="${y}" width="${sw}" height="30" as="geometry"/></mxCell>`);
    cells.push(`<mxCell id="${P}_lg${i}t" value="${esc(text)}" style="text;html=1;align=left;verticalAlign=middle;fontSize=11;" vertex="1" parent="1"><mxGeometry x="${x + 60}" y="${y}" width="140" height="30" as="geometry"/></mxCell>`);
  });

  const W = sideX + 440 + LEFT, H = Math.max(TOP + (maxRow + 2) * ROW, legendG.y + legendG.h + 40);
  return `<diagram id="flow-${pi}" name="${esc(p.name)}"><mxGraphModel dx="1400" dy="900" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${W}" pageHeight="${H}" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>${cells.join('')}</root></mxGraphModel></diagram>`;
}

const diagrams = pages.map(buildPage);
if (errors.length) {
  console.error(errors.map((e) => `❌ ${e}`).join('\n'));
  process.exit(1);
}
const out = join(dirname(fileURLToPath(import.meta.url)), 'flows.drawio');
writeFileSync(out, `<mxfile host="drive-rule gen-flows">${diagrams.join('')}</mxfile>\n`);
console.log(`✅ ${pages.length}페이지 · 겹침 0 · 관통 0 · 포개짐 0 → ${out}`);
