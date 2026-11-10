---
name: drive-rule-erd
description: |
  DRIVE RULE 의 ERD(drawio)를 그리거나 갱신할 때 쓴다 — "ERD 그려줘", "ERD 업데이트", "ERD 갱신",
  "엔티티 관계도", "테이블 설계 그려", "스키마 ERD", "ERD.drawio" 등. 서버 DB(MySQL, Flyway)와
  차량 앱 DB(Room) 두 구역을 한 장에 그린다. 테이블 정의는 docs/db/erd.data.mjs 에 데이터로 쓰고,
  같은 폴더의 gen-erd.mjs 로 docs/db/ERD.drawio 를 생성한다(손으로 XML 을 찍지 않는다).
  엔티티는 절대 겹치지 않게, 관계선 머리·몸통은 절대 포개지지 않게, 좌표는 산식으로 계산한다.
  격자·엣지 원칙은 [[drawio-ia]] 와 같다.
---

# DRIVE RULE ERD (drawio) 작성 가이드라인

ERD 는 **데이터 파일 → 생성기 → drawio** 로만 만든다.

| 파일 | 역할 |
|---|---|
| `docs/db/erd.data.mjs` | 테이블·컬럼·관계·데이터 이동을 적는 **유일한 편집 대상** |
| `.claude/skills/drive-rule-erd/gen-erd.mjs` | 높이·밴드·앵커·lane 계산 → XML → 자가검증 → Flyway drift 경고 |
| `docs/db/ERD.drawio` | 생성 산출물. **직접 고치지 않는다** (다음 생성 때 덮어씀) |

```bash
node .claude/skills/drive-rule-erd/gen-erd.mjs            # 기본 경로
node .claude/skills/drive-rule-erd/gen-erd.mjs <data.mjs> <out.drawio>
```

마지막 줄이 `✅ 겹침 0 · 앵커 중복 0 · 엣지 관통 0 · 포개짐 0` 이어야 통과. 실패하면 파일을 쓰지 않는다.

---

## 0. source of truth — 단계별로 다르다

| 단계 | 서버 구역 기준 | 앱 구역 기준 |
|---|---|---|
| **설계 모드** (마이그레이션·엔티티 아직 없음) | 기획(Notion 🚦 기획 페이지) → `erd.data.mjs` | 기획 → `erd.data.mjs` |
| **구현 이후** | `server/src/main/resources/db/migration/V*__*.sql` | `android/` 의 Room `@Entity` |

- 구현 이후에는 **코드가 기준, ERD 는 파생물**이다. "ERD 에 있으니 맞겠지" 금지.
- 서버 구역(`source: 'flyway'`)은 생성기가 V*.sql 의 `CREATE TABLE` / `ALTER TABLE ADD·DROP COLUMN` 을 읽어
  테이블·컬럼·타입 drift 를 `⚠️ DRIFT` 로 출력한다. `MODIFY`/`CHANGE`/`RENAME` 은 파싱하지 않고 수동 확인 경고만 낸다.
- 앱 구역(`source: 'room'`)은 자동 대조가 없다. `@Entity(tableName=…)` · `@PrimaryKey` · `@ColumnInfo` ·
  `ForeignKey(…)` 를 읽어 **drift 표를 텍스트로 먼저 뽑은 뒤** 데이터 파일을 고친다.
- 작업 시작 시 drift 목록부터 사용자에게 보여 준다. 설계 모드에서 기획과 다르게 정한 것은 "임의 결정"으로 따로 알린다.

---

## 1. 데이터 파일 형식 (`erd.data.mjs`)

```js
export default {
  // config: { V_GUTTER: 260 },   // lane 이 모자라면 상수만 키운다 (§2.2)
  zones: [
    { id: 'server', label: '서버 DB · MySQL 8.4', source: 'flyway',
      fill: 'light-dark(#cce5ff,#1d3a5c)', bands: { 1: 'Depth 1 - 법령·검수자' } },
    { id: 'app', label: '차량 앱 DB · Room', source: 'room',
      fill: 'light-dark(#eeeeee,#333333)' },
  ],
  tables: [
    { id: 'rule_card', kr: '검수 카드', zone: 'server', band: 2, col: 1, columns: [
      { kr: '카드ID', name: 'id', type: 'BIGINT', cons: 'PK' },
      { kr: '개정ID', name: 'revision_id', type: 'BIGINT', cons: 'FK' },        // NULL 허용
      { kr: '제목', name: 'title', type: 'VARCHAR(100)', cons: 'NN' },
    ] },
  ],
  relations: [   // 부모(PK) → 자식(FK). fk 는 자식 컬럼명
    { from: 'law_revision', to: 'rule_card', fk: 'revision_id', card: '0..1:0..N' },
  ],
  flows: [       // 구역 사이 데이터 이동 (FK 아님, 보라 점선)
    { from: 'rule_card', to: 'cached_card', label: '게시본 동기화' },
  ],
  overrides: {   // 자동 배치로 안 풀릴 때만 (§4.4)
    // 'R_law_revision__rule_card__revision_id': { fromShift: 20, toShift: -20 },
  },
  note: {        // 팀 공유 메모 (선택). 범례 왼쪽에 노란 메모로 그린다
    title: 'DB 역할 분리', width: 1040,
    sections: [{ heading: '서버 DB = …', items: ['…', '…'] }],
    footer: ['근거: …'],
  },
};
```

- **ERD 에 설명을 남길 때는 `note` 를 쓴다.** drawio 에서 직접 메모를 붙이면 다음 생성 때 사라진다.
  지금 메모는 "서버 DB 와 차량 DB 에 각각 무엇이 들어가는가"이다 — 테이블을 옮기거나 추가하면 메모 항목도 함께 고친다.

- `id` = 실제 테이블명 (snake_case). 헤더는 `테이블명 (한글명)` 으로 자동 생성.
- `zone` 은 위→아래 순서, `band` 는 구역 안 depth(1부터), `col` 은 0부터. 같은 밴드에 같은 col 두 개 금지.
- 컬럼 순서: **PK → FK → 일반 → 타임스탬프(created_at, updated_at)**. 구현 이후엔 SQL/Entity 선언 순서를 따른다.

생성기가 먼저 막는 데이터 오류: 테이블/컬럼 중복, 없는 테이블 참조, FK 표기 없는 관계 컬럼,
**관계가 없는 FK 컬럼(누락 엣지)**, 미지원 카디널리티. NULL 여부와 카디널리티가 어긋나면 경고.

---

## 2. 겹침 금지 & 간격 — 절대 규칙

### 2.1 불변 규칙

1. **엔티티는 절대 겹치지 않는다.** (hard fail)
2. **관계선 머리는 포개지지 않는다.** 한 테이블의 (변, 위치)는 엣지마다 전부 다르다.
3. **서로 다른 두 엣지의 선분이 같은 직선 위에서 구간이 겹치면(collinear) 안 된다.**
4. 몸통이 한 점에서 교차하는 것은 허용.

### 2.2 상수 (`config` 로 덮어쓰기 가능)

```
TABLE_W = 400, HEADER_H = 30, ROW_H = 30   → table.height = 30 + 30 × 컬럼 수
H_GUTTER = 320   같은 밴드 좌우 테이블 사이 (gap 안에 수직 lane 최대 9개)
V_GUTTER = 220   위 밴드 실제 바닥 ~ 아래 밴드 top (gap 안에 수평 lane 최대 5개)
LEFT = 240       왼쪽 여백 = 구역·밴드 라벨 전용 열 (엣지가 지나가지 않음)
LANE_STEP = 30   lane 간격
ZONE_EXTRA = 100 구역 경계(구분선 + 구역 라벨)에 추가하는 높이
```

- `x = LEFT + col × (TABLE_W + H_GUTTER)`
- `band.y = 위 밴드의 max(y + height) + V_GUTTER` (구역이 바뀌면 `+ ZONE_EXTRA`). **고정 y 금지.**
- `LANE OVERFLOW` 가 나면 테이블을 옮기기 전에 해당 GUTTER 를 키운다.

---

## 3. drawio 빌딩 블록

- 테이블: `shape=table` (헤더 30) + 행 `shape=tableRow` (30) + 셀 `shape=partialRectangle` 4개.
- 셀: **한글명 100 | 컬럼명 160 | 타입 90 | 제약 50**. PK 행은 굵게.
- 구역 색: 서버 = 파랑, 앱 = 회색 (`light-dark()` 로 다크 모드 대응).
- 구역 경계에 가로 점선 + 구역 라벨, 밴드마다 왼쪽 여백에 `Depth N - …` 라벨.
- 범례는 맨 아래 오른쪽: 카디널리티 4종 + 데이터 이동 + 제약 설명 + 구역 색. 생성기가 매번 다시 그린다.
- 메모(`note`)는 범례 왼쪽 같은 높이 (자리가 없으면 범례 아래). 높이는 줄 수로 계산하며 한 줄 18px 기준 —
  한 항목이 메모 폭보다 길면 줄바꿈되어 넘칠 수 있으니 항목은 짧게, 필요하면 `width` 를 키운다.

---

## 4. 엣지 라우팅 (생성기가 하는 일 — 결과를 읽을 줄 알아야 한다)

### 4.1 경로 종류

| 두 테이블 위치 | 출구 → 입구 | 경유 |
|---|---|---|
| 같은 밴드, 이웃 col | 옆변 → 옆변 | 두 col 사이 gap 의 수직 lane |
| 같은 밴드, 사이에 col 있음 | 하단 → 하단 | 밴드 아래 gap 의 수평 lane |
| 바로 아래 밴드 | 하단 → 상단 | 사이 gap 의 수평 lane |
| 2단 이상 아래 | 하단 → 상단 | 아래 gap → 자식 col 왼쪽 gap 수직 lane → 자식 위 gap |
| 위쪽 (역방향) | 상단 → 하단 | 위와 대칭 |

- 모든 엣지에 `exitX/exitY/entryX/entryY` 명시, waypoint 는 전부 10 의 배수.
- lane 은 gap 마다 카운터로 30px 씩 배정 → **같은 lane 을 두 엣지가 공유하지 않는다.**

### 4.2 앵커 분산 (머리 겹침 방지)

- 한 변에 붙는 엣지는 반대편 테이블 위치 순으로 정렬해 등분한다.
- **하단·우측 앵커 = 20 의 배수, 상단·좌측 앵커 = 20 의 배수 + 10.**
  위 테이블의 출구 수직선과 아래 테이블의 입구 수직선이 같은 x 를 쓸 수 없어, 같은 gap 에서의 포개짐이 구조적으로 막힌다.
  (그래서 1:1 로 곧게 내려가는 엣지도 10px 꺾임이 생긴다 — 정상.)

### 4.3 crow's-foot

| `card` | 부모 머리 | 자식 머리 | 쓰는 경우 |
|---|---|---|---|
| `1:0..N` | ERone | ERzeroToMany | NN FK (기본) |
| `0..1:0..N` | ERzeroToOne | ERzeroToMany | NULL 허용 FK |
| `1:1` | ERone | ERone | FK + UQ |
| `1:0..1` | ERone | ERzeroToOne | FK + UQ, 자식이 없을 수도 있음 |
| `1:1..N` | ERone | ERoneToMany | 자식이 최소 1개라는 규칙을 앱이 보장할 때만 |

source = 부모(PK), target = 자식(FK). 복합 PK 의 각 컬럼은 `PK, FK` 처럼 모두 표기.

### 4.4 자동 배치로 안 풀릴 때

1. 먼저 **배치를 바꾼다** — 허브 테이블(엣지 다수)은 가운데 col, 관련 테이블은 사방으로. band/col 만 고치면 된다.
2. 그래도 `COLLINEAR` / `EDGE THRU` 가 남으면 `overrides`:
   - `{ fromShift, toShift }` : 앵커를 ±20 단위로 민다 (홀짝 규칙 유지).
   - `{ fromSide, toSide, points: [[x,y],…] }` : 경로를 수동 지정 (최후 수단, 좌표는 10 배수 · gap 안).
3. 엣지 id 는 `R_<부모>__<자식>__<fk>`, 데이터 이동은 `F_<from>__<to>`.

---

## 5. 타입 표기

**서버 (MySQL 8.4)** — SQL 에 쓴 그대로 (drift 비교 기준).

| 용도 | 표기 |
|---|---|
| PK / FK | `BIGINT` (PK 는 AUTO_INCREMENT, 표기 생략) |
| 문자열 | `VARCHAR(n)` / `TEXT` |
| 날짜·시각 | `DATE` / `DATETIME` (타임존 Asia/Seoul, application.yml) |
| 좌표 | `DECIMAL(10,7)` |
| 참·거짓 | `TINYINT(1)` (BOOLEAN 으로 써도 drift 비교에서 같게 본다) |
| 열거형 | `ENUM` — **값 목록은 ERD 에 쓰지 않는다** (Java enum 이 기준) |
| 해시 | `CHAR(64)` |

**차량 앱 (Room / SQLite)** — Kotlin 타입이 아니라 저장 affinity 로 쓴다.

| Kotlin | 표기 |
|---|---|
| `Long`, `Int`, `Boolean`, epoch millis | `INTEGER` |
| `String`, enum, `LocalDate`(TypeConverter) | `TEXT` |
| `Double`, `Float` | `REAL` |

---

## 6. 제약 (4번 셀)

`PK` · `FK` · `NN`(NOT NULL) · `UQ`(UNIQUE), 조합은 `FK, NN` 처럼 콤마+공백.
비어 있으면 NULL 허용. PK 는 NN 을 따로 쓰지 않는다.

---

## 7. 한글 라벨 용어집 (도메인 — 처음 정한 값, 바뀌면 여기부터 고친다)

| 컬럼 | 한글 | 컬럼 | 한글 |
|---|---|---|---|
| id | 〜ID | created_at | 생성일시 |
| card_id | 카드ID | updated_at | 수정일시 |
| revision_id | 개정ID | status | 상태 |
| profile_id | 프로필ID | version | 버전 |
| reviewer_id | 검수자ID | topic | 주제 |
| promulgated_on | 공포일 | effective_on | 시행일 |
| latitude / longitude | 위도 / 경도 | title | 제목 |

도메인 이름: 법령 개정, 검수 카드, 판단 조건, 조건별 검수 문장, 교차 검수, 검수자, 검수 요청 대기열,
어린이보호구역, 운전자 프로필, 확인 이력, 알림 게시 기록, 자주 가는 장소, 목적지 브리핑.

**개인정보 경계** — 기획상 프로필별 확인 이력·목적지 좌표는 차 밖으로 나가지 않는다.
이런 테이블은 앱 구역에만 두고, 서버로 가는 flow 를 그릴 때는 라벨에 무엇이 빠지는지(예: `식별자 없음`) 적는다.

---

## 8. 작성 절차

1. 기준 읽기: 설계 모드면 기획 페이지, 구현 이후면 V*.sql 전체 + Room `@Entity` 전체.
2. **drift 표**를 텍스트로 작성 (§0) → 사용자에게 먼저 보여 준다.
3. 테이블별 컬럼 표 (한글명 | 컬럼 | 타입 §5 | 제약 §6), 관계 표 (부모 | 자식 | 카디널리티 | FK).
4. 배치 결정: 구역 → band(참조 깊이) → col. 허브는 가운데.
5. `erd.data.mjs` 수정 → 생성기 실행.
6. 실패 메시지별 대응: `OVERLAP` → col/band 조정 · `LANE OVERFLOW` → GUTTER 키움 ·
   `ANCHOR DUP` → 그 테이블에 붙는 엣지를 다른 변으로 분산(배치 변경) · `COLLINEAR`/`EDGE THRU` → §4.4.
7. `⚠️` 경고(drift, NULL/카디널리티 불일치)를 하나씩 해소하거나 이유를 사용자에게 알린다.
8. 생성 결과는 사람이 drawio 로 열어 확인해야 한다 — 생성기는 렌더링을 보지 못한다고 보고할 것.

---

## 9. 자가 검증

생성기가 자동으로 하는 것 (실패 시 exit 1, 파일 미작성):

- **AABB 겹침**: 모든 테이블 쌍이 H/V GUTTER 이상, 라벨·범례는 40/10 이상 떨어짐.
- **앵커 중복**: (테이블, 변, 위치) 전부 distinct.
- **엣지 관통**: 폴리라인을 8px 로 샘플링해 어떤 테이블·라벨·범례 내부도 지나지 않음.
- **포개짐**: 다른 엣지끼리 같은 x 수직 / 같은 y 수평 구간 중첩 0.
- **격자**: 모든 waypoint 가 10 의 배수, 대각선 선분 0.

생성 후 수동 grep (생성기를 고쳤을 때 회귀 확인용):

```bash
f=docs/db/ERD.drawio
# 격자 — mxPoint/mxGeometry/mxRectangle 안의 좌표만 (vertex="1" 오탐 제외)
grep -oE '<mx(Point|Geometry|Rectangle)[^>]*' $f | grep -oE '(^| )(x|y)="[0-9.]+"' | grep -vE '0"$'
# 관계 엣지에 exit/entry 누락 (범례 LG_ 제외)
grep -E 'Arrow=ER' $f | grep -v exitX | grep -v '"LG_'
# 테이블 수 = 데이터 파일 tables 수
grep -c 'shape=table;' $f
```

---

## 10. 주의

- `ERD.drawio` 를 drawio 에서 손으로 옮기면 다음 생성 때 사라진다. 배치는 데이터의 band/col 로만 바꾼다.
- 생성기(`gen-erd.mjs`)를 고치면 §9 grep 과 경로 6종(이웃 col / 비이웃 col / 1단 아래 / N단 아래 / 위쪽 / 구역 간) 픽스처로 다시 확인한다.
- 범례·구분선·라벨은 데이터 테이블이 아니다 — 개수 검사에서 제외.

## 11. Appendix — 흔한 실수 (원본 prisma-erd v0.3 에서 이어받음 + 이 프로젝트 추가)

1. **stale ERD 신뢰** — 구현 이후엔 항상 SQL/Entity 기준.
2. **가변 높이 무시** — 고정 y 로 두면 컬럼 많은 테이블이 아래 밴드를 침범. 생성기 산식 사용.
3. **손 XML** — 규모가 조금만 커도 좌표·높이·앵커가 어긋난다. 데이터 → 생성기만 쓴다.
4. **격자 grep 오탐** — `vertex="1"` 을 좌표로 잡지 않게 mx* 태그 안만 본다.
5. **lane 공유 → 포개짐** — 앵커 검사로는 못 잡는다. collinear 검사 필수.
6. **관계 필드 누락** — FK 컬럼인데 relations 에 없으면 생성기가 막는다. 무시하지 말 것.
7. **카디널리티 혼용** — NULL 허용 FK 인데 부모 쪽 `1` 로 그림. §4.3 표로 통일.
8. **(추가) 두 DB 혼동** — 서버 FK 와 서버↔앱 동기화를 같은 실선으로 그리지 않는다. 구역 간은 항상 flow(점선).
9. **(추가) 출구·입구 수직선 포개짐** — 위 테이블 하단 출구와 아래 테이블 상단 입구가 같은 x 면 같은 gap 에서 겹친다.
   앵커 홀짝 규칙(§4.2)으로 막았다. 생성기를 고칠 때 이 규칙을 지우지 말 것.
